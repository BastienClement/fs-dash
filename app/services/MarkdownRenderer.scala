package services

import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

import scala.jdk.CollectionConverters._

object MarkdownRenderer {
  private val extensions = List(TablesExtension.create()).asJava

  private val parser: Parser =
    Parser.builder().extensions(extensions).build()

  private val htmlRenderer: HtmlRenderer =
    HtmlRenderer.builder().extensions(extensions).escapeHtml(false).build()

  def render(text: String): String = htmlRenderer.render(parser.parse(text))
}
