package controllers

import com.google.inject.Inject
import common.money.Currency
import common.time.{Clock, TimeUtils}
import models.access.JvmEntityAccess
import models.accounting._
import models.accounting.config.{Account, Config, Template}
import models.modification.EntityModification
import models.money.ExchangeRateMeasurement
import models.user.{User, Users}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._

import scala.collection.immutable.Seq

final class ExternalApi @Inject()(implicit override val messagesApi: MessagesApi,
                                  components: ControllerComponents,
                                  clock: Clock,
                                  playConfiguration: play.api.Configuration,
                                  accountingConfig: Config,
                                  entityAccess: JvmEntityAccess)
    extends AbstractController(components)
    with I18nSupport {

  // ********** actions ********** //
  def healthCheck = Action { implicit request =>
    entityAccess.checkConsistentCaches()
    Ok("OK")
  }

  def addTransactionFromTemplate(templateCode: String, applicationSecret: String) = Action {
    implicit request =>
      validateApplicationSecret(applicationSecret)

      implicit val issuer = Users.getOrCreateRobotUser()
      val template = accountingConfig.templateWithCode(templateCode)

      val groupAddition = EntityModification.createAddWithRandomId(TransactionGroup(createdDate = clock.now))
      val transactionAdditions =
        toTransactions(template, transactionGroup = groupAddition.entity, issuer)
          .map(EntityModification.createAddWithRandomId(_))
      entityAccess.persistEntityModifications(
        groupAddition +: transactionAdditions
      )

      Ok("OK")
  }

  def addExchangeRateMeasurement(dateString: String,
                                 foreignCurrencyCode: String,
                                 ratioReferenceToForeignCurrency: Double,
                                 applicationSecret: String) = Action { implicit request =>
    validateApplicationSecret(applicationSecret)

    implicit val user = Users.getOrCreateRobotUser()
    val date = TimeUtils.parseDateString(dateString)
    require(Currency.of(foreignCurrencyCode).isForeign)

    entityAccess.persistEntityModifications(
      EntityModification.createAddWithRandomId(
        ExchangeRateMeasurement(
          date = date,
          foreignCurrencyCode = foreignCurrencyCode,
          ratioReferenceToForeignCurrency = ratioReferenceToForeignCurrency)))
    Ok("OK")
  }

  // ********** private helper methods ********** //
  private def validateApplicationSecret(applicationSecret: String): Unit = {
    val realApplicationSecret: String = playConfiguration.get[String]("play.http.secret.key")
    require(
      applicationSecret == realApplicationSecret,
      s"Invalid application secret. Found '$applicationSecret' but should be '$realApplicationSecret'")
  }

  private def toTransactions(template: Template,
                             transactionGroup: TransactionGroup,
                             issuer: User): Seq[Transaction] = {
    def checkNotEmpty(s: String): String = {
      require(!s.isEmpty)
      s
    }
    val groupPartial = template.toPartial(Account.nullInstance)
    for (partial <- groupPartial.transactions)
      yield
        Transaction(
          transactionGroupId = transactionGroup.id,
          issuerId = issuer.id,
          beneficiaryAccountCode = checkNotEmpty(partial.beneficiary.get.code),
          moneyReservoirCode = checkNotEmpty(partial.moneyReservoir.get.code),
          categoryCode = checkNotEmpty(partial.category.get.code),
          description = checkNotEmpty(partial.description),
          flowInCents = partial.flowInCents,
          detailDescription = partial.detailDescription,
          tags = partial.tags,
          createdDate = clock.now,
          transactionDate = clock.now,
          consumedDate = clock.now
        )
  }
}
