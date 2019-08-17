package model

import java.time.Instant

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

case class Session(
    id: Snowflake,
    owner: Snowflake,
    accessToken: String,
    scope: String,
    tokenType: String,
    expiresAt: Instant,
    lastChecked: Instant,
    refreshToken: String
)

object Session {
  implicit def Reads: Reads[Session] =
    ((JsPath \ "access_token").read[String] and
      (JsPath \ "scope").read[String] and
      (JsPath \ "token_type").read[String] and
      (JsPath \ "expires_in").read[Int].map(sec => Instant.now.plusSeconds(sec)) and
      (JsPath \ "refresh_token").read[String])
      .apply(Session(Snowflake.next, Snowflake.dummy, _, _, _, _, Instant.now, _))
}
