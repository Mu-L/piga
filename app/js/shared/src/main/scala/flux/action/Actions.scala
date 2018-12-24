package flux.action

import common.OrderToken
import hydro.flux.action.Action
import models.document.DocumentEntity

import scala.collection.immutable.Seq

object Actions {

  // **************** Document-related actions **************** //
  case class AddEmptyDocument(name: String, orderToken: OrderToken) extends Action
  case class UpdateDocuments(documents: Seq[DocumentEntity]) extends Action
  case class RemoveDocument(existingDocument: DocumentEntity) extends Action
}
