package cr4s

import Foo.FooResource
import com.softwaremill.quicklens._
import controller.Reconciler
import skuber._
import skuber.LabelSelector.IsEqualRequirement
import skuber.apps.Deployment

class FooDeploymentReconciler extends Reconciler[FooResource, Deployment] {
  override def reconciler: Event => List[Action] = {
    case Modified(foo, Nil) =>
      List(Create(createDeployment(foo)))

    case Modified(foo, deployment :: tail) =>
      val updateAction: Option[Action] =
        deployment.copySpec.replicas match {
          case Some(i) =>
            if (foo.spec.replicas == i) None
            else
              Some(Update(deployment.modify(_.spec.each.replicas).setTo(Some(foo.spec.replicas))))

          case _ => None
        }

      val deleteActions = tail.map(Delete)

      updateAction.toList ++ deleteActions

    case Deleted(foo, deployments) => deployments.map(Delete)
  }

  def createDeployment(f: FooResource): Deployment = {
    val labels = Map(
      "app" -> "nginx",
      "controller" -> f.metadata.name
    )

    val container = Container(name = "nginx", image = "nginx:latest")

    val template = Pod.Template.Spec(metadata = ObjectMeta()).addLabels(labels).addContainer(container)

    val deployment = Deployment(
      metadata = ObjectMeta(
        name = f.spec.deploymentName,
        namespace = f.metadata.namespace,
      ))
      .withReplicas(f.spec.replicas)
      .withLabelSelector(LabelSelector(labels.map(x => IsEqualRequirement(x._1, x._2)).toList: _*))
      .withTemplate(template)

    addOwnerReference(f, deployment)
  }

}
