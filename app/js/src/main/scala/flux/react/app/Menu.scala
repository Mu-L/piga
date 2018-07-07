package flux.react.app

import common.I18n
import common.LoggingUtils.{LogExceptionsCallback, logExceptions}
import common.money.ExchangeRateManager
import common.time.Clock
import flux.react.ReactVdomUtils.{<<, ^^}
import flux.react.router.{Page, RouterContext}
import flux.react.uielements
import flux.stores.entries.factories.AllEntriesStoreFactory
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import jsfacades.Mousetrap
import models.access.EntityAccess
import models.accounting.config.{Config, Template}
import models.user.User

import scala.collection.immutable.Seq

private[app] final class Menu(implicit entriesStoreFactory: AllEntriesStoreFactory,
                              entityAccess: EntityAccess,
                              user: User,
                              clock: Clock,
                              accountingConfig: Config,
                              exchangeRateManager: ExchangeRateManager,
                              i18n: I18n) {

  private val component = ScalaComponent
    .builder[Props](getClass.getSimpleName)
    .renderBackend[Backend]
    .componentWillMount(scope => scope.backend.configureKeyboardShortcuts(scope.props.router))
    .componentDidMount(scope =>
      LogExceptionsCallback {
        scope.props.router.currentPage match {
          case page: Page.Search => {
            scope.backend.queryInputRef().setValue(page.query)
          }
          case _ =>
        }
    })
    .componentWillReceiveProps(scope => scope.backend.configureKeyboardShortcuts(scope.nextProps.router))
    .build

  // **************** API ****************//
  def apply(router: RouterContext): VdomElement = {
    component(Props(router))
  }

  // **************** Private inner types ****************//
  private type State = Unit
  private class Backend(val $ : BackendScope[Props, State]) {
    val queryInputRef = uielements.input.TextInput.ref()

    def render(props: Props, state: State) = logExceptions {
      implicit val router = props.router
      def menuItem(label: String, page: Page, iconClass: String = null): VdomElement =
        router
          .anchorWithHrefTo(page)(
            ^^.ifThen(page.getClass == props.router.currentPage.getClass) { ^.className := "active" },
            ^.key := label,
            <.i(^.className := Option(iconClass) getOrElse page.iconClass),
            " ",
            <.span(^.dangerouslySetInnerHtml := label)
          )

      <.ul(
        ^.className := "nav",
        ^.id := "side-menu",
        <.li(
          ^.className := "sidebar-search",
          <.form(
            <.div(
              ^.className := "input-group custom-search-form",
              uielements.input
                .TextInput(
                  ref = queryInputRef,
                  name = "query",
                  placeholder = i18n("app.search"),
                  classes = Seq("form-control")),
              <.span(
                ^.className := "input-group-btn",
                <.button(
                  ^.className := "btn btn-default",
                  ^.tpe := "submit",
                  ^.onClick ==> { (e: ReactEventFromInput) =>
                    LogExceptionsCallback {
                      e.preventDefault()

                      queryInputRef().value match {
                        case Some(query) => props.router.setPage(Page.Search(query))
                        case None        =>
                      }
                    }
                  },
                  <.i(^.className := "fa fa-search")
                )
              )
            ))
        ),
        <.li(
          menuItem(i18n("app.everything.html"), Page.Everything),
          menuItem(i18n("app.cash-flow.html"), Page.CashFlow),
          menuItem(i18n("app.liquidation.html"), Page.Liquidation),
          menuItem(i18n("app.endowments.html"), Page.Endowments),
          menuItem(i18n("app.summary.html"), Page.Summary)
        ),
        <.li(
          menuItem(i18n("app.templates.html"), Page.TemplateList),
          menuItem(i18n("app.new-entry.html"), Page.NewTransactionGroup())
        ),
        <<.ifThen(newEntryTemplates.nonEmpty) {
          <.li({
            for (template <- newEntryTemplates)
              yield menuItem(template.name, Page.NewFromTemplate(template), iconClass = template.iconClass)
          }.toVdomArray)
        }
      )
    }

    def configureKeyboardShortcuts(implicit router: RouterContext): Callback = LogExceptionsCallback {
      def bind(shortcut: String, runnable: () => Unit): Unit = {
        Mousetrap.bindGlobal(shortcut, e => {
          e.preventDefault()
          runnable()
        })
      }
      def bindToPage(shortcut: String, page: Page): Unit =
        bind(shortcut, () => {
          router.setPage(page)
        })

      bindToPage("shift+alt+e", Page.Everything)
      bindToPage("shift+alt+a", Page.Everything)
      bindToPage("shift+alt+c", Page.CashFlow)
      bindToPage("shift+alt+l", Page.Liquidation)
      bindToPage("shift+alt+v", Page.Liquidation)
      bindToPage("shift+alt+d", Page.Endowments)
      bindToPage("shift+alt+s", Page.Summary)
      bindToPage("shift+alt+t", Page.TemplateList)
      bindToPage("shift+alt+j", Page.TemplateList)
      bindToPage("shift+alt+n", Page.NewTransactionGroup())

      bind("shift+alt+f", () => queryInputRef().focus())
    }

    private def newEntryTemplates(implicit router: RouterContext): Seq[Template] = {
      def templatesForPlacement(placement: Template.Placement): Seq[Template] =
        accountingConfig.templatesToShowFor(placement, user)

      router.currentPage match {
        case Page.Everything            => templatesForPlacement(Template.Placement.EverythingView)
        case Page.CashFlow              => templatesForPlacement(Template.Placement.CashFlowView)
        case Page.Liquidation           => templatesForPlacement(Template.Placement.LiquidationView)
        case Page.Endowments            => templatesForPlacement(Template.Placement.EndowmentsView)
        case Page.Summary               => templatesForPlacement(Template.Placement.SummaryView)
        case _: Page.Search             => templatesForPlacement(Template.Placement.SearchView)
        case page: Page.NewFromTemplate => Seq(accountingConfig.templateWithCode(page.templateCode))
        case _                          => Seq()
      }
    }
  }

  private case class Props(router: RouterContext)
}
