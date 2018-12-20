package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import controller.Controller
import cr4s.manager.Manager
import cr4s.reconcile.Reconciler
import skuber._
import skuber.api.client.{ RequestLoggingContext, WatchEvent }
import skuber.apps.Deployment
import skuber.json.apps.format._
import skuber.json.format._

object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val loggingContext = RequestLoggingContext()

  implicit val k8s = k8sInit

  val podReconciler: Reconciler[Pod] = new Reconciler[Pod] {
    override def reconcile(l: WatchEvent[Pod]): Unit =
      println(s"[Pod] ${l._type}: ${l._object.namespace}/${l._object.name}") // scalastyle:ignore
  }

  val deploymentReconciler: Reconciler[Deployment] = new Reconciler[Deployment] {
    override def reconcile(l: WatchEvent[Deployment]): Unit =
      println(s"[Deployment] ${l._type}: ${l._object.namespace}/${l._object.name}") // scalastyle:ignore
  }

  val fooReconciler = new FooReconciler()

  val podController = new Controller[Pod](podReconciler)
  val deploymentController = new Controller[Deployment](deploymentReconciler)
  val fooController = new Controller[Foo](fooReconciler)

  val manager = new Manager(podController, deploymentController, fooController)

  manager.watch.runWith(Sink.foreach(println)) // scalastyle:ignore
}
