import play.api.mvc.{AnyContent, Controller, RequestHeader, Request}
import play.api.templates.{Html, Txt}
import play.api.{Logger, Plugin, Application}
import securesocial.controllers.TemplatesPlugin
import securesocial.core.{Identity, SecuredRequest}
import play.api.data.Form
import securesocial.controllers.Registration.RegistrationInfo
import securesocial.controllers.PasswordChange.ChangeInfo

class LoginRegisterViews(application: Application) extends TemplatesPlugin {
  override def getLoginPage[A](implicit request : play.api.mvc.Request[A], form : play.api.data.Form[scala.Tuple2[scala.Predef.String, scala.Predef.String]], msg : scala.Option[scala.Predef.String]) = {
    views.html.LoginRegister.login(form, msg)
  }

  override def getSignUpPage[A](implicit request : play.api.mvc.Request[A], form : play.api.data.Form[securesocial.controllers.Registration.RegistrationInfo], token : scala.Predef.String) : play.api.templates.Html = {
    securesocial.views.html.Registration.signUp(form, token)
  }

  override def getStartSignUpPage[A](implicit request : play.api.mvc.Request[A], form : play.api.data.Form[scala.Predef.String]) : play.api.templates.Html = {
    securesocial.views.html.Registration.startSignUp(form)
  }
  override def getResetPasswordPage[A](implicit request : play.api.mvc.Request[A], form : play.api.data.Form[scala.Tuple2[scala.Predef.String, scala.Predef.String]], token : scala.Predef.String) : play.api.templates.Html = {
    securesocial.views.html.Registration.resetPasswordPage(form, token)
  }
  override def getStartResetPasswordPage[A](implicit request : play.api.mvc.Request[A], form : play.api.data.Form[scala.Predef.String]) : play.api.templates.Html = {
    securesocial.views.html.Registration.startResetPassword(form)
  }
  override def getPasswordChangePage[A](implicit request : securesocial.core.SecuredRequest[A], form : play.api.data.Form[securesocial.controllers.PasswordChange.ChangeInfo]) : play.api.templates.Html = {
    securesocial.views.html.passwordChange(form)
  }
  override def getNotAuthorizedPage[A](implicit request : play.api.mvc.Request[A]) : play.api.templates.Html = {
    securesocial.views.html.notAuthorized()
  }

  def getSignUpEmail(token: String)(implicit request: RequestHeader): (Option[Txt], Option[Html]) = {
    (None, Some(securesocial.views.html.mails.signUpEmail(token)))
  }

  def getAlreadyRegisteredEmail(user: Identity)(implicit request: RequestHeader): (Option[Txt], Option[Html]) = {
    (None, Some(securesocial.views.html.mails.alreadyRegisteredEmail(user)))
  }

  def getWelcomeEmail(user: Identity)(implicit request: RequestHeader): (Option[Txt], Option[Html]) = {
    (None, Some(securesocial.views.html.mails.welcomeEmail(user)))
  }

  def getUnknownEmailNotice()(implicit request: RequestHeader): (Option[Txt], Option[Html]) = {
    (None, Some(securesocial.views.html.mails.unknownEmailNotice(request)))
  }

  def getSendPasswordResetEmail(user: Identity, token: String)(implicit request: RequestHeader): (Option[Txt], Option[Html]) = {
    (None, Some(securesocial.views.html.mails.passwordResetEmail(user, token)))
  }

  def getPasswordChangedNoticeEmail(user: Identity)(implicit request: RequestHeader): (Option[Txt], Option[Html]) = {
    (None, Some(securesocial.views.html.mails.passwordChangedNotice(user)))
  }
}