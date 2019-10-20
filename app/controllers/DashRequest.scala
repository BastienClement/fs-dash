package controllers

import model.User
import play.api.mvc.{Request, WrappedRequest}
import services.Services

class DashRequest[A](
    val optUser: Option[User],
    request: Request[A]
)(implicit val services: Services)
    extends WrappedRequest(request) {

  def user: User = optUser.getOrElse(User.Anonymous)

  def authenticated: Boolean = user.isFromScratch

  def authLink(implicit request: DashRequest[_]): String = services.discordService.loginLink
}

object DashRequest {
  def default[A](request: Request[A])(implicit services: Services): DashRequest[A] =
    new DashRequest[A](None, request)
}
