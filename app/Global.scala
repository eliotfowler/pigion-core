import java.lang.reflect.Constructor

import actors.ExpirationActor
import models.User
import play.api.mvc._
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
import scala.collection.JavaConverters._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.Play.current
import play.api.mvc._

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

class IPFilter extends EssentialFilter {
  def apply(nextFilter: EssentialAction) = new EssentialAction {
    def apply(requestHeader: RequestHeader) = {
      if(requestHeader.path contains "signupRemote") {
        // read the IPs as a Scala Seq (converting from the Java list)
        val ips: Seq[String] = current.configuration.getStringList("pigion.signup-ipwhitelist")
          .map(_.asScala).getOrElse(Seq.empty)

        // Check we've got an allowed IP, otherwise ignore the
        // request body and immediately return a forbidden.
        if (ips.contains(requestHeader.remoteAddress)) nextFilter(requestHeader)
        else Iteratee.ignore[Array[Byte]]
          .map(_ => Results.Forbidden(s"Bad IP! ${requestHeader.remoteAddress}"))
      } else {
        nextFilter(requestHeader)
      }

    }
  }
}

object Global extends WithFilters(new CorsFilter, new IPFilter) with GlobalSettings {

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