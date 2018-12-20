package cr4s
package reconcile

import scala.concurrent.ExecutionContext
import skuber.ObjectResource
import skuber.api.client.{ LoggingContext, RequestContext, WatchEvent }

abstract class Reconciler[O <: ObjectResource](implicit context: RequestContext,
                                               lc: LoggingContext,
                                               ec: ExecutionContext) {
  def reconcile(l: WatchEvent[O]): Unit
}
