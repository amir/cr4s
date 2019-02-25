package cr4s
package reconciler

import GenericResourceModifiers._
import akka.{ Done, NotUsed }
import akka.event.LoggingAdapter
import akka.stream.{ ClosedShape, Materializer }
import akka.stream.scaladsl.{ Flow, GraphDSL, Merge, Partition, RunnableGraph, Sink, Source }
import cr4s.interpreter._
import play.api.libs.json.Format
import scala.concurrent.{ ExecutionContext, Future }
import shapeless.{ HList, LabelledGeneric, Typeable, TypeCase }
import skuber.{
  CustomResource,
  HasStatusSubresource,
  LabelSelector,
  ListResource,
  ObjectResource,
  OwnerReference,
  ResourceDefinition
}
import skuber.LabelSelector.IsEqualRequirement
import skuber.api.client.{ EventType, RequestContext, WatchEvent }

abstract class Reconciler[S <: CustomResource[_, _]: Typeable, T <: ObjectResource: Typeable] {

  type Source = S
  type Target = T
  type TargetList = ListResource[Target]

  sealed trait Event {
    def source: Source
  }
  case class Modified(source: S, children: List[T]) extends Event
  case class Deleted(source: S, children: List[T]) extends Event

  sealed trait Action
  case class Create(child: Target) extends Action
  case class Update(child: Target) extends Action
  case class Delete(child: Target) extends Action
  case class ChangeStatus(sourceName: String, update: Source => Source) extends Action

  def reconciler[R <: HList](implicit generic: LabelledGeneric.Aux[T, R],
                             modifier: MetadataModifier[R]): Event => List[Action] = {
    wrapReconciler(doReconcile)
  }

  def doReconcile: Event => List[Action]

  def wrapReconciler[R <: HList](recon: Event => List[Action])(implicit generic: LabelledGeneric.Aux[T, R],
                                                               modifier: MetadataModifier[R]): Event => List[Action] = {
    event =>
      recon(event) map {
        case Create(t)               => Create(addOwnerReference(event.source, t))
        case Update(t)               => Update(addOwnerReference(event.source, t))
        case d @ Delete(_)           => d
        case cs @ ChangeStatus(_, _) => cs
      }
  }

  // scalastyle:off
  def watchSource(parallelism: Int)(implicit context: RequestContext,
                                    sourceFormat: Format[S],
                                    sourceListFormat: Format[ListResource[S]],
                                    sourceResourceDefinition: ResourceDefinition[S],
                                    sourceListResourceDefinition: ResourceDefinition[ListResource[S]],
                                    targetFormat: Format[T],
                                    targetListFormat: Format[ListResource[T]],
                                    targetResourceDefinition: ResourceDefinition[T],
                                    targetListResourceDefinition: ResourceDefinition[ListResource[T]],
                                    c: ExecutionContext,
                                    materializer: Materializer) = {
    context.list[ListResource[S]].map { l =>
      val initialSource = Source(l.items.map(l => WatchEvent(EventType.MODIFIED, l)))
      val watchedSource = context.watchAllContinuously[S](Some(l.resourceVersion))

      initialSource.concat(watchedSource).mapAsync(parallelism) { watchEvent =>
        val labelSelector = LabelSelector(IsEqualRequirement("controller", watchEvent._object.name))
        val targetList = context.listSelected[TargetList](labelSelector).map { x =>
          x.items.filter(_.metadata.ownerReferences.contains(ownerReference(watchEvent._object)))
        }

        targetList map { targets =>
          watchEvent._type match {
            case EventType.ADDED | EventType.MODIFIED => Modified(watchEvent._object, targets)
            case EventType.DELETED                    => Deleted(watchEvent._object, targets)
          }
        }
      }
    }
  }

  def watchTarget(parallelism: Int)(implicit context: RequestContext,
                                    sourceFormat: Format[S],
                                    sourceListFormat: Format[ListResource[S]],
                                    sourceResourceDefinition: ResourceDefinition[S],
                                    sourceListResourceDefinition: ResourceDefinition[ListResource[S]],
                                    targetFormat: Format[T],
                                    targetListFormat: Format[ListResource[T]],
                                    targetResourceDefinition: ResourceDefinition[T],
                                    targetListResourceDefinition: ResourceDefinition[ListResource[T]],
                                    c: ExecutionContext,
                                    materializer: Materializer) = {
    context.list[ListResource[T]].map { l =>
      val initialSource = Source(l.items.map(l => WatchEvent(EventType.MODIFIED, l)))
      val watchedSource = context.watchAllContinuously[T](Some(l.resourceVersion))

      initialSource
        .concat(watchedSource)
        .filter(watchEvent => watchEvent._object.metadata.labels.contains("controller"))
        .mapAsync(parallelism) { watchEvent =>
          context.get[Source](watchEvent._object.metadata.labels("controller")) map { parent =>
            (parent, watchEvent._object)
          }
        }
        .filter { case (p, ch) => ch.metadata.ownerReferences.exists(_.uid == p.metadata.uid) }
        .map { case (parent, child) => Modified(parent, List(child)) }
    }
  }
  // scalastyle:on

  case class CacheEntry(s: Source, ts: List[Target])
  case class CacheEntryKey(name: String, namespace: String, kind: String)
  case class Cache(events: List[Event], cache: Map[CacheEntryKey, CacheEntry])

  case class Resource[O <: ObjectResource](watch: akka.stream.scaladsl.Source[WatchEvent[O], _], list: List[O])
  case class ResourcesState(source: akka.stream.scaladsl.Source[WatchEvent[S], _],
                            target: akka.stream.scaladsl.Source[WatchEvent[T], _],
                            state: Map[CacheEntryKey, CacheEntry])

  def buildResource[O <: ObjectResource](implicit context: RequestContext,
                                         format: Format[O],
                                         listFormat: Format[ListResource[O]],
                                         resourceDefinition: ResourceDefinition[O],
                                         ec: ExecutionContext): Future[Resource[O]] = {
    context.list[ListResource[O]]().map { l =>
      Resource[O](
        context.watchAllContinuously[O](Some(l.resourceVersion)),
        l.items
      )
    }
  }

  def buildState(sources: List[S], target: List[T]): Map[CacheEntryKey, CacheEntry] = {
    val initKeys = sources.foldLeft(Map.empty[CacheEntryKey, CacheEntry]) {
      case (acc, s) =>
        val key = CacheEntryKey(name = s.name, namespace = s.namespace, kind = s.kind)
        acc + (key -> CacheEntry(s, List.empty))
    }

    target.foldLeft(initKeys) {
      case (acc, t) =>
        val owner = t.metadata.ownerReferences.head
        val sourceKey = CacheEntryKey(name = owner.name, namespace = t.namespace, kind = owner.kind)

        acc.get(sourceKey).fold(acc)(s => acc + (sourceKey -> s.copy(ts = t :: s.ts)))
    }
  }

  def buildResourcesState(implicit context: RequestContext,
                          sourceFormat: Format[S],
                          sourceListFormat: Format[ListResource[S]],
                          sourceResourceDefinition: ResourceDefinition[S],
                          targetFormat: Format[T],
                          targetListFormat: Format[ListResource[T]],
                          targetResourceDefinition: ResourceDefinition[T],
                          ec: ExecutionContext): Future[ResourcesState] = {

    for {
      source <- buildResource[S]
      target <- buildResource[T]
    } yield
      ResourcesState(
        source.watch,
        target.watch,
        buildState(source.list, target.list)
      )

  }

  def scanner(acc: Cache, x: WatchEvent[_ >: T with S <: ObjectResource]): Cache = {
    val `WatchEvent[S]` = TypeCase[WatchEvent[S]]
    val `WatchEvent[T]` = TypeCase[WatchEvent[T]]

    x match {
      case `WatchEvent[S]`(we) =>
        val key = CacheEntryKey(name = we._object.name, namespace = we._object.namespace, kind = we._object.kind)
        val targets = acc.cache.get(key).fold(List.empty[T])(_.ts)
        val cache = acc.cache + (key -> CacheEntry(we._object, targets))

        we._type match {
          case EventType.ADDED | EventType.MODIFIED => Cache(List(Modified(we._object, targets)), cache)
          case EventType.DELETED                    => Cache(List(Deleted(we._object, targets)), cache - key)
        }

      case `WatchEvent[T]`(we) =>
        val owner = we._object.metadata.ownerReferences.head
        val sourceKey = CacheEntryKey(name = owner.name, namespace = we._object.namespace, kind = owner.kind)

        acc.cache.get(sourceKey) match {
          case Some(s) =>
            val cacheEntry = we._type match {
              case EventType.DELETED => CacheEntry(s.s, s.ts.filterNot(_.uid == we._object.uid))
              case _                 => CacheEntry(s.s, List(we._object))
            }
            val cache = acc.cache + (sourceKey -> cacheEntry)
            Cache(List(Modified(s.s, cacheEntry.ts)), cache)

          case None => acc
        }
    }
  }

  def merge(implicit context: RequestContext,
            sourceFormat: Format[S],
            sourceListFormat: Format[ListResource[S]],
            sourceResourceDefinition: ResourceDefinition[S],
            targetFormat: Format[T],
            targetListFormat: Format[ListResource[T]],
            targetResourceDefinition: ResourceDefinition[T],
            ec: ExecutionContext): akka.stream.scaladsl.Source[List[Event], Future[NotUsed]] = {

    Source.fromFutureSource(
      for {
        resourceState <- buildResourcesState
      } yield
        Source
          .combine(resourceState.source, resourceState.target)(Merge(_))
          .scan(Cache(resourceState.state.values.toList.map(ce => Modified(ce.s, ce.ts)), resourceState.state))(scanner)
          .map(_.events)
    )
  }

  def ownerReference[O <: ObjectResource](o: O): OwnerReference = {
    OwnerReference(
      apiVersion = o.apiVersion,
      kind = o.kind,
      name = o.metadata.name,
      uid = o.uid,
      controller = Some(true),
      blockOwnerDeletion = Some(true)
    )
  }

  def addOwnerReference[R <: HList](source: S, target: T)(implicit generic: LabelledGeneric.Aux[T, R],
                                                          modifier: MetadataModifier[R]): T = {

    addOwner(target, ownerReference(source))
  }

  def sink(implicit logger: LoggingAdapter): Sink[ActionResult, Future[Done]] =
    Sink.foreach[ActionResult](r => logger.info("{}", r))

  private def errorPartition = GraphDSL.create() { implicit b =>
    b.add(Partition[ActionResult](2, {
      case ActionResult(_, Success)    => 0
      case ActionResult(_, Failure(_)) => 1
    }))
  }

  private def convert(parallelism: Int)(
    implicit context: RequestContext,
    ec: ExecutionContext,
    sourceFormat: Format[S],
    sourceResourceDefinition: ResourceDefinition[S],
    targetListFormat: Format[ListResource[T]],
    targetListResourceDefinition: ResourceDefinition[ListResource[T]]): Flow[ActionResult, Event, NotUsed] =
    Flow[ActionResult].mapAsync(parallelism) { res =>
      val st = for {
        s <- context.get[Source](res.action.name)
        t <- context.list[ListResource[Target]].map { t =>
          t.items.filter(x => x.metadata.ownerReferences.exists(_.uid == s.uid))
        }
      } yield (s, t)

      st.map {
        case (source, target) =>
          res.action match {
            case _: CreateAction | _: UpdateAction | _: ChangeStatusAction => Modified(source, target)
            case _: DeleteAction                                           => Deleted(source, target)
          }

      }
    }

  def graph[R <: HList](interpreterFlow: Flow[List[Action], ActionResult, NotUsed], parallelism: Int)(
    implicit context: RequestContext,
    logger: LoggingAdapter,
    sourceFormat: Format[S],
    sourceListFormat: Format[ListResource[S]],
    sourceResourceDefinition: ResourceDefinition[S],
    sourceListResourceDefinition: ResourceDefinition[ListResource[S]],
    targetFormat: Format[T],
    targetListFormat: Format[ListResource[T]],
    targetResourceDefinition: ResourceDefinition[T],
    targetListResourceDefinition: ResourceDefinition[ListResource[T]],
    hasStatusSubresource: HasStatusSubresource[S],
    generic: LabelledGeneric.Aux[T, R],
    modifier: MetadataModifier[R],
    c: ExecutionContext,
    materializer: Materializer): RunnableGraph[NotUsed] = {

    RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val mergeEvents = builder.add(Merge[Event](2))
      val merge = builder.add(Merge[ActionResult](2))

      val s = Source.fromFutureSource(watchSource(parallelism))
      val t = Source.fromFutureSource(watchTarget(parallelism))
      val partition = builder.add(errorPartition)

      s ~> mergeEvents
      t ~> mergeEvents
      mergeEvents.out.map(reconciler).via(interpreterFlow) ~> merge ~> partition

      partition.out(1) ~> convert(parallelism).map(reconciler).via(interpreterFlow) ~> merge
      partition.out(0) ~> sink

      ClosedShape
    })
  }
}
