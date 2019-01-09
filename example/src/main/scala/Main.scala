package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Merge, Source}
import controller.Controller2
import cr4s.interpreter.SkuberInterpreter
import skuber._
import skuber.api.client.RequestLoggingContext
import skuber.json.apps.format._

object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val loggingContext = RequestLoggingContext()

  implicit val k8s = k8sInit

  val fooDeploymentController = new FooDeploymentController
  val sourceWatch = Source.fromFutureSource(Controller2.watchSource(fooDeploymentController))
  val targetWatch = Source.fromFutureSource(Controller2.watchTarget(fooDeploymentController))

  val interpreter = new SkuberInterpreter(k8s, fooDeploymentController)
  Source.combine(sourceWatch, targetWatch)(Merge(_)).map(fooDeploymentController.reconciler).runWith(interpreter.sink)
}
