package app.common.testing

import hydro.common.OrderToken
import app.common.testing.TestObjects._
import app.models.document.Document.DetachedCursor
import app.models.document.Document.DetachedSelection
import app.models.document.Document.IndexedCursor
import app.models.document.Document.IndexedSelection
import app.models.document.Document
import app.models.document.Task
import app.models.document.TextWithMarkup
import app.models.document.TextWithMarkup.Formatting
import hydro.common.time.LocalDateTime

import scala.collection.immutable.Seq

object JsTestObjects {

  val taskA = newTask("Task A", orderToken = orderTokenA, indentation = 2)
  val taskB = newTask("Task B", orderToken = orderTokenB, indentation = 3)
  val taskC = newTask("Task C", orderToken = orderTokenC)
  val taskD = newTask("Task D", orderToken = orderTokenD)
  val taskE = newTask("Task E", orderToken = orderTokenE)
  def testTask = taskA

  def testSelection = IndexedSelection.singleton(IndexedCursor(2, 123))
  def testDetachedCursor = DetachedCursor(taskA, 12938)
  def testDetachedSelection = DetachedSelection(testDetachedCursor, testDetachedCursor)

  def textWithMarkup(string: String, formatting: Formatting = Formatting.none): TextWithMarkup = {
    TextWithMarkup.create(string, formatting, alreadySanitized = true)
  }

  def newTask(
      contentString: String = null,
      content: TextWithMarkup = null,
      orderToken: OrderToken = orderTokenB,
      indentation: Int = 2,
      collapsed: Boolean = false,
      checked: Boolean = false,
      delayedUntil: Option[LocalDateTime] = Some(testDate),
      tags: Seq[String] = Seq("test-tag"),
  ): Task = {
    require(content != null || contentString != null)
    implicit val document = newDocument()
    implicit val user = TestObjects.testUser

    Task.withRandomId(
      content = Option(content) getOrElse TextWithMarkup
        .create(contentString, formatting = Formatting.none, alreadySanitized = true),
      orderToken = orderToken,
      indentation = indentation,
      collapsed = collapsed,
      checked = checked,
      delayedUntil = delayedUntil,
      tags = tags,
    )
  }

  def newDocument(tasks: Task*): Document =
    new Document(id = 12873, name = "test document", tasks = Seq(tasks: _*))
}
