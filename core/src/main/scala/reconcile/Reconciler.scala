package cr4s
package reconcile

import skuber.api.client.WatchEvent
import skuber.ObjectResource

trait Reconciler[O <: ObjectResource] {
  def reconcile(l: WatchEvent[O]): Unit
}
