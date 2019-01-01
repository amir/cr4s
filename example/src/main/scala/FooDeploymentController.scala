package cr4s

import controller.Controller2
import skuber.LabelSelector.IsEqualRequirement
import skuber._
import skuber.apps.Deployment

class FooDeploymentController extends Controller2[Foo, Deployment] {
  override def reconciler: Event => List[Action] = {
    case Modified(foo, Nil) =>
      List(Create(createDeployment(foo)))

    case Modified(foo, deployment :: tail) =>
      val updateAction: Option[Action] = foo.spec.map(_.replicas).flatMap { replicas =>
        deployment.copySpec.replicas match {
          case Some(i) =>
            if (replicas == i) None
            else Some(Update(deployment.copy(spec = deployment.spec.map(a => a.copy(replicas = Some(replicas))))))

          case _ => None
        }
      }

      val deleteActions = tail.map(Delete)

      updateAction.toList ++ deleteActions

    case Deleted(foo, deployments) => deployments.map(Delete)
  }

  def createDeployment(f: Foo): Deployment = {
    val labels = Map(
      "app" -> "nginx",
      "controller" -> f.metadata.name
    )

    val container = Container(name = "nginx", image = "nginx:latest")

    val template = Pod.Template.Spec(metadata = ObjectMeta()).addLabels(labels).addContainer(container)

    Deployment(
      metadata = ObjectMeta(
        name = f.spec.fold("foo")(_.deploymentName),
        namespace = f.metadata.namespace,
        ownerReferences = List(ownerReference(f))
      ))
      .withReplicas(f.spec.fold(1)(_.replicas))
      .withLabelSelector(LabelSelector(labels.map(x => IsEqualRequirement(x._1, x._2)).toList: _*))
      .withTemplate(template)
  }

}
