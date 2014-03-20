import actors.ExpirationActor
import scala.concurrent.duration.DurationInt
import play.api.Application
import play.api.GlobalSettings
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.concurrent.Akka
import akka.actor.Props
import play.api.Play.current

object Global extends GlobalSettings {

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