package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Merge, Source }
import controller.Controller2
import cr4s.interpreter.SkuberInterpreter
import skuber._
import skuber.api.client.RequestLoggingContext
import skuber.json.apps.format._
import skuber.json.format._

object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val loggingContext = RequestLoggingContext()

  implicit val k8s = k8sInit

  val fooDeploymentController = new FooDeploymentController
  val fdSourceWatch = Source.fromFutureSource(Controller2.watchSource(fooDeploymentController))
  val fdTargetWatch = Source.fromFutureSource(Controller2.watchTarget(fooDeploymentController))

  val fooServiceController = new FooServiceController
  val fsSourceWatch = Source.fromFutureSource(Controller2.watchSource(fooServiceController))
  val fsTargetWatch = Source.fromFutureSource(Controller2.watchTarget(fooServiceController))

  val fdInterpreter = new SkuberInterpreter(k8s, fooDeploymentController)
  Source
    .combine(fdSourceWatch, fdTargetWatch)(Merge(_))
    .map(fooDeploymentController.reconciler)
    .runWith(fdInterpreter.sink)

  val fsInterpreter = new SkuberInterpreter(k8s, fooServiceController)
  Source
    .combine(fsSourceWatch, fsTargetWatch)(Merge(_))
    .map(fooServiceController.reconciler)
    .runWith(fsInterpreter.sink)
}
