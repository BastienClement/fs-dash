package controllers

import javax.inject._
import model.Snowflake

@Singleton
class HomeController extends DashController {
  def index = DashAction { implicit req =>
    Redirect(routes.CharterController.index())
  }

  def snowflake = Action { req =>
    Ok(List.fill(req.getQueryString("n").map(Integer.parseInt).getOrElse(1))(Snowflake.next).mkString("\n"))
  }
}
