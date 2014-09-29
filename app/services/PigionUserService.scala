package services

import _root_.java.sql.{Date, Timestamp}
import anorm.SqlParser._
import anorm._
import play.api.db.DB
import securesocial.core._
import securesocial.core.PasswordInfo
import anorm.~
import securesocial.core.OAuth2Info
import securesocial.core.OAuth1Info
import play.api.Application
import securesocial.core.providers.Token
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormatter, DateTimeFormat}
import play.api.Play.current
import models.User


class PigionUserService extends UserService[User] {

  override def findByEmailAndProvider(email: String, providerId: String): Option[Identity] = User.findByEmailAndProvider(email, providerId)

  override def deleteToken(uuid: String): Unit = User.deleteToken(uuid)

  override def findToken(token: String): Option[Token] = User.findToken(token)

  override def save(token: Token): Unit = User.save(token)

  override def save(user: Identity): Identity = User.save(user)

  override def find(id: IdentityId): Option[User] = User.find(id)

  def find(seqId: Long): Option[User] = User.find(seqId)

  override def deleteExpiredTokens(): Unit = User.deleteExpiredTokens()
}