package cr4s

import Foo.FooResource
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.softwaremill.quicklens._
import com.typesafe.config.ConfigFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import scala.concurrent.Await
import scala.concurrent.duration._
import skuber._
import skuber.apps.Deployment
import skuber.json.apps.format._

class FooDeploymentReconcilerTest extends AsyncFlatSpec with Eventually with Matchers {
  val fooName = java.util.UUID.randomUUID().toString

  behavior of "Foo Deployment Reconciler"

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  implicit val k8s = k8sInit(ConfigFactory.load())
  implicit val logger = k8s.log

  val foo = Foo(fooName, Foo.Spec(fooName, 1))

  it should "create the Foo CRD" in {
    k8s.create[FooResource](foo) map { f =>
      assert(f.name == fooName)
    }
  }

  val reconciler = new FooDeploymentReconciler
  reconciler.graph(1).run()

  it should "create a Deployment corresponding to the CRD" in {
    eventually(timeout(200 seconds), interval(5 seconds)) {
      val deployment = k8s.get[Deployment](fooName)
      ScalaFutures.whenReady(deployment, timeout(2 seconds), interval(1 seconds)) { d =>
        d.copySpec.replicas shouldBe Some(foo.spec.replicas)
      }
    }
  }

  it should "reflect CRD changes in the Deployment" in {
    k8s.get[FooResource](fooName).flatMap { f =>
      val replicas = f.spec.replicas
      val updateFoo = f.modify(_.spec.replicas).setTo(replicas+1)
      k8s.update[FooResource](updateFoo).flatMap { _ =>
        eventually(timeout(200 seconds), interval(5 seconds)) {
          val deployment = k8s.get[Deployment](f.spec.deploymentName)
          ScalaFutures.whenReady(deployment, timeout(2 seconds), interval(1 second)) { d =>
            d.copySpec.replicas shouldBe Some(replicas+1)
          }
        }
      }
    }
  }

  it should "update CRD's status subresource" in {
    eventually(timeout(200 seconds), interval(3 seconds)) {
      val fr = k8s.get[FooResource](fooName)
      ScalaFutures.whenReady(fr, timeout(2 seconds), interval(1 seconds)) { f =>
        f.status shouldBe Some(Foo.Status(2,2))
      }
    }
  }

  it should "delete Deployment once CRD is deleted" in {
    k8s.delete[FooResource](fooName).map { _ =>
      eventually(timeout(200 seconds), interval(3 seconds)) {
        val deployment = k8s.get[Deployment](fooName)
        val retrieved = Await.ready(deployment, 2 seconds).value.get
        retrieved match {
          case _: scala.util.Success[_] => assert(false)
          case scala.util.Failure(ex) => ex match {
            case ex: K8SException if ex.status.code.contains(404) => assert(true)
            case _ => assert(false)
          }
        }
      }
    }
  }
}