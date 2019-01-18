package cr4s

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import com.softwaremill.quicklens._
import cr4s.reconciler.Reconciler
import org.scalatest.{ FlatSpec, Matchers }
import skuber.Service

class ReconcilerTest extends FlatSpec with Matchers {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  class DeploymentServiceReconciler extends Reconciler[Foo.FooResource, Service] {
    override def doReconcile: Event => List[Action] = {
      case Modified(deployment, Nil) =>
        List(Create(Service(deployment.name)))

      case Modified(deployment, service :: tail) => Nil

      case Deleted(deployment, services) => services.map(Delete)
    }
  }

  val testController = new DeploymentServiceReconciler

  val foo = Foo("test", Foo.Spec("test", 1))
  val service =
    Service(foo.name).modify(_.metadata.ownerReferences).setTo(List(testController.ownerReference(foo)))

  val events: List[testController.Event] = List(
    testController.Modified(foo, List.empty[Service])
  )

  val source: Source[List[testController.Action], NotUsed] = Source(events).map(testController.reconciler)
  val materializedSource = source.runWith(TestSink.probe[List[testController.Action]])

  it should "inject OwnerReference" in {
    assert(materializedSource.request(1).expectNext() === List(testController.Create(service)))
  }
}
