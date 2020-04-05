package hydro.common.testing

import hydro.models.modification.EntityModification
import hydro.models.modification.EntityType
import hydro.models.Entity
import hydro.models.access.DbQueryExecutor
import hydro.models.access.LocalDatabase
import hydro.models.access.PendingModificationsListener
import hydro.models.access.SingletonKey

import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.js

final class FakeLocalDatabase extends LocalDatabase {
  private val modificationsBuffer: ModificationsBuffer = new ModificationsBuffer()
  private val _pendingModifications: mutable.Buffer[EntityModification] = mutable.Buffer()
  private val singletonMap: mutable.Map[SingletonKey[_], js.Any] = mutable.Map()

  // **************** Getters ****************//
  override def queryExecutor[E <: Entity: EntityType]() = {
    DbQueryExecutor.fromEntities(modificationsBuffer.getAllEntitiesOfType[E]).asAsync
  }
  override def pendingModifications() = Future.successful(_pendingModifications.toVector)
  override def getSingletonValue[V](key: SingletonKey[V]) = {
    Future.successful(singletonMap.get(key) map key.valueConverter.toScala)
  }
  override def isEmpty = {
    Future.successful(modificationsBuffer.isEmpty && singletonMap.isEmpty)
  }

  // **************** Setters ****************//
  override def applyModifications(modifications: Seq[EntityModification]) = {
    modificationsBuffer.addModifications(modifications)
    Future.successful(true)
  }
  override def addAll[E <: Entity: EntityType](entities: Seq[E]) = {
    modificationsBuffer.addEntities(entities)
    Future.successful(true)
  }
  override def addPendingModifications(modifications: Seq[EntityModification]) = Future.successful {
    _pendingModifications ++= modifications
    true
  }
  override def removePendingModifications(modifications: Seq[EntityModification]) = Future.successful {
    _pendingModifications --= modifications
    true
  }
  override def setSingletonValue[V](
      key: SingletonKey[V],
      value: V,
      abortUnlessExistingValueEquals: V = null,
  ) = {
    singletonMap.put(key, key.valueConverter.toJs(value))
    Future.successful(true)
  }
  override def addSingletonValueIfNew[V](key: SingletonKey[V], value: V) = {
    if (singletonMap contains key) {
      Future.successful(false)
    } else {
      singletonMap.put(key, key.valueConverter.toJs(value))
      Future.successful(true)
    }
  }
  override def save() = Future.successful((): Unit)
  override def resetAndInitialize[V](alsoSetSingleton: (SingletonKey[V], V) = null) = {
    modificationsBuffer.clear()
    singletonMap.clear()
    Future.successful((): Unit)
  }

  override def registerPendingModificationsListener(listener: PendingModificationsListener): Unit = {}

  // **************** Additional methods for tests ****************//
  def allModifications: Seq[EntityModification] = modificationsBuffer.getModifications()
}
