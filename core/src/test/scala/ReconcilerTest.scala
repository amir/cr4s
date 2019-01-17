import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import com.softwaremill.quicklens._
import cr4s.reconciler.Reconciler
import org.scalatest.{ FlatSpec, Matchers }
import skuber.Service
import skuber.apps.Deployment

class ReconcilerTest extends FlatSpec with Matchers {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  class DeploymentServiceReconciler extends Reconciler[Deployment, Service] {
    override def doReconcile: Event => List[Action] = {
      case Modified(deployment, Nil) =>
        List(Create(Service(deployment.name)))

      case Modified(deployment, service :: tail) => Nil

      case Deleted(deployment, services) => services.map(Delete)
    }
  }

  val testController = new DeploymentServiceReconciler

  val deployment = Deployment("test")
  val service =
    Service(deployment.name).modify(_.metadata.ownerReferences).setTo(List(testController.ownerReference(deployment)))

  val events: List[testController.Event] = List(
    testController.Modified(deployment, List.empty[Service])
  )

  val source: Source[List[testController.Action], NotUsed] = Source(events).map(testController.reconciler)
  val materializedSource = source.runWith(TestSink.probe[List[testController.Action]])

  it should "inject OwnerReference" in {
    assert(materializedSource.request(1).expectNext() === List(testController.Create(service)))
  }
}
