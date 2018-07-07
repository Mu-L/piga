package api

import java.time.Month._

import api.Picklers._
import api.ScalaJsApi._
import boopickle.Default._
import boopickle.Pickler
import common.money.Currency
import common.testing.TestObjects._
import common.testing._
import common.time.LocalDateTimes
import models.accounting.Transaction
import models.accounting.config.Config
import models.modification.{EntityModification, EntityType}
import org.junit.runner._
import org.specs2.runner._

import scala.collection.SortedMap
import scala.collection.immutable.Seq

@RunWith(classOf[JUnitRunner])
class PicklersTest extends HookedSpecification {

  "accounting config" in {
    testPickleAndUnpickle[Config](testAccountingConfig)
  }

  "EntityType" in {
    testPickleAndUnpickle[EntityType.any](EntityType.ExchangeRateMeasurementType)
  }

  "EntityModification" in {
    testPickleAndUnpickle[EntityModification](EntityModification.Add(testUserRedacted))
    testPickleAndUnpickle[EntityModification](EntityModification.Add(testTransactionWithId))
    testPickleAndUnpickle[EntityModification](EntityModification.Add(testTransactionGroupWithId))
    testPickleAndUnpickle[EntityModification](EntityModification.Add(testBalanceCheckWithId))
    testPickleAndUnpickle[EntityModification](EntityModification.Add(testExchangeRateMeasurementWithId))
    testPickleAndUnpickle[EntityModification](EntityModification.Update(testUserRedacted))
    testPickleAndUnpickle[EntityModification](EntityModification.Update(testTransactionWithId))
    testPickleAndUnpickle[EntityModification](EntityModification.Update(testTransactionGroupWithId))
    testPickleAndUnpickle[EntityModification](EntityModification.Update(testBalanceCheckWithId))
    testPickleAndUnpickle[EntityModification](EntityModification.Update(testExchangeRateMeasurementWithId))
    testPickleAndUnpickle[EntityModification](EntityModification.Remove[Transaction](123054))
  }

  "GetInitialDataResponse" in {
    testPickleAndUnpickle[GetInitialDataResponse](
      GetInitialDataResponse(
        accountingConfig = testAccountingConfig,
        user = testUserRedacted,
        allUsers = Seq(testUserRedacted),
        i18nMessages = Map("abc" -> "def"),
        ratioReferenceToForeignCurrency =
          Map(Currency.Gbp -> SortedMap(LocalDateTimes.createDateTime(2012, MAY, 2) -> 1.2349291837)),
        nextUpdateToken = testUpdateToken
      ))
  }

  "GetAllEntitiesResponse" in {
    testPickleAndUnpickle[GetAllEntitiesResponse](
      GetAllEntitiesResponse(
        entitiesMap = Map(EntityType.TransactionType -> Seq(testTransactionWithId)),
        nextUpdateToken = testUpdateToken))
  }

  "ModificationsWithToken" in {
    testPickleAndUnpickle[ModificationsWithToken](
      ModificationsWithToken(modifications = Seq(testModification), nextUpdateToken = testUpdateToken))
  }

  private def testPickleAndUnpickle[T: Pickler](value: T) = {
    val bytes = Pickle.intoBytes[T](value)
    val unpickled = Unpickle[T].fromBytes(bytes)
    unpickled mustEqual value
  }
}
