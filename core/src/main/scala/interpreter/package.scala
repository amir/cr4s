package cr4s

package object interpreter {
  sealed trait Result
  case object Success extends Result
  case class Failure(t: Throwable) extends Result

  sealed trait ActionType {
    def name: String
    def namespace: String
    def kind: String
  }
  case class CreateAction(name: String, namespace: String, kind: String) extends ActionType
  case class UpdateAction(name: String, namespace: String, kind: String) extends ActionType
  case class DeleteAction(name: String, namespace: String, kind: String) extends ActionType
  case class ChangeStatusAction(name: String, namespace: String, kind: String) extends ActionType

  final case class ActionResult(action: ActionType, result: Result)
}
