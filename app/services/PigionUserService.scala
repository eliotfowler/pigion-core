package services

import _root_.java.sql.{Timestamp, Date}
import anorm.SqlParser._
import anorm._
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.db.DB
import securesocial.core._
import securesocial.core.PasswordInfo
import anorm.~
import securesocial.core.providers.MailToken
import org.joda.time.DateTime
import play.api.Play.current
import models.User
import securesocial.core.services.{SaveMode, UserService}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


class PigionUserService extends UserService[User] {

  implicit def tuple2OAuth1Info(tuple: (Option[String], Option[String])): Option[OAuth1Info] =
    tuple match {
      case (Some(token), Some(secret)) => Some(OAuth1Info(token, secret))
      case _ => None
    }

  implicit def tuple2OAuth2Info(tuple: (Option[String], Option[String], Option[Int], Option[String])) =
    tuple match {
      case (Some(token), tokenType, expiresIn, refreshToken) =>
        Some(OAuth2Info(token, tokenType, expiresIn, refreshToken))
      case _ => None
    }

  implicit def tuple2PasswordInfo(tuple: (Option[String], Option[String], Option[String])): Option[PasswordInfo] =
    tuple match {
      case (Some(hasher), Some(password), salt) =>
        Some(PasswordInfo(hasher, password, salt))
      case _ => None
    }

  val user = {
    get[Long]("seqId") ~
      get[String]("userId") ~
      get[String]("providerId") ~
      get[Option[String]]("firstName") ~
      get[Option[String]]("lastName") ~
      get[Option[String]]("fullName") ~
      get[Option[String]]("email") ~
      get[Option[String]]("avatarUrl") ~
      get[String]("authenticationMethod") ~
      get[Option[String]]("oAuth1Token") ~
      get[Option[String]]("oAuth1Secret") ~
      get[Option[String]]("oAuth2AccessToken") ~
      get[Option[String]]("oAuth2TokenType") ~
      get[Option[Int]]("oAuth2ExpiresIn") ~
      get[Option[String]]("oAuth2RefreshToken") ~
      get[Option[String]]("passwordHasher") ~
      get[Option[String]]("password") ~
      get[Option[String]]("passwordSalt") map {
      case seqId ~ userId ~ providerId ~ firstName ~ lastName ~ fullName ~ email ~ avatarUrl ~
        authenticationMethod ~ oAuth1Token ~ oAuth1Secret ~ oAuth2AccessToken ~ oAuth2TokenType ~
        oAuth2ExpiresIn ~ oAuth2RefreshToken ~ passwordHasher ~ password ~ passwordSalt =>
        User(seqId, BasicProfile(providerId, userId, firstName, lastName, fullName, email, avatarUrl, AuthenticationMethod(authenticationMethod),
          (oAuth1Token, oAuth1Secret), (oAuth2AccessToken, oAuth2TokenType, oAuth2ExpiresIn, oAuth2RefreshToken),
          (passwordHasher, password, passwordSalt)))
    }
  }


  val mailToken: RowParser[MailToken] = {
    get[String]("uuid") ~
      get[String]("email") ~
      get[DateTime]("creationTime") ~
      get[DateTime]("expirationTime") ~
      get[Boolean]("isSignUp") map {
      case uuid ~ email ~ creationTime ~ expirationTime ~ isSignUp =>
        MailToken(uuid, email, creationTime, expirationTime, isSignUp)
    }
  }

  val dateFormatGeneration: DateTimeFormatter = DateTimeFormat.forPattern("yyyyMMddHHmmssSS");

  implicit def rowToDateTime: Column[DateTime] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case ts: Timestamp => Right(new DateTime(ts.getTime))
      case d: Date => Right(new DateTime(d.getTime))
      case str: String => Right(dateFormatGeneration.parseDateTime(str))
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass) )
    }
  }





  def find(providerId: String, userId: String): Future[Option[BasicProfile]] = {
    val foundUser: Option[User] = DB.withConnection { implicit c =>
      SQL("SELECT * FROM p_user_profile WHERE userId={userId} AND providerId={providerId}")
        .on(
          'userId -> userId,
          'providerId -> providerId
        ).as(user *).headOption
    }

    val internalObj: Option[BasicProfile] = foundUser match {
      case Some(u: User) => Option(u.userProfile)
      case _ => None
    }

    Future.successful(internalObj)
  }

  def findByEmailAndProvider(email: String, providerId: String): Future[Option[BasicProfile]] = {
    val foundUser: Option[User] = DB.withConnection { implicit c =>
      SQL("SELECT * FROM p_user_profile WHERE email={email} AND providerId={providerId}")
        .on(
          'email -> email,
          'providerId -> providerId
        ).as(user *).headOption
    }

    val internalObj: Option[BasicProfile] = foundUser match {
      case Some(u: User) => Option(u.userProfile)
      case _ => None
    }

    Future.successful(internalObj)
  }

  def save(user: BasicProfile, mode: SaveMode): Future[User] = {
    mode match {
      case SaveMode.SignUp =>
        addNewUser(user)
      case SaveMode.LoggedIn =>

    }
    // first see if there is a user with this BasicProfile already.
    val maybeUser: Option[BasicProfile] =  Await.result(find(user.providerId, user.userId), 10 seconds)

    maybeUser match {
      case Some(existingUser: BasicProfile) =>
        // We found this user, update him
        updateExistingUser(existingUser, None)
      case None =>
        // We need to create a new user
        addNewUser(user)
    }
  }

  def link(current: User, to: BasicProfile): Future[User] = {
    Future.successful(current)
  }

  def saveToken(token: MailToken): Future[MailToken] = {
    DB.withConnection { implicit c =>
      SQL("INSERT INTO token (uuid, email, creationTime, expirationTime, isSignUp) " +
        "VALUES ({uuid}, {email}, {creationTime}, {expirationTime}, {isSignUp})")
        .on(
          'uuid -> token.uuid,
          'email -> token.email,
          'creationTime -> new Timestamp(token.creationTime.getMillis),
          'expirationTime -> new Timestamp(token.expirationTime.getMillis),
          'isSignUp -> token.isSignUp
        ).executeUpdate()
    }

    Future.successful(token)
  }

  def findToken(token: String): Future[Option[MailToken]] = {
    val result = DB.withConnection { implicit c =>
      SQL("SELECT * FROM token WHERE uuid={uuid}").on(
        'uuid -> token
      ).as(mailToken *).headOption
    }

    Future.successful(result)
  }

  def deleteToken(uuid: String): Future[Option[MailToken]] = {
    val result = findToken(uuid)

    DB.withConnection { implicit c =>
      SQL("DELETE FROM token WHERE uuid={uuid}").on(
        'uuid -> uuid
      )
    }

    result
  }

  def deleteExpiredTokens() = {
    DB.withConnection { implicit c =>
      SQL("DELETE FROM token WHERE isExpired=true")
    }
  }

  def addNewUser(userProfile: BasicProfile): Future[User] = {
    val oAuth1Token: String = userProfile.oAuth1Info match {
      case Some(info) => info.token
      case _ => null
    }
    val oAuth1Secret: String = userProfile.oAuth1Info match {
      case Some(info) => info.secret
      case _ => null
    }

    val oAuth2AccessToken: String = userProfile.oAuth2Info match {
      case Some(info) => info.accessToken
      case _ => null
    }

    val oAuth2TokenType: String = userProfile.oAuth2Info match {
      case Some(info) => info.tokenType.get
      case _ => null
    }

    val oAuth2ExpiresIn: Int = userProfile.oAuth2Info match {
      case Some(info) => info.expiresIn.get
      case _ => 0
    }

    val oAuth2RefreshToken: String = userProfile.oAuth2Info match {
      case Some(info) => info.refreshToken.get
      case _ => null
    }

    val passwordHasher: String = userProfile.passwordInfo match {
      case Some(info) => info.hasher
      case _ => null
    }

    val password: String = userProfile.passwordInfo match {
      case Some(info) => info.password
      case _ => null
    }

    val passwordSalt:Option[String] = userProfile.passwordInfo match {
      case Some(info) => info.salt
      case _ => None
    }

    DB.withConnection { implicit c =>
      SQL("INSERT INTO p_user_profile (userId, providerId, firstName, lastName, fullName, email, " +
        "avatarUrl, authenticationMethod, oAuth1Token, oAuth1Secret, oAuth2AccessToken, oAuth2TokenType, " +
        "oAuth2ExpiresIn, oAuth2RefreshToken, passwordHasher, password, passwordSalt) VALUES " +
        "({userId}, {providerId}, {firstName}, {lastName}, {fullName}, {email}, {avatarUrl}, " +
        "{authMethod}, {oAuth1Token}, {oAuth1Secret}, {oAuth2AccessToken}, {oAuth2TokenType}, " +
        "{oAuth2ExpiresIn}, {oAuth2RefreshToken}, {passwordHasher}, {password}, {passwordSalt})").on(
          'userId -> userProfile.userId,
          'providerId -> userProfile.providerId,
          'firstName -> userProfile.firstName,
          'lastName -> userProfile.lastName,
          'fullName -> userProfile.fullName,
          'email -> userProfile.email,
          'avatarUrl -> userProfile.avatarUrl,
          'authMethod -> userProfile.authMethod.method,
          'oAuth1Token -> oAuth1Token,
          'oAuth1Secret -> oAuth1Secret,
          'oAuth2AccessToken -> oAuth2AccessToken,
          'oAuth2TokenType -> oAuth2TokenType,
          'oAuth2ExpiresIn -> oAuth2ExpiresIn,
          'oAuth2RefreshToken -> oAuth2RefreshToken,
          'passwordHasher -> passwordHasher,
          'password -> password,
          'passwordSalt -> passwordSalt.getOrElse("")
        ).executeUpdate()
    }

    Future.successful(User(1, userProfile))
  }

  def updateExistingUser(userProfile: BasicProfile, newPasswordInfo: Option[PasswordInfo]): Future[User] = {
    val oAuth1Token: String = userProfile.oAuth1Info match {
      case Some(info) => info.token
      case _ => null
    }
    val oAuth1Secret: String = userProfile.oAuth1Info match {
      case Some(info) => info.secret
      case _ => null
    }

    val oAuth2AccessToken: String = userProfile.oAuth2Info match {
      case Some(info) => info.accessToken
      case _ => null
    }

    val oAuth2TokenType: String = userProfile.oAuth2Info match {
      case Some(info) => info.tokenType.get
      case _ => null
    }

    val oAuth2ExpiresIn: Int = userProfile.oAuth2Info match {
      case Some(info) => info.expiresIn.get
      case _ => 0
    }

    val oAuth2RefreshToken: String = userProfile.oAuth2Info match {
      case Some(info) => info.refreshToken.get
      case _ => null
    }

    val passwordHasher: String =  newPasswordInfo match {
      case Some(info) =>   info.hasher
      case _ =>
        userProfile.passwordInfo match {
          case Some(info) => info.hasher
          case _ => null
        }
    }

    val password: String = newPasswordInfo match {
      case Some(info) => info.password
      case _ =>
        userProfile.passwordInfo match {
          case Some(info) => info.password
          case _ => null
        }
    }

    val passwordSalt:Option[String] = newPasswordInfo match {
      case Some(info) => info.salt
      case _ =>
        userProfile.passwordInfo match {
          case Some(info) => info.salt
          case _ => None
        }
    }

    DB.withConnection { implicit c =>
      SQL("UPDATE p_user_profile SET firstName={firstName}, lastName={lastName}" +
        ", fullName={fullName} , email={email} , avatarUrl={avatarUrl}" +
        " , authenticationMethod={authMethod} , oAuth1Token={oAuth1Token} , oAuth1Secret={oAuth1Secret} " +
        " , oAuth2AccessToken={oAuth2AccessToken} , oAuth2TokenType={oAuth2TokenType} " +
        " , oAuth2ExpiresIn={oAuth2ExpiresIn} , oAuth2RefreshToken={oAuth2RefreshToken}" +
        " , passwordHasher={passwordHasher} , password={password} , passwordSalt={passwordSalt}" +
        " WHERE userId={userId} AND providerId={providerId}").on(
          'firstName -> userProfile.firstName,
          'lastName -> userProfile.lastName,
          'fullName -> userProfile.fullName,
          'email -> userProfile.email,
          'avatarUrl ->userProfile.avatarUrl,
          'authMethod -> userProfile.authMethod.method,
          'oAuth1Token -> oAuth1Token,
          'oAuth1Secret -> oAuth1Secret,
          'oAuth2AccessToken -> oAuth2AccessToken,
          'oAuth2TokenType -> oAuth2TokenType,
          'oAuth2ExpiresIn -> oAuth2ExpiresIn,
          'oAuth2RefreshToken -> oAuth2RefreshToken,
          'passwordHasher -> passwordHasher,
          'password -> password,
          'passwordSalt -> passwordSalt.getOrElse(""),
          'userId -> userProfile.userId,
          'providerId -> userProfile.providerId
        ).executeUpdate()
    }

    Future.successful(User(1, userProfile))
  }

  override def passwordInfoFor(user: User): Future[Option[PasswordInfo]] = {
    val profile: BasicProfile =  Await.result(find(user.userProfile.providerId, user.userProfile.userId), 10 seconds).get
    Future.successful(profile.passwordInfo)
  }

  override def updatePasswordInfo(user: User, info: PasswordInfo): Future[Option[BasicProfile]] = {
    val updatedUser =  Await.result(updateExistingUser(user.userProfile, Option(info)), 10 seconds)
    Future.successful(Option(updatedUser.userProfile))
  }
}

