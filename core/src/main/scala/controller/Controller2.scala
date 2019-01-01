package cr4s
package controller

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.Format
import scala.concurrent.{ ExecutionContext, Future }
import skuber.{ LabelSelector, ListResource, ObjectResource, OwnerReference, ResourceDefinition }
import skuber.LabelSelector.IsEqualRequirement
import skuber.api.client.{ EventType, RequestContext, WatchEvent }

abstract class Controller2[S <: ObjectResource, T <: ObjectResource] {

  type Source = S
  type Target = T
  type TargetList = ListResource[Target]

  sealed trait Event
  case class Modified(source: S, children: List[T]) extends Event
  case class Deleted(source: S, children: List[T]) extends Event

  sealed trait Action
  case class Create(child: T) extends Action
  case class Update(child: T) extends Action
  case class Delete(child: T) extends Action
  case class ChangeStatus(update: S => S) extends Action

  def reconciler: Event => List[Action]

  //def ownerReference(source: Source): OwnerReference = {
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

  // TODO Figure out how to generically update the target's metadata
  /*def addOwnerReference(source: Source, target: Target): Target = {
    val ownerRefs = target.metadata.ownerReferences.filterNot(or => or.kind == source.kind && or.uid == source.uid)
    target.copy(metadata = target.metadata.copy(ownerReferences = ownerReference(source) :: ownerRefs))
  }*/
}

object Controller2 {
  def watchSource[S <: ObjectResource, T <: ObjectResource](controller2: Controller2[S, T])(
    implicit context: RequestContext,
    listFmt1: Format[ListResource[S]],
    listRd1: ResourceDefinition[ListResource[S]],
    objFmt1: Format[S],
    objRd1: ResourceDefinition[S],
    listFmt2: Format[ListResource[T]],
    listRd2: ResourceDefinition[ListResource[T]],
    objFmt2: Format[T],
    objRd2: ResourceDefinition[T],
    c: ExecutionContext,
    materializer: Materializer)/*: Future[Source[controller2.Event, _]]*/ = {
    context.list[ListResource[S]].map { l =>
      val initialSource = Source(l.items.map(l => WatchEvent(EventType.MODIFIED, l)))
      val watchedSource = context.watchAllContinuously[S](Some(l.resourceVersion))

      initialSource.concat(watchedSource).mapAsync(1) { watchEvent =>
        val labelSelector = LabelSelector(IsEqualRequirement("controller", watchEvent._object.name))
        val ownerReference = controller2.ownerReference(watchEvent._object)
        val deps = context.listSelected[controller2.TargetList](labelSelector).map { x =>
          x.items.filter(_.metadata.ownerReferences.contains(ownerReference))
        }

        deps map { deployments =>
          val event = watchEvent._type match {
            case EventType.ADDED | EventType.MODIFIED => controller2.Modified(watchEvent._object, deployments)
            case EventType.DELETED                    => controller2.Deleted(watchEvent._object, deployments)
          }
          event
        }
      }
    }
  }

  def watchTarget[S <: ObjectResource, T <: ObjectResource](controller2: Controller2[S, T])(
    implicit context: RequestContext,
    listFmt1: Format[ListResource[S]],
    listRd1: ResourceDefinition[ListResource[S]],
    objFmt1: Format[S],
    objRd1: ResourceDefinition[S],
    listFmt2: Format[ListResource[T]],
    listRd2: ResourceDefinition[ListResource[T]],
    objFmt2: Format[T],
    objRd2: ResourceDefinition[T],
    c: ExecutionContext,
    materializer: Materializer): Future[Source[controller2.Event, _]] = {
    context.list[ListResource[T]].map { l =>
      val initialSource = Source(l.items.map(l => WatchEvent(EventType.MODIFIED, l)))
      val watchedSource = context.watchAllContinuously[T](Some(l.resourceVersion))

      //TODO Filter the target events to ones with the label of contoller then map the lookup the ownerReference and map to modify events of this

      initialSource
        .concat(watchedSource)
        .filter(watchEvent => watchEvent._object.metadata.labels.contains("controller"))
        .mapAsync(1) { watchEvent =>
          //watchEvent._object.metadata.ownerReferences.find()

          val controller = context.get[controller2.Source](watchEvent._object.metadata.labels("controller"))

          controller map { parent =>
            controller2.Modified(parent, List(watchEvent._object))
          }
        }
    }
  }
}

