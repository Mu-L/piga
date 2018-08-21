package common

import japgolly.scalajs.react.CallbackTo
import org.scalajs.dom
import org.scalajs.dom.console

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.immutable.Seq

object DomNodeUtils {

  def asTextNode(node: dom.raw.Node): Option[dom.raw.Text] = {
    if (node.nodeType == dom.raw.Node.TEXT_NODE) {
      Some(node.asInstanceOf[dom.raw.Text])
    } else {
      None
    }
  }

  def nodeIsList(node: dom.raw.Node): Boolean = parseNode(node) match {
    case ParsedNode.Ul(_) | ParsedNode.Ol(_) => true
    case _                                   => false
  }
  def nodeIsLi(node: dom.raw.Node): Boolean = parseNode(node).isInstanceOf[ParsedNode.Li]

  def children(node: dom.raw.Node): Seq[dom.raw.Node] = {
    for (i <- 0 until node.childNodes.length) yield node.childNodes.item(i)
  }

  def walkDepthFirstPreOrder(node: dom.raw.Node): Iterable[NodeWithOffset] = {
    var offsetSoFar = 0
    def internal(node: dom.raw.Node): Iterable[NodeWithOffset] = {
      val nodeLength = asTextNode(node).map(_.length) getOrElse 0
      val nodeWithOffset = NodeWithOffset(node, offsetSoFar, offsetAtEnd = offsetSoFar + nodeLength)
      offsetSoFar += nodeLength

      val iterables = for (i <- 0 until node.childNodes.length) yield {
        internal(node.childNodes.item(i))
      }
      nodeWithOffset +: iterables.flatten
    }
    internal(node)
  }

  case class NodeWithOffset(node: dom.raw.Node, offsetSoFar: Int, offsetAtEnd: Int)

  def parseNode(node: dom.raw.Node): ParsedNode = {
    if (node.nodeType == dom.raw.Node.TEXT_NODE) {
      ParsedNode.Text(node.asInstanceOf[dom.raw.Text].wholeText)
    } else if (node.nodeType == dom.raw.Node.ELEMENT_NODE) {
      val element = node.asInstanceOf[dom.raw.Element]
      element.tagName match {
        case "LI"  => ParsedNode.Li(element)
        case "UL"  => ParsedNode.Ul(element)
        case "OL"  => ParsedNode.Ol(element)
        case "BR"  => ParsedNode.Br(element)
        case "DIV" => ParsedNode.Div(element)
        case "P"   => ParsedNode.P(element)
        case _     => ParsedNode.Other(node)
      }
    } else {
      ParsedNode.Other(node)
    }
  }

  sealed trait ParsedNode
  object ParsedNode {
    case class Text(string: String) extends ParsedNode
    case class Li(element: dom.raw.Element) extends ParsedNode
    case class Ul(element: dom.raw.Element) extends ParsedNode
    case class Ol(element: dom.raw.Element) extends ParsedNode
    case class Br(element: dom.raw.Element) extends ParsedNode
    case class Div(element: dom.raw.Element) extends ParsedNode
    case class P(element: dom.raw.Element) extends ParsedNode
    case class Other(node: dom.raw.Node) extends ParsedNode
  }
}
