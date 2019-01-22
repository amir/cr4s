package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
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
  implicit val logger = k8s.log

  val fooDeploymentController = new FooDeploymentReconciler
  val fooServiceController = new FooServiceReconciler

  fooServiceController.graph(1).run()
  fooDeploymentController.graph(1).run()
}
