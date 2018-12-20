package cr4s
package manager

import akka.stream.scaladsl.Source
import cr4s.controller.Controller
import skuber.ObjectResource

import scala.concurrent.ExecutionContext

class Manager(controllers: Seq[Controller[_ <: ObjectResource]])(implicit ec: ExecutionContext) {

  def watch = {
    controllers.map(c => Source.fromFutureSource(c.watch))
      .foldLeft(Source.empty[Controller.Event])(_ merge _).map { e =>
        e.reconciler.reconcile(e.watchEvent)
      }
  }

}
