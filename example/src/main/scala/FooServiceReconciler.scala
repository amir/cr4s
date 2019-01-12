package cr4s

import Foo.FooResource
import cr4s.controller.Reconciler
import skuber.{ ObjectMeta, Service }

class FooServiceReconciler extends Reconciler[FooResource, Service] {
  override def reconciler: Event => List[Action] = {
    case Modified(foo, Nil) =>
      List(Create(createService(foo)))

    case Modified(_, service :: tail) => Nil

    case Deleted(_, services) => services.map(Delete)
  }

  def createService(f: FooResource): Service = {

    val spec = Service.Spec(ports = List(Service.Port(port = 80)),
                            selector = Map("app" -> "nginx", "controller" -> f.metadata.name))

    Service(metadata = ObjectMeta(
              name = f.spec.deploymentName,
              ownerReferences = List(ownerReference(f))
            ),
            spec = Some(spec))
  }
}
