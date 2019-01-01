package cr4s
package manager

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import cr4s.controller.Controller2
import play.api.libs.json.Format
import scala.concurrent.ExecutionContext
import skuber.{ListResource, ObjectResource, ResourceDefinition}
import skuber.api.client.RequestContext

class Manager2[S <: ObjectResource, T <: ObjectResource](controllers: Controller2[S, T]*) (
             implicit context: RequestContext,
             listFmt1: Format[ListResource[S]],
             listRd1: ResourceDefinition[ListResource[S]],
             objFmt1: Format[S],
             objRd1: ResourceDefinition[S],
             listFmt2: Format[ListResource[T]],
             listRd2: ResourceDefinition[ListResource[T]],
             objFmt2: Format[T],
             objRd2: ResourceDefinition[T],
             ec: ExecutionContext,
             materializer: Materializer){

  def watch = {
    controllers
      .map(c => Source.fromFutureSource(Controller2.watchSource(c)))
      /*.foldLeft(Source.empty[controllers.Event])(_ merge _)
      .map(e => e.reconciler.reconcile(e.watchEvent))*/
  }

  /*def watchA[O <: ObjectResource] = {
    context.list[ListResource[O]].map { l =>
      val initialSource = Source(l.items.map(l => WatchEvent(EventType.MODIFIED, l)))
      val watchedSource = context.watchAllContinuously[O](Some(l.resourceVersion))

      initialSource.concat(watchedSource).map(EventImpl.apply)
    }
  }*/
}
