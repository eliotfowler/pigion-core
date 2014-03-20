package actors

import akka.actor.Actor
import play.api.db.DB
import anorm._
import java.sql.Timestamp
import org.joda.time.DateTime
import play.api.Play.current
import models.Destination

class ExpirationActor extends Actor{

  def receive = {

    case _ => {
      expireOldDestinations()
      Destination.removeExpiredFromS3()
    }

  }
  // Set expired on all expired destinations
  def expireOldDestinations() {
    DB.withConnection { implicit c =>
      SQL("UPDATE destination SET isExpired = true WHERE expirationTime < {now}").on(
        'now -> new Timestamp(DateTime.now().getMillis())
      ).executeUpdate()
    }
  }
}
