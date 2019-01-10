package cr4s
package interpreter

import akka.NotUsed
import akka.stream.scaladsl.{ Flow, Sink }
import cr4s.controller.Controller
import play.api.libs.json.Format
import scala.concurrent.ExecutionContext
import skuber.{ ObjectResource, ResourceDefinition }
import skuber.api.client.RequestContext

class SkuberInterpreter[C <: Controller[_ <: ObjectResource, _ <: ObjectResource]](k8s: RequestContext,
                                                                                   controller2: C) {

  def sink(implicit sourceFmt: Format[controller2.Source],
           sourceResourceDefinition: ResourceDefinition[controller2.Source],
           targetFmt: Format[controller2.Target],
           targetResourceDefinition: ResourceDefinition[controller2.Target],
           ec: ExecutionContext): Sink[List[C#Action], NotUsed] = Flow[List[C#Action]].to(Sink.foreach { actions =>
    actions.foreach {
      case controller2.Create(c) => k8s.create[controller2.Target](c)
      case controller2.Update(c) => k8s.update[controller2.Target](c)
      case controller2.Delete(c) => k8s.delete[controller2.Target](c.name)
      case otherwise             => k8s.log.info(s"Action $otherwise ignored")
    }
  })

}
