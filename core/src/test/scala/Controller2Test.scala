import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import cr4s.controller.Controller2
import skuber.Service
import skuber.apps.Deployment

class Controller2Test {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  class TestController extends Controller2[Deployment, Service] {
    override def reconciler: Event => List[Action] = {
      case Modified(deployment, Nil) =>
        List(Create(Service(deployment.name)))

      case Modified(deployment, service :: tail) => Nil

      case Deleted(deployment, services) => services.map(Delete)
    }
  }

  val testController = new TestController

  val deployment = Deployment("test")
  val service = Service(deployment.name)

  val events: List[testController.Event] = List(
    testController.Modified(deployment, List.empty[Service]),
    testController.Modified(deployment, List(service)),
    testController.Deleted(deployment, List(service)),
    testController.Modified(deployment, List.empty[Service]), // triggered by child
  )

  val source: Source[List[testController.Action], NotUsed] = Source(events).map(testController.reconciler)

  source.runWith(TestSink.probe[List[testController.Action]])
    .request(1)
    .expectNext(List(testController.Create(service)))
    .request(1)
    .expectNext(List.empty)
    .request(1)
    .expectNext(List(testController.Delete(service)))
    .request(1)
    .expectNext(List(testController.Create(service)))
    .expectComplete()
}
