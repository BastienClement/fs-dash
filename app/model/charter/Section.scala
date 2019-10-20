package model.charter

import model.Snowflake
import services.MarkdownRenderer

case class Section(id: Snowflake, number: Int, title: String, body: String) {
  def html: String = MarkdownRenderer.render(body)
}
