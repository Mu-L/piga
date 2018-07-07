package flux.react.app

import common.I18n
import common.LoggingUtils.logExceptions
import flux.react.ReactVdomUtils.<<
import flux.react.router.{Page, RouterContext}
import flux.react.uielements
import flux.react.uielements.Panel
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import models.access.EntityAccess
import models.accounting.config.{Config, Template}
import models.user.User

import scala.collection.immutable.Seq

private[app] final class DesktopTaskList(implicit user: User,
                                      entityAccess: EntityAccess,
                                      i18n: I18n) {

  private val component = ScalaComponent
    .builder[Props](getClass.getSimpleName)
    .renderP(($, props) =>
      logExceptions {
        implicit val router = props.router
        <.span(
          uielements.PageHeader(router.currentPage),
          Panel(
            title = "Piga Task List"
          ) {
            <.span("Hello world!")
          }
        )
    })
    .build

  // **************** API ****************//
  def apply(router: RouterContext): VdomElement = {
    component(Props(router))
  }

  // **************** Private inner types ****************//
  private case class Props(router: RouterContext)
}
