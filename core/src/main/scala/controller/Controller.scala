package cr4s
package controller

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import cr4s.reconcile.Reconciler
import play.api.libs.json.Format

import scala.concurrent.{ ExecutionContext, Future }
import skuber.{ ListResource, ObjectResource, ResourceDefinition }
import skuber.api.client.{ EventType, RequestContext, WatchEvent }

class Controller[O <: ObjectResource](reconciler: Reconciler[O])(
  implicit context: RequestContext,
  listFmt: Format[ListResource[O]],
  listRd: ResourceDefinition[ListResource[O]],
  objFmt: Format[O],
  objRd: ResourceDefinition[O],
  ec: ExecutionContext,
  materializer: Materializer
) { self =>

  case class EventImpl(watchEvent: WatchEvent[O]) extends Controller.Event {
    type Object = O

    override def reconciler: Reconciler[O] = self.reconciler
  }

  //def watch: Future[Source[Controller.Event, _]] = {
  def watch = {
    context.list[ListResource[O]].map { l =>
      val initialSource: Source[WatchEvent[O], NotUsed] = Source(l.items.map(l => WatchEvent(EventType.MODIFIED, l)))
      val watchedSource: Source[WatchEvent[O], _] = context.watchAllContinuously[O](Some(l.resourceVersion))

      initialSource.concat(watchedSource).map(EventImpl.apply)
    }
  }
}

object Controller {
  trait Event {
    type Object <: ObjectResource

    def reconciler: Reconciler[Object]
    def watchEvent: WatchEvent[Object]
  }
}
