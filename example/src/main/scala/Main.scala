package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Merge, Source }
import controller.Controller
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
  val fdSourceWatch = Source.fromFutureSource(fooDeploymentController.watchSource)
  val fdTargetWatch = Source.fromFutureSource(fooDeploymentController.watchTarget)

  val fooServiceController = new FooServiceController
  val fsSourceWatch = Source.fromFutureSource(fooServiceController.watchSource)
  val fsTargetWatch = Source.fromFutureSource(fooServiceController.watchTarget)

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
