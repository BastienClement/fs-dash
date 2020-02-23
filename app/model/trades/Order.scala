package model.trades

import model.Snowflake
import play.twirl.api.Html

case class Order(
    id: Snowflake,
    session: Snowflake,
    owner: Snowflake,
    account: Snowflake,
    kind: String,
    quantity: Int,
    closed: Boolean,
    closeQuantity: Option[Int],
    ack: Option[Boolean],
    ackBy: Option[Snowflake],
    archived: Boolean
) {
  def statusText: Html = (kind, closed, ack) match {
    case (_, _, Some(false)) => Html("<span class='class-11'>Annulé</span>")
    case ("ask", false, _)   => Html("<span class='class-4'>Vente</span>")
    case ("ask", true, _)    => Html("<span class='class-3'>Vendu</span>")
    case ("bid", false, _)   => Html("<span class='class-4'>Achat</span>")
    case ("bid", true, _)    => Html("<span class='class-3'>Acheté</span>")
  }

  def guildStatusText: Html =
    if (kind == "ask") Html("<span class='class-11'>Recevoir</span>")
    else Html("<span class='class-3'>Envoyer</span>")

  def detailsText: Html = (kind, closed, ack) match {
    case ("ask", true, None) => Html("<span class='class-11'>À envoyer à un officier ou \"Fstrade\".</span>")
    case ("bid", true, None) => Html("<span class='class-11'>En attente d'envoi par la banque de guilde.</span>")
    case _                   => Html("")
  }
}
