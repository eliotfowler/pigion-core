package controllers

import models.User
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc._
import securesocial.controllers.BaseRegistration._
import securesocial.controllers.RegistrationInfo
import securesocial.core._
import securesocial.core.authenticator.CookieAuthenticator
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.providers.utils.PasswordValidator
import securesocial.core.services.SaveMode

import scala.concurrent.{Await, ExecutionContext, Future}

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

  val providerId = UsernamePasswordProvider.UsernamePassword
  val UserName = "userName"
  val FirstName = "firstName"
  val LastName = "lastName"

  val formWithUsername = Form[RegistrationInfo](
    mapping(
      UserName -> nonEmptyText.verifying(Messages(UserNameAlreadyTaken), userName => {
        // todo: see if there's a way to avoid waiting here :-\
        import scala.concurrent.duration._
        Await.result(env.userService.find(providerId, userName), 20.seconds).isEmpty
      }),
      FirstName -> nonEmptyText,
      LastName -> nonEmptyText,
      Password -> nonEmptyText
    )
      // binding
      ((userName, firstName, lastName, password) => RegistrationInfo(Some(userName), firstName, lastName, password))
      // unbinding
      (info => Some(info.userName.getOrElse(""), info.firstName, info.lastName, ""))
  )

  def handleJsonSignup = Action.async { implicit request =>
    import ExecutionContext.Implicits.global
    formWithUsername.bindFromRequest.fold(
      errors => {

        Future.successful(BadRequest(Json.toJson(
          errors.errors.map { e =>
            Map("key" -> e.key, "message" -> e.message)
          }
        )))
      },
      info => {
        val id = info.userName.get
        val newUser = BasicProfile(
          providerId,
          id,
          Some(info.firstName),
          Some(info.lastName),
          Some("%s %s".format(info.firstName, info.lastName)),
          info.userName,
          None,
          AuthenticationMethod.UserPassword,
          passwordInfo = Some(env.currentHasher.hash(info.password))
        )

        val withAvatar = env.avatarService.map {
          _.urlFor(info.userName.getOrElse("")).map { url =>
            if ( url != newUser.avatarUrl) newUser.copy(avatarUrl = url) else newUser
          }
        }.getOrElse(Future.successful(newUser))

        import securesocial.core.utils._
        val result = for (
          toSave <- withAvatar;
          saved <- env.userService.save(toSave, SaveMode.SignUp)
        ) yield {
          if (UsernamePasswordProvider.sendWelcomeEmail)
            env.mailer.sendWelcomeEmail(newUser)
          val eventSession = Events.fire(new SignUpEvent(saved)).getOrElse(session)
          if (UsernamePasswordProvider.signupSkipLogin) {
            env.authenticatorService.find(CookieAuthenticator.Id).map {
              _.fromUser(saved).flatMap { authenticator =>
                Ok.startingAuthenticator(authenticator)
              }
            } getOrElse {
              Future.successful(InternalServerError(s"[securesocial] There isn't CookieAuthenticator registered in the RuntimeEnvironment"))
            }
          } else {
            Future.successful(Ok)
          }
        }
        result.flatMap(f => f)
      })
  }
}