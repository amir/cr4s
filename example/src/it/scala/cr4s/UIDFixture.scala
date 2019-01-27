package cr4s

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.{fixture, FutureOutcome}
import skuber._
import skuber.api.client.RequestContext

trait UIDFixture extends fixture.AsyncFlatSpec {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  case class FixtureParam(context: RequestContext, uid: String)

  override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val k8s = k8sInit(ConfigFactory.load())
    complete {
      withFixture(test.toNoArgAsyncTest(FixtureParam(k8s, java.util.UUID.randomUUID().toString)))
    } lastly {
      k8s.close
    }
  }
}
