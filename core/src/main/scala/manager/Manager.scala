package cr4s
package manager

import akka.stream.scaladsl.Source
import cr4s.controller.Controller
import scala.concurrent.ExecutionContext
import skuber.ObjectResource
import skuber.api.client.{ LoggingContext, RequestContext }

class Manager(controllers: Controller[_ <: ObjectResource]*)(implicit ec: ExecutionContext,
                                                             context: RequestContext,
                                                             lc: LoggingContext) {

  def watch = {
    controllers
      .map(c => Source.fromFutureSource(c.watch))
      .foldLeft(Source.empty[Controller.Event])(_ merge _)
      .map(e => e.reconciler.reconcile(e.watchEvent))
  }

}
