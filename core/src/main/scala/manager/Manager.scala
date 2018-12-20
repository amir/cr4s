package cr4s
package manager

import akka.stream.scaladsl.Source
import cr4s.controller.Controller
import skuber.ObjectResource
import skuber.api.client.{EventType, LoggingContext, RequestContext}

import scala.concurrent.ExecutionContext

class Manager(controllers: Seq[Controller[_ <: ObjectResource]])(implicit ec: ExecutionContext, context: RequestContext, lc: LoggingContext) {

  def watch = {
    controllers.map(c => Source.fromFutureSource(c.watch))
      .foldLeft(Source.empty[Controller.Event])(_ merge _).map { e =>
      e.watchEvent._type match {
        case EventType.ADDED | EventType.MODIFIED => e.reconciler.reconcile(e.watchEvent)
        case _ =>
      }
    }
  }

}
