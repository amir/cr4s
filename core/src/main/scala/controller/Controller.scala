package cr4s
package controller

import akka.Done
import akka.stream.{KillSwitches, Materializer, UniqueKillSwitch}
import akka.stream.scaladsl.{Keep, Sink}
import cr4s.reconcile.Reconciler
import play.api.libs.json.Format
import skuber.{ListResource, ObjectResource, ResourceDefinition}
import skuber.api.client.RequestContext

import scala.concurrent.{ExecutionContext, Future}

class Controller[O <: ObjectResource](reconciler: Reconciler[O])(
  implicit context: RequestContext,
  listFmt: Format[ListResource[O]], listRd: ResourceDefinition[ListResource[O]],
  objFmt: Format[O], objRd: ResourceDefinition[O],
  ec: ExecutionContext, materializer: Materializer
) {

  def watch: Future[(UniqueKillSwitch, Future[Done])] = {
    context.list[ListResource[O]].map { l =>
      context.watchAllContinuously[O](Some(l.resourceVersion))
        .viaMat(KillSwitches.single)(Keep.right)
        .map { we =>
          we._type match {
            case _ => reconciler.reconcile(we)
          }
        }.async.toMat(Sink.ignore)(Keep.both).run()
    }
  }
}