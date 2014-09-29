package controllers

import models.User
import play.api.mvc._
import securesocial.core._

class Application(override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  def index = SecuredAction {
    Ok(views.html.index())
  }

  def landing = Action {
    Ok(views.html.landing())
  }

  def preflight(all: String) = Action {
    Ok.withHeaders("Access-Control-Allow-Origin" -> "*",
      "Allow" -> "*",
      "Access-Control-Allow-Methods" -> "POST, GET, PUT, DELETE, OPTIONS",
      "Access-Control-Allow-Headers" -> "Origin, X-Requested-With, Content-Type, Accept, Referrer, User-Agent");
  }
}