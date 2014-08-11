package controllers

import play.api._
import play.api.mvc._
import securesocial.core._

object Application extends Controller with SecureSocial {

  def index = SecuredAction {
    Ok(views.html.index())
  }

  def landing = Action {
    Ok(views.html.landing())
  }
}