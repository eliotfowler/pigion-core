package controllers

import play.api._
import play.api.mvc._
import securesocial.core._

object Application extends Controller with SecureSocial {

  def index = SecuredAction {
    Ok(views.html.index())
  }

  def signupBetaClosed = Action {
    Ok(views.html.signupBetaClosed())
  }

  def sendBetaInvite(email: String) = Action {

  }

}