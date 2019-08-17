package controllers

import model.User
import play.api.mvc.{Request, WrappedRequest}
import services.Services

class DashRequest[A](
    val optUser: Option[User],
    request: Request[A]
)(implicit val services: Services)
    extends WrappedRequest(request) {

  def authenticated: Boolean = optUser.isDefined

  def user: User = optUser.get

  def authLink(implicit request: DashRequest[_]): String = services.discordService.loginLink
}

object DashRequest {
  def default[A](request: Request[A])(implicit services: Services): DashRequest[A] =
    new DashRequest[A](None, request)
}
