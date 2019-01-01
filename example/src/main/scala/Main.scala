package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import controller.Controller
import cr4s.manager.Manager
import cr4s.reconcile.Reconciler
import skuber.LabelSelector.IsEqualRequirement
import skuber._
import skuber.api.client.{ EventType, RequestLoggingContext, WatchEvent }
import skuber.apps.Deployment
import skuber.apps.DeploymentList
import skuber.json.apps.format._
import skuber.json.format._

object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val loggingContext = RequestLoggingContext()

  implicit val k8s = k8sInit

  val fooReconciler: Reconciler[Foo] = new Reconciler[Foo] {
    val newReconciler = new FooDeploymentController

    override def reconcile(l: WatchEvent[Foo]): Unit = {
      val labelSelector = LabelSelector(IsEqualRequirement("controller", l._object.name))
      val ownerReference = newReconciler.ownerReference(l._object)
      val deps = k8s.listSelected[newReconciler.TargetList](labelSelector).map { x =>
        x.items.filter(_.metadata.ownerReferences.contains(ownerReference))
      }

      deps map { deployments =>
        val event = l._type match {
          case EventType.ADDED | EventType.MODIFIED => newReconciler.Modified(l._object, deployments)
          case EventType.DELETED                    => newReconciler.Deleted(l._object, deployments)
        }

        val actions = newReconciler.reconciler(event)
        system.log.info(s"Reconciling ${l._object.kind}/${l._object.name} ===> $actions")

        actions.foreach {
          case newReconciler.Create(c) => k8s.create[newReconciler.Target](c)
          case newReconciler.Delete(c) => k8s.delete[newReconciler.Target](c.name)
          case newReconciler.Update(c) => k8s.update[newReconciler.Target](c)
        }
      }

    }
  }

  val fooController = new Controller[Foo](fooReconciler)

  val manager = new Manager(fooController)

  manager.watch.runWith(Sink.foreach(println))

}
