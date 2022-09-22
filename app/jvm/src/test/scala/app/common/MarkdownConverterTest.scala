package app.common

import app.common.MarkdownConverter.ParsedTask
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameters
import org.junit.runner.RunWith
import org.junit.Test

import scala.collection.JavaConverters._

@RunWith(classOf[TestParameterInjector])
class MarkdownConverterTest {

  @Test
  def markdownToParsedTasks_emptyString(): Unit = {
    assertThat(
      MarkdownConverter.markdownToParsedTasks("").asJava
    ) containsExactly ParsedTask("", 0)
  }

  @Test
  def markdownToParsedTasks_singleLine(): Unit = {
    assertThat(
      MarkdownConverter.markdownToParsedTasks("abc").asJava
    ) containsExactly ParsedTask("abc", 0)
  }

  @Test
  def markdownToParsedTasks_multiLine(): Unit = {
    assertThat(
      MarkdownConverter.markdownToParsedTasks("abc\ndef").asJava
    ) containsExactly ParsedTask("abc\ndef", 0)
  }

  @Test
  def markdownToParsedTasks_flatList(): Unit = {
    assertThat(
      MarkdownConverter
        .markdownToParsedTasks(
          "" +
            "- abc\n" +
            "  def\n" +
            "- ghi"
        )
        .asJava
    ) containsExactly (ParsedTask("abc\ndef", 0), ParsedTask("ghi", 0))
  }

  @Test
  def markdownToParsedTasks_indentedList(): Unit = {
    assertThat(
      MarkdownConverter
        .markdownToParsedTasks(
          "" +
            "- abc\n" +
            "  - def\n" +
            "  - ghi\n\n"
        )
        .asJava
    ) containsExactly (ParsedTask("abc", 0), ParsedTask("def", 1), ParsedTask("ghi", 1))
  }

  @Test
  def markdownToParsedTasks_bold(): Unit = {
    assertThat(
      MarkdownConverter.markdownToParsedTasks("abc **def**").asJava
    ) containsExactly ParsedTask("abc <b>def</b>", 0)
  }

  @Test
  def markdownToParsedTasks_italic(): Unit = {
    assertThat(
      MarkdownConverter.markdownToParsedTasks("abc *def*").asJava
    ) containsExactly ParsedTask("abc <i>def</i>", 0)
  }

  @Test
  def markdownToParsedTasks_code(): Unit = {
    assertThat(
      MarkdownConverter.markdownToParsedTasks("abc `def`").asJava
    ) containsExactly ParsedTask("abc <code>def</code>", 0)
  }

  @Test
  def markdownToParsedTasks_strikethrough(): Unit = {
    assertThat(
      MarkdownConverter.markdownToParsedTasks("abc ~def~").asJava
    ) containsExactly ParsedTask("abc <s>def</s>", 0)
  }

  @Test
  def markdownToParsedTasks_href(): Unit = {
    assertThat(
      MarkdownConverter.markdownToParsedTasks("- [abc](http://example.com)\n").asJava
    ) containsExactly ParsedTask("""<a href="http://example.com">abc</a>""", 0)
  }

  @Test
  def markdownToParsedTasks_href_multiple(): Unit = {
    assertThat(
      MarkdownConverter.markdownToParsedTasks("- [abc](c.com) -- ) ( ] ) [def](f.com)\n").asJava
    ) containsExactly ParsedTask("""<a href="c.com">abc</a> -- ) ( ] ) <a href="f.com">def</a>""", 0)
  }

  @Test
  def markdownToParsedTasks_symbolsCombined(): Unit = {
    assertThat(
      MarkdownConverter
        .markdownToParsedTasks("- [abc](ex.com) _def_ **ghi** `klm` `nop`\n")
        .asJava
    ) containsExactly ParsedTask(
      """<a href="ex.com">abc</a> <i>def</i> <b>ghi</b> <code>klm</code> <code>nop</code>""",
      0,
    )
  }
}
