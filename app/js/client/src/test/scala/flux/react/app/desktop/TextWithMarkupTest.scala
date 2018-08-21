package flux.react.app.desktop

import flux.react.app.desktop.TextWithMarkup.{Formatting, Part}
import scala2js.Converters._
import utest._

object TextWithMarkupTest extends TestSuite {

  override def tests = TestSuite {
    "contentString" - {
      val textWithMarkup = TextWithMarkup("a") + italic("bc") + bold("d")
      textWithMarkup.contentString ==> "abcd"
    }
    "sub" - {
      val textWithMarkup = TextWithMarkup("a") + italic("bc") + bold("d") + italic("efg")

      textWithMarkup.sub(0, 0) ==> TextWithMarkup.empty
      textWithMarkup.sub(3, 3) ==> TextWithMarkup.empty
      textWithMarkup.sub(0) ==> textWithMarkup

      textWithMarkup.sub(3) ==> bold("d") + italic("efg")
      textWithMarkup.sub(0, 3) ==> TextWithMarkup("a") + italic("bc")
      textWithMarkup.sub(0, 2) ==> TextWithMarkup("a") + italic("b")
      textWithMarkup.sub(5, 6) ==> italic("f")
      textWithMarkup.sub(5, 7) ==> italic("fg")
    }
    "withFormatting" - {
      val textWithMarkup = TextWithMarkup("abc") + italic("def")

      textWithMarkup.withFormatting(beginOffset = 1, endOffset = 4, _.copy(link = Some("example.com"))) ==>
        TextWithMarkup("a") +
          TextWithMarkup("bc", Formatting(link = Some("example.com"))) +
          TextWithMarkup("d", Formatting(italic = true, link = Some("example.com"))) +
          italic("ef")
    }
    "formattingAtCursor" - {
      val textWithMarkup = TextWithMarkup("a") +
        TextWithMarkup("bc", Formatting(italic = true, link = Some("example.com"))) +
        italic("d") +
        bold("e")

      textWithMarkup.formattingAtCursor(0) ==> Formatting.none
      textWithMarkup.formattingAtCursor(1) ==> Formatting.none
      textWithMarkup.formattingAtCursor(2) ==> Formatting(italic = true, link = Some("example.com"))
      textWithMarkup.formattingAtCursor(3) ==> Formatting(italic = true)
      textWithMarkup.formattingAtCursor(4) ==> Formatting(italic = true)
      textWithMarkup.formattingAtCursor(5) ==> Formatting(bold = true)
    }
    "canonicalized equality check" - {
      TextWithMarkup("a") + TextWithMarkup("b") ==> TextWithMarkup("ab")
      italic("a") + italic("b") ==> italic("ab")
      bold("abc").withFormatting(1, 2, _.copy(bold = true)) ==> bold("abc")
    }
    "toHtml" - {
      // TODO
    }
    "fromHtml" - {
      // TODO
    }
  }

  private def italic(string: String): TextWithMarkup = TextWithMarkup(string, Formatting(italic = true))
  private def bold(string: String): TextWithMarkup = TextWithMarkup(string, Formatting(bold = true))
}
