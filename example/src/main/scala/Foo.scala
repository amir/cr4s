package cr4s

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json}
import skuber.{ListResource, NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition, ResourceSpecification}
import skuber.json.format._

case class Foo(
              kind: String = "Foo",
              apiVersion: String = "v1alpha1",
              metadata: ObjectMeta,
              spec: Option[Foo.Spec] = None,
              status: Option[Foo.Status] = None
              ) extends ObjectResource

object Foo {
  val specification = NonCoreResourceSpecification(
    "samplecontroller.k8s.io", "v1alpha1",
    ResourceSpecification.Scope.Namespaced,
    ResourceSpecification.Names(plural="foos", singular="foo", kind="Foo", shortNames=List.empty[String])
  )

  case class Spec(deploymentName: String, replicas: Int)
  implicit val specFormat: Format[Spec] = Json.format[Spec]

  case class Status(replicas: Int = 0, availableReplicas: Int = 0)
  implicit val statusFormat: Format[Status] = (
    (JsPath \ "replicas").formatMaybeEmptyInt() and
      (JsPath \ "availableReplicas").formatMaybeEmptyInt()
  )(Status.apply _, unlift(Status.unapply))

  implicit val fooFormat: Format[Foo] = (
    objFormat and
      (JsPath \ "spec").formatNullable[Spec] and
      (JsPath \ "status").formatNullable[Status]
  )(Foo.apply _, unlift(Foo.unapply))

  type FooList = ListResource[Foo]
  implicit val fooListFormat: Format[FooList] = ListResourceFormat[Foo]

  implicit val fooDef = new ResourceDefinition[Foo] { def spec: ResourceSpecification = specification }
  implicit val fooListDef = new ResourceDefinition[FooList] { def spec: ResourceSpecification = specification }
}
