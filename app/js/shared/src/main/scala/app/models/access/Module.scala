package app.models.access

import app.api.ScalaJsApi.GetInitialDataResponse
import app.api.ScalaJsApiClient
import app.models.modification.EntityType.DocumentEntityType
import app.models.modification.EntityType.TaskEntityType
import app.models.modification.EntityType.UserType
import app.models.user.User
import hydro.models.access.EntityModificationPushClientFactory
import hydro.models.access.HybridRemoteDatabaseProxy
import hydro.models.access.JsEntityAccess
import hydro.models.access.JsEntityAccessImpl
import hydro.models.access.LocalDatabaseImpl
import hydro.models.access.LocalDatabaseImpl.SecondaryIndexFunction

import scala.collection.immutable.Seq

final class Module(implicit user: User,
                   scalaJsApiClient: ScalaJsApiClient,
                   getInitialDataResponse: GetInitialDataResponse) {

  implicit val secondaryIndexFunction = Module.secondaryIndexFunction
  implicit val entityModificationPushClientFactory: EntityModificationPushClientFactory =
    new EntityModificationPushClientFactory()

  implicit val entityAccess: JsEntityAccess = {
    val webWorkerModule = new hydro.models.access.webworker.Module()
    implicit val localDatabaseWebWorkerApiStub = webWorkerModule.localDatabaseWebWorkerApiStub
    val localDatabaseFuture = LocalDatabaseImpl.create()
    implicit val remoteDatabaseProxy = HybridRemoteDatabaseProxy.create(localDatabaseFuture)
    val entityAccess = new JsEntityAccessImpl()

    entityAccess.startCheckingForModifiedEntityUpdates()

    entityAccess
  }
}
object Module {
  val secondaryIndexFunction: SecondaryIndexFunction = SecondaryIndexFunction({
    case UserType           => Seq()
    case DocumentEntityType => Seq()
    case TaskEntityType     => Seq(ModelField.TaskEntity.documentId)
  })
}
