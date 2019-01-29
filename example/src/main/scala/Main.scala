package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
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
  implicit val logger = k8s.log

  val fooDeploymentController = new FooDeploymentReconciler
  val fooServiceController = new FooServiceReconciler

  val fdInterpreter = new SkuberInterpreter(k8s, fooDeploymentController)
  val fsInterpreter = new SkuberInterpreter(k8s, fooServiceController)

  fooServiceController.graph(fsInterpreter.flow(1), 1).run()
  fooDeploymentController.graph(fdInterpreter.flow(1), 1).run()
}
