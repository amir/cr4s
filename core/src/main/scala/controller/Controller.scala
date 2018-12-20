package cr4s
package controller

import akka.stream.Materializer
import cr4s.reconcile.Reconciler
import play.api.libs.json.Format
import scala.concurrent.ExecutionContext
import skuber.{ ListResource, ObjectResource, ResourceDefinition }
import skuber.api.client.{ RequestContext, WatchEvent }

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

  def watch = {
    context.list[ListResource[O]].map { l =>
      context.watchAllContinuously[O](Some(l.resourceVersion)).map(EventImpl.apply)
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
