package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cr4s.controller.Controller
import cr4s.reconcile.Reconciler
import skuber._
import skuber.json.format._

object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  implicit val k8s = k8sInit

  val reconciler: Reconciler[Pod] = (l: K8SWatchEvent[Pod]) => {
    println(s"${l._type}: ${l._object.namespace}/${l._object.name}")
  }

  val controller = new Controller[Pod](reconciler)
  controller.watch
}
