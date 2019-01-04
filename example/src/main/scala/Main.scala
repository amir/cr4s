package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import controller.Controller2
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
  Source.fromFutureSource(Controller2.watchSource(fooDeploymentController))
    .map(fooDeploymentController.reconciler)
    .runWith(Sink.foreach(println))
}
