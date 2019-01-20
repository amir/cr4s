package cr4s

import Foo.FooResource
import com.softwaremill.quicklens._
import reconciler.Reconciler
import skuber._
import skuber.LabelSelector.IsEqualRequirement
import skuber.apps.Deployment

class FooDeploymentReconciler extends Reconciler[FooResource, Deployment] {

  override def doReconcile: Event => List[Action] = {
    case Modified(foo, Nil) =>
      List(
        Create(createDeployment(foo)),
        ChangeStatus(foo.name, x => x.withStatus(Foo.Status(foo.spec.replicas, 0)))
      )

    case Modified(foo, deployment :: tail) =>
      val updateAction: List[Action] =
        deployment.copySpec.replicas match {
          case Some(i) =>
            val updateAction =
              if (foo.spec.replicas == i) Nil
              else
                List(Update(deployment.modify(_.spec.each.replicas).setTo(Some(foo.spec.replicas))))

            val changeStatusAction = ChangeStatus(foo.name, f => f.withStatus(Foo.Status(foo.spec.replicas, i)))

            updateAction :+ changeStatusAction

          case _ => Nil
        }

      val deleteActions = tail.map(Delete)

      updateAction ++ deleteActions

    case Deleted(_, deployments) => deployments.map(Delete)
  }

  def createDeployment(f: FooResource): Deployment = {
    val labels = Map(
      "app" -> "nginx",
      "controller" -> f.metadata.name
    )

    val container = Container(name = "nginx", image = "nginx:latest")

    val template = Pod.Template.Spec(metadata = ObjectMeta()).addLabels(labels).addContainer(container)

    Deployment(
      metadata = ObjectMeta(
        name = f.spec.deploymentName,
        namespace = f.metadata.namespace,
      ))
      .withReplicas(f.spec.replicas)
      .withLabelSelector(LabelSelector(labels.map(x => IsEqualRequirement(x._1, x._2)).toList: _*))
      .withTemplate(template)
  }

}
