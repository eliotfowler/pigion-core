import java.lang.reflect.Constructor

import actors.ExpirationActor
import models.User
import play.api.mvc.{WithFilters, EssentialFilter, EssentialAction, RequestHeader}
import securesocial.core.RuntimeEnvironment
import services.PigionUserService
import scala.concurrent.duration.DurationInt
import play.api.Application
import play.api.GlobalSettings
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.concurrent.Akka
import akka.actor.Props
import play.api.Play.current

class CorsFilter extends EssentialFilter {
  def apply(next: EssentialAction) = new EssentialAction {
    def apply(requestHeader: RequestHeader) = {
      next(requestHeader).map { result =>
        result.withHeaders("Access-Control-Allow-Origin" -> "*",
          "Access-Control-Expose-Headers" -> "WWW-Authenticate, Server-Authorization",
          "Access-Control-Allow-Methods" -> "POST, GET, OPTIONS, PUT, DELETE",
          "Access-Control-Allow-Headers" -> "x-requested-with,content-type,Cache-Control,Pragma,Date,X-Auth-Token")
      }
    }
  }
}

object Global extends WithFilters(new CorsFilter) with GlobalSettings {

  object MyRuntimeEnvironment extends RuntimeEnvironment.Default[User] {
    override lazy val userService: PigionUserService = new PigionUserService()
  }

  /**
   * An implementation that checks if the controller expects a RuntimeEnvironment and
   * passes the instance to it if required.
   *
   * This can be replaced by any DI framework to inject it differently.
   *
   * @param controllerClass
   * @tparam A
   * @return
   */
  override def getControllerInstance[A](controllerClass: Class[A]): A = {
    val instance  = controllerClass.getConstructors.find { c =>
      val params = c.getParameterTypes
      params.length == 1 && params(0) == classOf[RuntimeEnvironment[User]]
    }.map {
      _.asInstanceOf[Constructor[A]].newInstance(MyRuntimeEnvironment)
    }
    instance.getOrElse(super.getControllerInstance(controllerClass))
  }

  override def onStart(app: Application) {

    play.api.Play.mode(app) match {
//      case play.api.Mode.Test => // do not schedule anything for Test
      case _ => janitorDaemon(app)
    }
  }

  def janitorDaemon(app: Application) = {
    val janitor = Akka.system.actorOf(Props[ExpirationActor], name = "Janitor")
    Logger.info("Scheduling the janitor daemon")
//    val reminderActor = Akka.system(app).actorOf(Props(new ReminderActor()))
    Akka.system(app).scheduler.schedule(0 seconds, 1 minute, janitor, "janitorDaemon")
  }

}