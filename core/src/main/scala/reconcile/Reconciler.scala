package cr4s
package reconcile

import skuber.api.client.{LoggingContext, RequestContext, WatchEvent}
import skuber.ObjectResource

import scala.concurrent.ExecutionContext

trait Reconciler[O <: ObjectResource] {
  def reconcile(l: WatchEvent[O])(implicit context: RequestContext, lc: LoggingContext, ec: ExecutionContext): Unit
}
