package cr4s
package reconciler

import GenericResourceModifiers._
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.Format
import scala.concurrent.ExecutionContext
import shapeless.{ HList, LabelledGeneric }
import skuber.{ CustomResource, LabelSelector, ListResource, ObjectResource, OwnerReference, ResourceDefinition }
import skuber.LabelSelector.IsEqualRequirement
import skuber.api.client.{ EventType, RequestContext, WatchEvent }

abstract class Reconciler[S <: CustomResource[_, _], T <: ObjectResource] {

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
}
