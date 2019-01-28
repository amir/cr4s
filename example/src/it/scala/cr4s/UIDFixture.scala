package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.{ fixture, FutureOutcome }
import scala.concurrent.Await
import scala.concurrent.duration._
import skuber._
import skuber.api.client.RequestContext

trait UIDFixture extends fixture.AsyncFlatSpec {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  case class FixtureParam(context: RequestContext, uid: String)

  type Source <: CustomResource[_, _]

  implicit val rd: ResourceDefinition[Source]

  override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val k8s = k8sInit(ConfigFactory.load())
    val uid = java.util.UUID.randomUUID().toString
    complete {
      withFixture(test.toNoArgAsyncTest(FixtureParam(k8s, uid)))
    } lastly {
      Await.result(k8s.delete[Source](uid), 30 seconds)
      k8s.close
    }
  }
}
