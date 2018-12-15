package cr4s
package reconcile

import skuber.{K8SWatchEvent, ObjectResource}

trait Reconciler[O <: ObjectResource] {
  def reconcile(l: K8SWatchEvent[O]): Unit
}