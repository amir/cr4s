package cr4s
package interpreter

import akka.NotUsed
import akka.stream.scaladsl.{ Flow, Sink }
import cr4s.controller.Reconciler
import play.api.libs.json.Format
import scala.concurrent.ExecutionContext
import skuber.{ ObjectResource, ResourceDefinition }
import skuber.api.client.RequestContext

class SkuberInterpreter[C <: Reconciler[_ <: ObjectResource, _ <: ObjectResource]](k8s: RequestContext, controller: C) {

  def sink(implicit sourceFmt: Format[controller.Source],
           sourceResourceDefinition: ResourceDefinition[controller.Source],
           targetFmt: Format[controller.Target],
           targetResourceDefinition: ResourceDefinition[controller.Target],
           ec: ExecutionContext): Sink[List[C#Action], NotUsed] = Flow[List[C#Action]].to(Sink.foreach { actions =>
    actions.foreach {
      case controller.Create(c) => k8s.create[controller.Target](c)
      case controller.Update(c) => k8s.update[controller.Target](c)
      case controller.Delete(c) => k8s.delete[controller.Target](c.name)
      case otherwise            => k8s.log.info(s"Action $otherwise ignored")
    }
  })

}
