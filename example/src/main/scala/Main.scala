package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Merge, Sink, Source}
import cr4s.interpreter.{CustomResourceSkuberInterpreter, SkuberInterpreter}
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

  val fooDeploymentController = new FooDeploymentReconciler
  val fdSourceWatch = Source.fromFutureSource(fooDeploymentController.watchSource(1))
  val fdTargetWatch = Source.fromFutureSource(fooDeploymentController.watchTarget(1))

  val fooServiceController = new FooServiceReconciler
  val fsSourceWatch = Source.fromFutureSource(fooServiceController.watchSource(1))
  val fsTargetWatch = Source.fromFutureSource(fooServiceController.watchTarget(1))

  val fdInterpreter = new CustomResourceSkuberInterpreter(k8s, fooDeploymentController)
  Source
    .combine(fdSourceWatch, fdTargetWatch)(Merge(_))
    .map(fooDeploymentController.reconciler)
    .via(fdInterpreter.flow(1))
    .runWith(Sink.foreach(x => k8s.log.info("{}", x)))

  val fsInterpreter = new SkuberInterpreter(k8s, fooServiceController)
  Source
    .combine(fsSourceWatch, fsTargetWatch)(Merge(_))
    .map(fooServiceController.reconciler)
    .via(fsInterpreter.flow(1))
    .runWith(Sink.foreach(x => k8s.log.info("{}", x)))
}
