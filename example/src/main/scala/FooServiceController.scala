package cr4s
import cr4s.controller.Controller2
import skuber.{ ObjectMeta, Service }

class FooServiceController extends Controller2[Foo, Service] {
  override def reconciler: Event => List[Action] = {
    case Modified(foo, Nil) =>
      List(Create(createService(foo)))

    case Modified(_, service :: tail) => Nil

    case Deleted(_, services) => services.map(Delete)
  }

  def createService(f: Foo): Service = {

    val spec = Service.Spec(ports = List(Service.Port(port = 80)),
                            selector = Map("app" -> "nginx", "controller" -> f.metadata.name))

    Service(metadata = ObjectMeta(
              name = f.spec.fold(f.name)(_.deploymentName),
              ownerReferences = List(ownerReference(f))
            ),
            spec = Some(spec))
  }
}
