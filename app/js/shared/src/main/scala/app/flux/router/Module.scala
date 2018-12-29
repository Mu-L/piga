package app.flux.router

import app.common.I18n
import app.flux.stores.document.AllDocumentsStore
import hydro.flux.action.Dispatcher
import japgolly.scalajs.react.extra.router._
import app.models.access.EntityAccess
import hydro.flux.router.Page

final class Module(implicit reactAppModule: app.flux.react.app.Module,
                   dispatcher: Dispatcher,
                   i18n: I18n,
                   allDocumentsStore: AllDocumentsStore,
                   entityAccess: EntityAccess,
) {

  implicit lazy val router: Router[Page] = (new RouterFactory).createRouter()
}
