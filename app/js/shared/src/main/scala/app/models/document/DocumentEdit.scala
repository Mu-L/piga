package app.models.document

import app.models.access.ModelFields
import app.models.document.DocumentEdit.TaskUpdate
import hydro.common.OrderToken
import hydro.common.time.Clock
import hydro.common.time.LocalDateTime
import hydro.models.access.ModelField
import hydro.models.modification.EntityModification
import hydro.models.UpdatableEntity
import hydro.models.UpdatableEntity.LastUpdateTime

import scala.collection.immutable.Seq
import scala.collection.mutable

case class DocumentEdit(removedTasks: Seq[Task], addedTasks: Seq[Task], taskUpdates: Seq[TaskUpdate]) {

  def reverse: DocumentEdit = ???

  def toEntityModifications: Seq[EntityModification] = ???
}
object DocumentEdit {
  case class TaskUpdate private (
      originalTask: Task,
      content: Option[TextWithMarkup],
      orderToken: Option[OrderToken],
      indentation: Option[Int],
      collapsed: Option[Boolean],
      delayedUntil: Option[Option[LocalDateTime]],
      tags: Option[Seq[String]],
  ) {
    def reverse: TaskUpdate = ???

    def toEntityModification(implicit clock: Clock,
                             document: Document): EntityModification.Update[TaskEntity] = {
      var newTaskEntity = originalTask.toTaskEntity
      val fieldMask = mutable.Buffer[ModelField[_, TaskEntity]]()

      def maybeApplyField[V](maybeValue: Option[V], field: ModelField[V, TaskEntity]): Unit = {
        if (maybeValue.isDefined) {
          newTaskEntity = field.set(newTaskEntity, maybeValue.get)
          fieldMask.append(field)
        }
      }

      maybeApplyField(content.map(_.toHtml), ModelFields.TaskEntity.contentHtml)
      maybeApplyField(orderToken, ModelFields.TaskEntity.orderToken)
      maybeApplyField(indentation, ModelFields.TaskEntity.indentation)
      maybeApplyField(collapsed, ModelFields.TaskEntity.collapsed)
      maybeApplyField(delayedUntil, ModelFields.TaskEntity.delayedUntil)
      maybeApplyField(tags, ModelFields.TaskEntity.tags)

      EntityModification.createUpdate(newTaskEntity, fieldMask.toVector)
    }
  }
  object TaskUpdate {
    def fromFields(
        originalTask: Task,
        content: TextWithMarkup = null,
        orderToken: OrderToken = null,
        indentation: Int = -1,
        collapsed: java.lang.Boolean = null,
        delayedUntil: Option[LocalDateTime] = null,
        tags: Seq[String] = null,
    ): TaskUpdate = {
      def ifUpdate[V](value: V, currentValue: V): Option[V] = value match {
        case null | -1      => None
        case `currentValue` => None
        case _              => Some(value)
      }
      TaskUpdate(
        originalTask = originalTask,
        content = ifUpdate(content, originalTask.content),
        orderToken = ifUpdate(orderToken, originalTask.orderToken),
        indentation = ifUpdate(indentation, originalTask.indentation),
        collapsed = ifUpdate(collapsed, originalTask.collapsed),
        delayedUntil = ifUpdate(delayedUntil, originalTask.delayedUntil),
        tags = ifUpdate(tags, originalTask.tags),
      )
    }
  }
}
