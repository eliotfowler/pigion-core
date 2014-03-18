package controllers

import _root_.java.util.UUID
import play.api._
import play.api.mvc._
import securesocial.core._
import securesocial.core.providers._
import securesocial.core.providers.utils.Mailer
import org.joda.time.DateTime

object Application extends Controller with SecureSocial {
  val betaInviterKey = System.getenv("BETA_INVITER_KEY")

  def index = SecuredAction {
    Ok(views.html.index())
  }

  def signupBetaClosed() = Action {
    Ok(views.html.signupBetaClosed())
  }

  def sendBetaInvite(email: String, key: String)= Action { implicit request =>
    if(key equals betaInviterKey) {
      val token = createToken(email, isSignUp = true)
      Mailer.sendSignUpEmail(email, token._1)
      Ok
    } else {
      BadRequest
    }
  }

  private def createToken(email: String, isSignUp: Boolean): (String, Token) = {
    val uuid = UUID.randomUUID().toString
    val now = DateTime.now

    val token = Token(
      uuid, email,
      now,
      now.plusMinutes(10080),
      isSignUp = isSignUp
    )
    UserService.save(token)
    (uuid, token)
  }

}