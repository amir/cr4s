package cr4s

import akka.event.LoggingAdapter
import com.softwaremill.quicklens._
import cr4s.Foo.FooResource
import cr4s.interpreter.SkuberInterpreter
import org.scalatest.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.duration._
import skuber.api.client.RequestContext
import skuber.apps.Deployment
import skuber.json.apps.format._

class FooDeploymentReconcilerTest extends UIDFixture with Eventually with Matchers {
  val reconciler = new FooDeploymentReconciler

  type Source = FooResource

  implicit val rd = Foo.fooResourceDefinition

  it should "create the Foo CRD" in { fixture =>
    val foo = Foo(fixture.uid, Foo.Spec(fixture.uid, 1))
    fixture.context.create[FooResource](foo) map { f =>
      assert(f.name === fixture.uid)
    }
  }

  it should "create a Deployment corresponding to the CRD" in { fixture =>
    val foo = Foo(fixture.uid, Foo.Spec(fixture.uid, 1))
    implicit val context: RequestContext = fixture.context
    implicit val logger: LoggingAdapter = context.log
    context.create[FooResource](foo) flatMap { f =>
      val reconciler = new FooDeploymentReconciler
      val interpreter = new SkuberInterpreter(context, reconciler)
      reconciler.graph(interpreter.flow(1), 1).run()
      eventually(timeout(200 seconds), interval(5 seconds)) {
        val deployment = context.get[Deployment](fixture.uid)
        ScalaFutures.whenReady(deployment, timeout(2 seconds), interval(1 seconds)) { d =>
          d.copySpec.replicas shouldBe Some(f.spec.replicas)
        }
      }
    }
  }

  it should "reflect CRD changes in the Deployment" in { fixture =>
    val foo = Foo(fixture.uid, Foo.Spec(fixture.uid, 1))
    implicit val context: RequestContext = fixture.context
    implicit val logger: LoggingAdapter = context.log
    context.create[FooResource](foo) flatMap { f =>
      val reconciler = new FooDeploymentReconciler
      val interpreter = new SkuberInterpreter(context, reconciler)
      reconciler.graph(interpreter.flow(1), 1).run()
      val replicas = f.spec.replicas
      val updatedFoo = f.modify(_.spec.replicas).setTo(replicas + 1)
      context.update[FooResource](updatedFoo) flatMap { _ =>
        eventually(timeout(200 seconds), interval(5 seconds)) {
          val deployment = context.get[Deployment](fixture.uid)
          ScalaFutures.whenReady(deployment, timeout(2 seconds), interval(1 seconds)) { d =>
            d.copySpec.replicas shouldBe Some(replicas + 1)
          }
        }
      }
    }
  }

  it should "update CRD's status subresources" in { fixture =>
    val foo = Foo(fixture.uid, Foo.Spec(fixture.uid, 1))
    implicit val context: RequestContext = fixture.context
    implicit val logger: LoggingAdapter = context.log
    context.create[FooResource](foo) flatMap { f =>
      val reconciler = new FooDeploymentReconciler
      val interpreter = new SkuberInterpreter(context, reconciler)
      reconciler.graph(interpreter.flow(1), 1).run()
      eventually(timeout(200 seconds), interval(5 seconds)) {
        val fr = context.get[FooResource](fixture.uid)
        ScalaFutures.whenReady(fr, timeout(2 seconds), interval(1 seconds)) { f =>
          f.status shouldBe Some(Foo.Status(1, 1))
        }
      }
    }
  }
}
