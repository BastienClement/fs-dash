import db.driver.PostgresProfile

package object db {
  val api: PostgresProfile.api.type = PostgresProfile.api
}
