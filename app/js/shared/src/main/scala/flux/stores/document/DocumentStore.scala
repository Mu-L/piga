package flux.stores.document

import scala.concurrent.duration._
import scala.scalajs.js
import flux.stores.StateStore
import flux.stores.document.DocumentStore.{Replacement, State, SyncerWithReplenishingDelay}
import models.access.JsEntityAccess
import models.document.{Document, Task}
import models.modification.EntityModification

import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.js.timers.SetTimeoutHandle

final class DocumentStore(initialDocument: Document)(implicit entityAccess: JsEntityAccess)
    extends StateStore[State] {
  entityAccess.registerListener(JsEntityAccessListener)

  private var _state: State = State(document = initialDocument)
  private val syncer: SyncerWithReplenishingDelay[Replacement] = new SyncerWithReplenishingDelay(
    delay = 500.milliseconds,
    merge = _ merge _,
    sync = syncReplacement
  )

  // **************** Implementation of StateStore methods **************** //
  override def state: State = _state

  // **************** Additional public API **************** //
  /** Replaces tasks in state without calling the store listeners.
    *
    * Note that the listeners still will be called once the EntityModifications reach the back-end and are pushed back
    * to this front-end.
    */
  def replaceTasksWithoutCallingListeners(toReplace: Iterable[Task], toAdd: Iterable[Task]): Document = {
    val newDocument = _state.document.replaced(toReplace, toAdd)
    _state = _state.copy(document = newDocument)
    syncer.syncWithDelay(Replacement(removedTasks = toReplace.toSet, addedTasks = toAdd.toSet))
    newDocument
  }

  // **************** Private helper methods **************** //
  private def syncReplacement(replacement: Replacement): Future[_] = {
    val deletes = replacement.removedTasks.toVector
      .map(t => EntityModification.createDelete(t.toTaskEntity(_state.document)))
    val adds = replacement.addedTasks.map(t => EntityModification.Add(t.toTaskEntity(_state.document)))

    entityAccess.persistModifications(deletes ++ adds)
  }

  // **************** Private inner types **************** //
  private[document] object JsEntityAccessListener extends JsEntityAccess.Listener {
    override def modificationsAddedOrPendingStateChanged(modifications: Seq[EntityModification]): Unit = {
      // TODO
    }
  }
}

object DocumentStore {
  case class State(document: Document)

  private class SyncerWithReplenishingDelay[T](delay: FiniteDuration,
                                               merge: (T, T) => T,
                                               sync: T => Future[_]) {
    var currentValue: T = _
    var timeoutHandle: SetTimeoutHandle = _

    def syncWithDelay(addedValue: T): Unit = {
      val newValue = currentValue match {
        case null => addedValue
        case _    => merge(currentValue, addedValue)
      }
      currentValue = newValue

      if (timeoutHandle != null) {
        js.timers.clearTimeout(timeoutHandle)
      }
      timeoutHandle = js.timers.setTimeout(delay) {
        sync(newValue)
      }
    }
  }

  private case class Replacement(removedTasks: Set[Task], addedTasks: Set[Task]) {
    def merge(that: Replacement): Replacement = {
      val overlappingTasks = this.addedTasks intersect that.removedTasks

      Replacement(
        removedTasks = this.removedTasks ++ that.removedTasks.filterNot(overlappingTasks),
        addedTasks = that.addedTasks ++ this.addedTasks.filterNot(overlappingTasks),
      )
    }
  }
}
