package app.models.document

import app.models.access.ModelFields
import app.models.document.Document.IndexedSelection
import hydro.common.DomNodeUtils.nodeIsLi
import hydro.common.GuavaReplacement.Iterables.getOnlyElement
import hydro.common.OrderToken
import hydro.models.access.DbQueryImplicits._
import hydro.models.access.JsEntityAccess
import org.scalajs.dom

import scala.annotation.tailrec
import scala.async.Async.async
import scala.async.Async.await
import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

final class Document(val id: Long,
                     val name: String,
                     val orderToken: OrderToken,
                     val tasks: Seq[Task],
                     private val idToIndexMap: Map[Long, Int]) {

  def withAppliedEdit(edit: DocumentEdit.WithUpdateTimes): Document = {
    val canonicalEdit = edit.canonicalized

    def comprehensiveUpdate(edit: DocumentEdit.WithUpdateTimes) = {
      val mutableTasks = tasks.toBuffer

      for (update <- edit.taskUpdates) {
        idToIndexMap.get(update.id) match {
          case Some(index) => mutableTasks.update(index, mutableTasks(index) mergedWith update)
          case None        =>
        }
      }
      for (removedTaskId <- edit.removedTasksIds) {
        idToIndexMap.get(removedTaskId) match {
          case Some(index) => mutableTasks.remove(index)
          case None        =>
        }
      }
      mutableTasks.appendAll(edit.addedTasks)

      mutableTasks.toVector.sorted
    }

    def quickUpdate(taskIndex: Int, taskUpdate: Task) = {
      // Optimization
      val oldTask = tasks(taskIndex)
      tasks.updated(taskIndex, oldTask mergedWith taskUpdate)
    }

    val newTasks =
      if (canonicalEdit.removedTasksIds.isEmpty && canonicalEdit.addedTasks.isEmpty && canonicalEdit.taskUpdates.size == 1) {
        val update = getOnlyElement(canonicalEdit.taskUpdates)
        maybeIndexOf(update.id, orderTokenHint = update.orderToken) match {
          case Some(taskIndex) if tasks(taskIndex).orderToken == update.orderToken =>
            quickUpdate(taskIndex, update)
          case _ => comprehensiveUpdate(canonicalEdit)
        }
      } else {
        comprehensiveUpdate(canonicalEdit)
      }

    new Document(
      id,
      name,
      orderToken,
      tasks = newTasks,
      idToIndexMap = newTasks.toStream.map(_.id).zipWithIndex.toMap,
    )
  }

  def updateFromDocumentEntity(documentEntity: DocumentEntity): Document = {
    require(id == documentEntity.id)
    new Document(
      id = id,
      name = documentEntity.name,
      orderToken = documentEntity.orderToken,
      tasks = tasks,
      idToIndexMap = idToIndexMap)
  }

  def maybeIndexOf(taskId: Long, orderTokenHint: OrderToken = null): Option[Int] = idToIndexMap.get(taskId)

  def tasksOption(index: Int): Option[Task] = index match {
    case i if i < 0             => None
    case i if i >= tasks.length => None
    case _                      => Some(tasks(index))
  }

  def tasksIn(selection: IndexedSelection): Seq[Task] = for (i <- selection.seqIndices) yield tasks(i)

  def toDocumentEntity: DocumentEntity = DocumentEntity(name, orderToken = orderToken, idOption = Some(id))

  case class FamilyTreeRange(parentSeqIndex: Int, lastChildSeqIndex: Int) {
    def numberOfTasks: Int = lastChildSeqIndex - parentSeqIndex + 1
  }
  def familyTreeRange(anyMemberSeqIndex: Int, rootParentIndentation: Int): Option[FamilyTreeRange] = {
    def findRootParentIndex(seqIndex: Int): Option[Int] = {
      var result = seqIndex
      while (result >= 0 && tasks(result).indentation > rootParentIndentation) {
        result -= 1
      }
      result match {
        case -1                                                         => None
        case index if tasks(index).indentation == rootParentIndentation => Some(index)
        case _                                                          => None
      }
    }
    def findLastChildIndex(seqIndex: Int, parentIndentation: Int): Int = {
      var lastChildIndex = seqIndex
      while (tasksOption(lastChildIndex + 1).isDefined &&
             tasks(lastChildIndex + 1).indentation > parentIndentation) {
        lastChildIndex += 1
      }
      lastChildIndex
    }

    for {
      task <- tasksOption(anyMemberSeqIndex)
      parentIndex <- findRootParentIndex(anyMemberSeqIndex)
    } yield
      FamilyTreeRange(
        parentIndex,
        findLastChildIndex(anyMemberSeqIndex, parentIndentation = tasks(parentIndex).indentation))
  }

  // **************** Object methods **************** //
  override def equals(o: scala.Any): Boolean = {
    o match {
      case that if this.hashCode != that.hashCode() => false
      case that: Document =>
        this.id == that.id && this.name == that.name && this.orderToken == that.orderToken && this.tasks == that.tasks
      case _ => false
    }
  }
  override lazy val hashCode: Int = {
    var code = 11 + id.hashCode()
    code = code * 7 + name.hashCode()
    code = code * 7 + orderToken.hashCode()
    code = code * 7 + tasks.hashCode()
    code
  }
  override def toString: String = s"Document($id, $name, $tasks)"
}
object Document {

  def fromDocumentEntity(entity: DocumentEntity)(implicit entityAccess: JsEntityAccess): Future[Document] =
    async {
      val tasks = await(
        entityAccess.newQuery[TaskEntity]().filter(ModelFields.TaskEntity.documentId === entity.id).data())
      val sortedTasks = tasks.map(Task.fromTaskEntity).sorted
      new Document(
        id = entity.id,
        name = entity.name,
        orderToken = entity.orderToken,
        tasks = sortedTasks,
        idToIndexMap = sortedTasks.toStream.map(_.id).zipWithIndex.toMap,
      )
    }

  case class IndexedCursor(seqIndex: Int, offsetInTask: Int) extends Ordered[IndexedCursor] {

    override def compare(that: IndexedCursor): Int = {
      import scala.math.Ordered.orderingToOrdered
      (this.seqIndex, this.offsetInTask) compare ((that.seqIndex, that.offsetInTask))
    }

    def detach(implicit document: Document): DetachedCursor =
      DetachedCursor(task = document.tasks(seqIndex), offsetInTask = offsetInTask)

    def proceedNTasks(n: Int): IndexedCursor = n match {
      case 0 => this
      case _ => IndexedCursor(seqIndex + n, 0)
    }
    def plusOffset(diff: Int): IndexedCursor = IndexedCursor(seqIndex, offsetInTask + diff)
    def minusOffset(diff: Int): IndexedCursor = plusOffset(-diff)

    def plusTasks(seqIndexDiff: Int): IndexedCursor = IndexedCursor(seqIndex + seqIndexDiff, offsetInTask)

    def plusOffsetInSeq(diff: Int)(implicit document: Document): IndexedCursor = {
      val tasks = document.tasks
      @tailrec
      def fixOffset(c: IndexedCursor): IndexedCursor = c.offsetInTask match {
        case offset if offset < 0 =>
          if (c.seqIndex == 0) {
            IndexedCursor(0, 0)
          } else {
            fixOffset(IndexedCursor(c.seqIndex - 1, tasks(c.seqIndex - 1).contentString.length + offset + 1))
          }
        case offset if offset > tasks(c.seqIndex).contentString.length =>
          if (c.seqIndex == tasks.length - 1) {
            IndexedCursor(tasks.length - 1, tasks(tasks.length - 1).contentString.length)
          } else {
            fixOffset(IndexedCursor(c.seqIndex + 1, offset - tasks(c.seqIndex).contentString.length - 1))
          }
        case _ => c
      }
      fixOffset(IndexedCursor(seqIndex, offsetInTask + diff))
    }
    def minusOffsetInSeq(diff: Int)(implicit document: Document): IndexedCursor = plusOffsetInSeq(-diff)

    def plusWord(implicit document: Document): IndexedCursor = moveWord(step = 1)
    def minusWord(implicit document: Document): IndexedCursor = moveWord(step = -1)
    private def moveWord(step: Int)(implicit document: Document): IndexedCursor = {
      val result = copy(offsetInTask = {
        val task = document.tasks(seqIndex).contentString
        @tailrec
        def move(offsetInTask: Int, seenWord: Boolean = false): Int = {
          val nextOffset = offsetInTask + step
          if (nextOffset < 0 || nextOffset > task.length) {
            offsetInTask
          } else {
            val currentChar = if (step > 0) task.charAt(offsetInTask) else task.charAt(nextOffset)
            val currentCharIsWord = currentChar.isLetterOrDigit
            if (currentCharIsWord) {
              move(nextOffset, seenWord = true)
            } else {
              if (seenWord) {
                offsetInTask
              } else {
                move(nextOffset, seenWord = false)
              }
            }
          }
        }
        move(offsetInTask)
      })
      if (this == result) {
        // No movement happened --> move to the next/previous task
        plusOffsetInSeq(step)
      } else {
        result
      }
    }

    def toStartOfTask: IndexedCursor = IndexedCursor(seqIndex, offsetInTask = 0)
    def toEndOfTask(implicit document: Document): IndexedCursor =
      IndexedCursor(seqIndex, offsetInTask = document.tasks(seqIndex).contentString.length)
  }
  object IndexedCursor {
    def atStartOfTask(seqIndex: Int): IndexedCursor = IndexedCursor(seqIndex, 0)
    def atEndOfTask(seqIndex: Int)(implicit document: Document) =
      IndexedCursor(seqIndex, document.tasks(seqIndex).contentString.length)
  }

  case class IndexedSelection(start: IndexedCursor, end: IndexedCursor) {
    require(start <= end)

    def detach(implicit document: Document): DetachedSelection = DetachedSelection(start.detach, end.detach)
    def isSingleton: Boolean = start == end

    def seqIndices: Range = start.seqIndex to end.seqIndex
    def startOffsetInTask(task: Task)(implicit document: Document): Int =
      if (task == document.tasks(start.seqIndex)) start.offsetInTask else 0
    def endOffsetInTask(task: Task)(implicit document: Document): Int =
      if (task == document.tasks(end.seqIndex)) end.offsetInTask else task.contentString.length

    def includeChildren(collapsedOnly: Boolean = false)(implicit document: Document): IndexedSelection = {
      val task = document.tasks(end.seqIndex)

      var index = end.seqIndex
      if (!collapsedOnly || task.collapsed) {
        while (document.tasksOption(index + 1).isDefined &&
               document.tasks(index + 1).indentation > task.indentation) {
          index += 1
        }
      }
      IndexedSelection(
        start = start,
        end = if (index == end.seqIndex) end else IndexedCursor(index, 0)
      )
    }
  }
  object IndexedSelection {
    def singleton(cursor: IndexedCursor): IndexedSelection = IndexedSelection(start = cursor, end = cursor)

    def tupleFromSelection(selection: dom.raw.Selection): IndexedSelection = {
      val anchor = cursorFromNode(selection.anchorNode, selection.anchorOffset)
      val focus = cursorFromNode(selection.focusNode, selection.focusOffset)
      if (anchor < focus) IndexedSelection(anchor, focus) else IndexedSelection(focus, anchor)
    }

    private def cursorFromNode(node: dom.raw.Node, offset: Int): IndexedCursor = {
      parentLiElement(node) match {
        case None => IndexedCursor(0, 0) // Fallback
        case Some(parentLi) =>
          val offsetInTask = {
            val preCursorRange = dom.document.createRange()
            preCursorRange.selectNodeContents(parentLi)
            preCursorRange.setEnd(node, offset)
            preCursorRange.toString.length
          }

          IndexedCursor(seqIndex = parentLi.getAttribute("num").toInt, offsetInTask = offsetInTask)
      }
    }

    private def parentLiElement(node: dom.raw.Node): Option[dom.raw.Element] = {
      if (node == null) {
        println("  Warning: Could not find parent li element")
        None
      } else if (nodeIsLi(node)) {
        Some(node.asInstanceOf[dom.raw.Element])
      } else {
        parentLiElement(node.parentNode)
      }
    }
  }

  case class DetachedCursor(task: Task, offsetInTask: Int) {
    def attachToDocument(implicit document: Document): IndexedCursor =
      IndexedCursor(
        seqIndex = document.maybeIndexOf(task.id, orderTokenHint = task.orderToken) getOrElse {
          println(s"  Warning: Could not find task in document: task = $task")
          0
        },
        offsetInTask = offsetInTask
      )
  }
  case class DetachedSelection(start: DetachedCursor, end: DetachedCursor) {
    def isSingleton: Boolean = start == end

    def attachToDocument(implicit document: Document): IndexedSelection =
      IndexedSelection(start = start.attachToDocument, end = end.attachToDocument)
  }
  object DetachedSelection {
    def singleton(cursor: DetachedCursor): DetachedSelection = DetachedSelection(cursor, cursor)
  }
}
