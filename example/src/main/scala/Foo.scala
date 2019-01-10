package cr4s
import play.api.libs.json.{ Format, Json }
import skuber.{ CustomResource, ListResource, ResourceDefinition }
import skuber.ResourceSpecification.Subresources
import skuber.apiextensions.CustomResourceDefinition

object Foo {

  type FooResource = CustomResource[Foo.Spec, Foo.Status]
  type FooResourceList = ListResource[FooResource]

  case class Spec(deploymentName: String, replicas: Int)
  case class Status(replicas: Int, availableReplicas: Int)

  implicit val specFormat: Format[Spec] = Json.format[Spec]
  implicit val statusFormat: Format[Status] = Json.format[Status]

  implicit val fooResourceDefinition = ResourceDefinition[FooResource](
    group = "samplecontroller.k8s.io",
    version = "v1alpha1",
    kind = "Foo",
    plural = Some("foos"),
    singular = Some("foo"),
    shortNames = List.empty[String],
    subresources = Some(Subresources().withStatusSubresource)
  )

  implicit val statusSubresourceEnabled = CustomResource.statusMethodsEnabler[FooResource]

  val crd = CustomResourceDefinition[FooResource]

  def apply(name: String, spec: Spec): CustomResource[Spec, Status] = CustomResource[Spec, Status](spec).withName(name)
}
