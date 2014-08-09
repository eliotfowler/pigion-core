package models

import _root_.java.sql.{Date, Timestamp}
import anorm.SqlParser._
import anorm._
import play.api.db.DB
import securesocial.core._
import securesocial.core.PasswordInfo
import anorm.~
import securesocial.core.OAuth2Info
import securesocial.core.OAuth1Info
import securesocial.core.providers.Token
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormatter, DateTimeFormat}
import play.api.Play.current

  case class User(seqId: Long, identityId: IdentityId, firstName: String, lastName: String, fullName: String, email: Option[String],
                  oAuth1Info: Option[OAuth1Info], oAuth2Info: Option[OAuth2Info], avatarUrl: Option[String],
                  passwordInfo: Option[PasswordInfo], authMethod: AuthenticationMethod, isAdmin: Boolean) extends Identity

  object User {
    implicit def tuple2OAuth1Info(tuple: (Option[String], Option[String])): Option[OAuth1Info] =
      tuple match {
        case (Some(token), Some(secret)) => Some(OAuth1Info(token, secret))
        case _ => None
      }

    implicit def tuple2OAuth2Info(tuple: (Option[String], Option[String],
      Option[Int], Option[String])) = tuple match {
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
      get[Option[String]]("passwordSalt") ~
      get[Boolean]("isAdmin") map {
        case seqId ~ userId ~ providerId ~ firstName ~ lastName ~ fullName ~ email ~ avatarUrl ~
          authenticationMethod ~ oAuth1Token ~ oAuth1Secret ~ oAuth2AccessToken ~ oAuth2TokenType ~
          oAuth2ExpiresIn ~ oAuth2RefreshToken ~ passwordHasher ~ password ~ passwordSalt ~ isAdmin =>
          User(seqId, IdentityId(userId, providerId), firstName.getOrElse(""), lastName.getOrElse(""), fullName.getOrElse(""), email,
            (oAuth1Token, oAuth1Secret),
            (oAuth2AccessToken, oAuth2TokenType, oAuth2ExpiresIn, oAuth2RefreshToken),
            avatarUrl, (passwordHasher, password, passwordSalt), AuthenticationMethod(authenticationMethod), isAdmin)
      }
    }

    val token = {
      get[String]("uuid") ~
      get[String]("email") ~
      get[DateTime]("creationTime") ~
      get[DateTime]("expirationTime") ~
      get[Boolean]("isSignUp") map {
        case uuid ~ email ~ creationTime ~ expirationTime ~ isSignUp => Token(uuid, email, creationTime, expirationTime, isSignUp)
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

    def deleteExpiredTokens() {
      val now = new Timestamp(DateTime.now().getMillis)
      DB.withConnection { implicit c =>
        SQL("DELETE FROM token WHERE expirationTime < {now}").on(
          'now -> now
        )
      }
    }

    def deleteToken(uuid: String) {
      DB.withConnection { implicit c =>
        SQL("DELETE FROM token WHERE uuid={uuid}").on(
          'uuid -> uuid
        )
      }
    }

    def findToken(uuid: String): Option[Token] = {
      DB.withConnection { implicit c =>
        SQL("SELECT * FROM token WHERE uuid={uuid}").on(
          'uuid -> uuid
        ).as(token *).headOption
      }
    }

    def save(token: Token) {
      DB.withConnection { implicit c =>
        SQL("INSERT INTO token (uuid, email, creationTime, expirationTime, isSignUp) " +
          "VALUES ({uuid}, {email}, {creationTime}, {expirationTime}, {isSignUp})").on(
          'uuid -> token.uuid,
          'email -> token.email,
          'creationTime -> new Timestamp(token.creationTime.getMillis),
          'expirationTime -> new Timestamp(token.expirationTime.getMillis),
          'isSignUp -> token.isSignUp
        ).executeUpdate()
      }
    }

    def save(identity: Identity): Identity = {
      val user = find(identity.identityId)

      user match {
        case Some(existingUser) => updateExisting(existingUser)
        case _ => createNew(identity)
      }
    }

    def updateExisting(existingUser: Identity): Identity = {
      val oAuth1Token: String = existingUser.oAuth1Info match {
        case Some(info) => info.token
        case _ => null
      }
      val oAuth1Secret: String = existingUser.oAuth1Info match {
        case Some(info) => info.secret
        case _ => null
      }

      val oAuth2AccessToken: String = existingUser.oAuth2Info match {
        case Some(info) => info.accessToken
        case _ => null
      }

      val oAuth2TokenType: String = existingUser.oAuth2Info match {
        case Some(info) => info.tokenType.get
        case _ => null
      }

      val oAuth2ExpiresIn: Int = existingUser.oAuth2Info match {
        case Some(info) => info.expiresIn.get
        case _ => 0
      }

      val oAuth2RefreshToken: String = existingUser.oAuth2Info match {
        case Some(info) => info.refreshToken.get
        case _ => null
      }

      val passwordHasher: String = existingUser.passwordInfo match {
        case Some(info) => info.hasher
        case _ => null
      }

      val password: String = existingUser.passwordInfo match {
        case Some(info) => info.password
        case _ => null
      }

      val passwordSalt:Option[String] = existingUser.passwordInfo match {
        case Some(info) => info.salt
        case _ => None
      }

      DB.withConnection { implicit c =>
        SQL("UPDATE p_user SET firstName={firstName}, lastName={lastName}" +
          ", fullName={fullName} , email={email} , avatarUrl={avatarUrl}" +
          " , authenticationMethod={authMethod} , oAuth1Token={oAuth1Token} , oAuth1Secret={oAuth1Secret} " +
          " , oAuth2AccessToken={oAuth2AccessToken} , oAuth2TokenType={oAuth2TokenType} " +
          " , oAuth2ExpiresIn={oAuth2ExpiresIn} , oAuth2RefreshToken={oAuth2RefreshToken}" +
          " , passwordHasher={passwordHasher} , password={password} , passwordSalt={passwordSalt}" +
          " WHERE userId={userId} AND providerId={providerId}").on(
            'firstName -> existingUser.firstName,
            'lastName -> existingUser.lastName,
            'fullName -> existingUser.fullName,
            'email -> existingUser.email,
            'avatarUrl -> existingUser.avatarUrl,
            'authMethod -> existingUser.authMethod.method,
            'oAuth1Token -> oAuth1Token,
            'oAuth1Secret -> oAuth1Secret,
            'oAuth2AccessToken -> oAuth2AccessToken,
            'oAuth2TokenType -> oAuth2TokenType,
            'oAuth2ExpiresIn -> oAuth2ExpiresIn,
            'oAuth2RefreshToken -> oAuth2RefreshToken,
            'passwordHasher -> passwordHasher,
            'password -> password,
            'passwordSalt -> passwordSalt.getOrElse(null),
            'userId -> existingUser.identityId.userId,
            'providerId -> existingUser.identityId.providerId
         ).executeUpdate()
      }

      find(existingUser.identityId).get
    }

    def createNew(identity: Identity, isAdmin:Boolean = false): Identity = {
      val oAuth1Token: String = identity.oAuth1Info match {
        case Some(info) => info.token
        case _ => null
      }
      val oAuth1Secret: String = identity.oAuth1Info match {
        case Some(info) => info.secret
        case _ => null
      }

      val oAuth2AccessToken: String = identity.oAuth2Info match {
        case Some(info) => info.accessToken
        case _ => null
      }

      val oAuth2TokenType: String = identity.oAuth2Info match {
        case Some(info) => info.tokenType.get
        case _ => null
      }

      val oAuth2ExpiresIn: Int = identity.oAuth2Info match {
        case Some(info) => info.expiresIn.get
        case _ => 0
      }

      val oAuth2RefreshToken: String = identity.oAuth2Info match {
        case Some(info) => info.refreshToken.get
        case _ => null
      }

      val passwordHasher: String = identity.passwordInfo match {
        case Some(info) => info.hasher
        case _ => null
      }

      val password: String = identity.passwordInfo match {
        case Some(info) => info.password
        case _ => null
      }

      val passwordSalt:Option[String] = identity.passwordInfo match {
        case Some(info) => info.salt
        case _ => None
      }

      DB.withConnection { implicit c =>
        SQL("INSERT INTO p_user (userId, providerId, firstName, lastName, fullName, email, " +
          "avatarUrl, authenticationMethod, oAuth1Token, oAuth1Secret, oAuth2AccessToken, oAuth2TokenType, " +
          "oAuth2ExpiresIn, oAuth2RefreshToken, passwordHasher, password, passwordSalt, isAdmin) VALUES " +
          "({userId}, {providerId}, {firstName}, {lastName}, {fullName}, {email}, {avatarUrl}, " +
          "{authMethod}, {oAuth1Token}, {oAuth1Secret}, {oAuth2AccessToken}, {oAuth2TokenType}, " +
          "{oAuth2ExpiresIn}, {oAuth2RefreshToken}, {passwordHasher}, {password}, {passwordSalt}, {isAdmin})").on(
            'userId -> identity.identityId.userId,
            'providerId -> identity.identityId.providerId,
            'firstName -> identity.firstName,
            'lastName -> identity.lastName,
            'fullName -> identity.fullName,
            'email -> identity.email.getOrElse(null),
            'avatarUrl -> identity.avatarUrl.getOrElse(null),
            'authMethod -> identity.authMethod.method,
            'oAuth1Token -> oAuth1Token,
            'oAuth1Secret -> oAuth1Secret,
            'oAuth2AccessToken -> oAuth2AccessToken,
            'oAuth2TokenType -> oAuth2TokenType,
            'oAuth2ExpiresIn -> oAuth2ExpiresIn,
            'oAuth2RefreshToken -> oAuth2RefreshToken,
            'passwordHasher -> passwordHasher,
            'password -> password,
            'passwordSalt -> passwordSalt.getOrElse(null),
            'isAdmin -> isAdmin
          ).executeUpdate()
      }

      find(identity.identityId).get
    }

    def findByEmailAndProvider(email: String, providerId: String): Option[Identity] = {
      DB.withConnection { implicit c =>
        SQL("SELECT * FROM p_user WHERE email={email} AND providerId={providerId}").on(
          'email -> email,
          'providerId -> providerId
        ).as(user *).headOption
      }
    }

    def find(id: IdentityId): Option[User] = {
      DB.withConnection { implicit c =>
        SQL("SELECT * FROM p_user WHERE userId={userId} AND providerId={providerId}").on(
          'userId -> id.userId,
          'providerId -> id.providerId
        ).as(user *).headOption
      }
    }

    def find(seqId: Long): Option[User] = {
      DB.withConnection { implicit c =>
        SQL("SELECT * FROM p_user WHERE seqId={seqId}").on(
          'seqId -> seqId
        ).as(user *).headOption
      }
    }
  }