package cr4s
package manager

import cr4s.controller.Controller

import scala.concurrent.{ExecutionContext, Future}

class Manager(controllers: Seq[Controller[_]])(implicit ec: ExecutionContext) {
  def watch = {
    Future.sequence(controllers.map(_.watch))
  }
}
