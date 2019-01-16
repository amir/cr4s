package cr4s
package interpreter

import akka.NotUsed
import akka.stream.scaladsl.Flow
import cr4s.controller.Reconciler
import play.api.libs.json.Format
import scala.concurrent.{ ExecutionContext, Future }
import skuber.{ ObjectResource, ResourceDefinition }
import skuber.api.client.RequestContext

class SkuberInterpreter[C <: Reconciler[_ <: ObjectResource, _ <: ObjectResource]](k8s: RequestContext, controller: C) {
  def flow(parallelism: Int)(implicit sourceFmt: Format[controller.Source],
                             sourceResourceDefinition: ResourceDefinition[controller.Source],
                             targetFmt: Format[controller.Target],
                             targetResourceDefinition: ResourceDefinition[controller.Target],
                             ec: ExecutionContext): Flow[List[C#Action], C#ActionResult, NotUsed] =
    Flow[List[C#Action]].mapConcat(identity).mapAsync(parallelism) {
      case controller.Create(c) =>
        val action = controller.CreateAction(c.metadata.name, c.metadata.namespace, c.kind)
        k8s.create[controller.Target](c).map { _ =>
          controller.ActionResult(action, controller.Success)
        } recover {
          case t: Throwable => controller.ActionResult(action, controller.Failure(t))
        }

      case controller.Update(c) =>
        val action = controller.UpdateAction(c.metadata.name, c.metadata.namespace, c.kind)
        k8s.update[controller.Target](c).map { _ =>
          controller.ActionResult(action, controller.Success)
        } recover {
          case t: Throwable => controller.ActionResult(action, controller.Failure(t))
        }

      case controller.Delete(c) =>
        val action = controller.DeleteAction(c.metadata.name, c.metadata.namespace, c.kind)
        k8s.delete[controller.Target](c.name).map { _ =>
          controller.ActionResult(action, controller.Success)
        } recover {
          case t: Throwable => controller.ActionResult(action, controller.Failure(t))
        }

      case controller.ChangeStatus(_) =>
        Future {
          controller.ActionResult(controller.ChangeStatusAction("", "", ""), controller.Success)
        }
    }
}
