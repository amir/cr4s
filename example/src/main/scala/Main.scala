package cr4s

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import cr4s.interpreter.{ ActionResult, SkuberInterpreter }
import skuber._
import skuber.api.client.RequestLoggingContext
import skuber.json.apps.format._

object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val loggingContext = RequestLoggingContext()

  implicit val k8s = k8sInit
  implicit val logger = Logging.getLogger(system, this)

  val sink = Sink.foreach[ActionResult](r => logger.info("{}", r))

  val fooDeploymentController = new FooDeploymentReconciler
  val fdInterpreter = new SkuberInterpreter(k8s, fooDeploymentController)
  fooDeploymentController.merge
    .mapConcat(a => a.map(fooDeploymentController.reconciler))
    .via(fdInterpreter.flow(1))
    .runForeach(a => logger.info("{}", a))
    .onComplete {
      case scala.util.Success(_) =>
      case scala.util.Failure(e) => logger.error("{}", e)
    }
}
