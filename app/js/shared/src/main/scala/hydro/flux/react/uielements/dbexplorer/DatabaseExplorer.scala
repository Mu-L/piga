package hydro.flux.react.uielements.dbexplorer

import hydro.common.I18n
import hydro.flux.react.uielements.Bootstrap
import hydro.flux.react.uielements.PageHeader
import hydro.flux.react.HydroReactComponent
import hydro.flux.router.RouterContext
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^
import japgolly.scalajs.react.vdom.html_<^._

import scala.collection.immutable.Seq

final class DatabaseExplorer(implicit i18n: I18n, pageHeader: PageHeader)
    extends HydroReactComponent.Stateless {

  // **************** API ****************//
  def apply(router: RouterContext): VdomElement = {
    component(Props(router))
  }

  // **************** Implementation of HydroReactComponent methods ****************//
  override protected val statelessConfig = StatelessComponentConfig(backendConstructor = new Backend(_))

  // **************** Implementation of HydroReactComponent types ****************//
  protected case class Props(router: RouterContext)

  protected final class Backend(val $ : BackendScope[Props, State]) extends BackendBase($) {
    override def render(props: Props, state: Unit): VdomElement = {
      implicit val router = props.router

      <.span(
        pageHeader(router.currentPage),
        Bootstrap.Row("Hello world")
      )
    }
  }
}
