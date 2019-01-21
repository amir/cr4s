package cr4s

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, ClosedShape }
import akka.stream.scaladsl.{ Flow, GraphDSL, Merge, Partition, RunnableGraph, Sink, Source }
import cr4s.interpreter.{ ActionResult, CreateAction }
import cr4s.reconciler.Reconciler
import scala.util.Random
import skuber.Service

class FeedbackTest {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  class FooServiceReconciler extends Reconciler[Foo.FooResource, Service] {
    override def doReconcile: Event => List[Action] = {
      case Modified(service, Nil) =>
        List(Create(Service(service.name)))
    }
  }

  val flowInterpreter: Flow[List[FooServiceReconciler#Action], ActionResult, NotUsed] =
    Flow[List[FooServiceReconciler#Action]].mapConcat(identity).map { _ =>
      ActionResult(CreateAction("a", "b", "c"),
                   if (Random.nextBoolean()) interpreter.Success else interpreter.Failure(new Throwable()))
    }

  val errorPartition = GraphDSL.create() { implicit b =>
    b.add(Partition[ActionResult](2, {
      case ActionResult(_, interpreter.Success)    => 0
      case ActionResult(_, interpreter.Failure(_)) => 1
    }))
  }

  val foo = Foo("test", Foo.Spec("test", 1))
  val reconciler = new FooServiceReconciler

  val events: List[reconciler.Event] = List.fill(10) { reconciler.Modified(foo, List.empty[Service]) }

  val errorFlow = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val in = Source(events).map(reconciler.reconciler).via(flowInterpreter)
    val out = Sink.foreach[ActionResult](r => system.log.info("{}", r))

    val convert: Flow[ActionResult, reconciler.Event, NotUsed] = Flow[ActionResult].map { res =>
      reconciler.Modified(foo, List.empty[Service])
    }

    val partition = builder.add(errorPartition)
    val merge = builder.add(Merge[ActionResult](2))

    in ~> merge ~> partition
    partition.out(1) ~> convert.map(reconciler.reconciler) ~> flowInterpreter ~> merge
    partition.out(0) ~> out

    ClosedShape
  })

  errorFlow.run
}
