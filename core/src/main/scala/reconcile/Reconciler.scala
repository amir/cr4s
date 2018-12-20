package cr4s
package reconcile

import skuber.api.client.{LoggingContext, RequestContext, WatchEvent}
import skuber.ObjectResource

import scala.concurrent.ExecutionContext

abstract class Reconciler[O <: ObjectResource](implicit context: RequestContext, lc: LoggingContext, ec: ExecutionContext) {
  def reconcile(l: WatchEvent[O]): Unit
}
