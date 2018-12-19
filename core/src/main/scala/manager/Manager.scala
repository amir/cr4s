package cr4s
package manager

import akka.stream.{FlowShape, Graph}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source, Zip}
import cr4s.controller.Controller
import skuber.{K8SWatchEvent, ObjectResource}
import skuber.api.client.WatchEvent

import scala.concurrent.{ExecutionContext, Future}

class Manager(controllers: Seq[Controller[_ <: ObjectResource]])(implicit ec: ExecutionContext) {

  val foo = Flow.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val broadcast = b.add(Broadcast[Int](2))
    val zip = b.add(Zip[Int, String]())

    broadcast.out(0).map(identity) ~> zip.in0
    broadcast.out(1).map(_.toString) ~> zip.in1

    FlowShape(broadcast.in, zip.out)
  })

  val cache: Map[String, String] = Map.empty[String, String]

  def watch = {
    //Future.sequence(controllers.map(_.watch))
    //val f: List[String] = controllers.map(c => Source.fromFutureSource(c.watch2)).toList
    //val fs = controllers.map(c => Source.fromFutureSource(c.watch2))//.toList
    //Source.combine(fs(0), fs(1))(Merge(_))
    //fs.foldLeft(Source.empty[WatchEvent])(_ merge _)

    controllers.map(c => Source.fromFutureSource(c.watch2(cache)))
      .foldLeft(Source.empty[WatchEvent[_]])(_ merge _)
  }
}
