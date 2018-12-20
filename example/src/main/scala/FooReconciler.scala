package cr4s

import akka.http.scaladsl.model.StatusCodes
import cr4s.reconcile.Reconciler
import play.api.libs.json.Format
import skuber.LabelSelector.IsEqualRequirement
import skuber.api.client
import skuber._
import skuber.api.client.{LoggingContext, RequestContext}
import skuber.apps.v1.Deployment

import scala.concurrent.{ExecutionContext, Future}

class FooReconciler(implicit context: RequestContext, lc: LoggingContext, ec: ExecutionContext) extends Reconciler[Foo] {

  def getInNamespaceOption[O <: ObjectResource](name: String, namespace: String)(
    implicit fmt: Format[O], rd: ResourceDefinition[O]
  ): Future[Option[O]] = {
    context.getInNamespace[O](name, namespace).map { result =>
      Some(result)
    } recover {
      case ex: K8SException if ex.status.code.contains(StatusCodes.NotFound.intValue) => None
    }
  }

  def createDeployment(f: Foo): Future[Deployment] = {
    val labels = Map(
      "app" -> "nginx",
      "controller" -> f.metadata.name
    )

    val container = Container(name = "nginx", image = "nginx:latest")

    val template = Pod.Template.Spec(metadata = ObjectMeta()).addLabels(labels).addContainer(container)

    val deployment = Deployment(metadata = ObjectMeta(
      name = f.spec.fold("foo")(_.deploymentName),
      namespace = f.metadata.namespace,
      ownerReferences = List(OwnerReference(
        apiVersion = f.apiVersion, kind = f.kind, name = f.metadata.name, uid = f.uid,
        controller = Some(true), blockOwnerDeletion = Some(true)
      ))
    )).withReplicas(f.spec.fold(1)(_.replicas))
      .withLabelSelector(LabelSelector(labels.map(x => IsEqualRequirement(x._1, x._2)).toList: _*))
      .withTemplate(template)

    context.create[Deployment](deployment)
  }

  def updateDeployment(foo: Foo, deployment: Deployment): Future[Deployment] = {
    val updated = foo.spec.fold(deployment) { spec =>
      deployment.withResourceVersion(foo.metadata.resourceVersion)
        .copy(metadata = deployment.metadata.copy(name = spec.deploymentName))
        .withReplicas(spec.replicas)
    }

    context.update[Deployment](updated)
  }

  override def reconcile(l: client.WatchEvent[Foo]): Unit = {
    l._object.spec.foreach { spec =>
      getInNamespaceOption[Deployment](name = spec.deploymentName, namespace = l._object.metadata.namespace).map {
        case Some(d) => updateDeployment(l._object, d)
        case None => createDeployment(l._object)
      }
    }
  }
}
