package cr4s

import cr4s.reconcile.Reconciler
import scala.concurrent.ExecutionContext
import skuber.api.client
import skuber.api.client.{ EventType, LoggingContext, RequestContext }
import skuber.apps.Deployment

class DeploymentReconciler(implicit context: RequestContext, lc: LoggingContext, ec: ExecutionContext)
    extends Reconciler[Deployment] {
  override def reconcile(l: client.WatchEvent[Deployment]): Unit = {
    l._type match {
      case EventType.DELETED =>
        l._object.metadata.ownerReferences.find(_.kind == "Foo").map { ownerReference =>
          }
    }
  }
}
