package model.bounties

import model.Snowflake
import services.MarkdownRenderer

case class Bounty(id: Snowflake, title: String, body: String) {
  def html: String = MarkdownRenderer.render(body)
}
