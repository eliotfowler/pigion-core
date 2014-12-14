package models

import anorm._
import anorm.SqlParser._
import org.mindrot.jbcrypt.BCrypt
import play.api.db.DB
import play.api.Play.current
import play.api.libs.json.{Writes, Json}
import org.joda.time.DateTime
import java.sql.{Date, Timestamp}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import fly.play.s3.S3
import play.api.Logger
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random


case class Destination(id: Long, userSeqId: Long, originalUrl: String, shortUrlHash: String,
                       fileName:String, contentType: String,
                       expirationTime: DateTime, isExpired: Boolean, isDeleted: Boolean, uploadCompleted: Boolean,
                       contentSize: Long, numDownloads: Int, maxDownloads: Int, password: Option[String])

object Destination {
  val BASE: Int = 62

  val UPPERCASE_OFFSET: Int = 55
  val LOWERCASE_OFFSET: Int = 61
  val DIGIT_OFFSET:Int = 48

  val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

  val destination = {
    get[Long]("id") ~
    get[Long]("userSeqId") ~
    get[String]("originalUrl") ~
    get[String]("shortUrlHash") ~
    get[String]("fileName") ~
    get[String]("contentType") ~
    get[DateTime]("expirationTime") ~
    get[Boolean]("isExpired")  ~
    get[Boolean]("isDeleted")  ~
    get[Boolean]("uploadCompleted")  ~
    get[Long]("contentSize")   ~
    get[Int]("numDownloads")  ~
    get[Int]("maxDownloads") ~
    get[Option[String]]("password") map {
      case id ~ userSeqId ~ originalUrl ~ shortUrlHash ~ fileName ~
        contentType ~ expirationTime ~ isExpired ~ isDeleted ~
        uploadCompleted ~ contentSize ~ numDownloads ~ maxDownloads ~ password =>
        Destination(id, userSeqId, originalUrl, shortUrlHash, fileName,
          contentType, expirationTime, isExpired, isDeleted, uploadCompleted,
          contentSize, numDownloads, maxDownloads, password)
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

  implicit val locationWrites = new Writes[Destination] {
    def writes(destination: Destination) = Json.obj(
      "id" -> destination.id,
      "fullUrl" -> destination.originalUrl,
      "shortUrlHash" -> destination.shortUrlHash,
      "fileName" -> destination.fileName,
      "contentType" -> destination.contentType,
      "expirationTime" -> destination.expirationTime,
      "isExpired" -> destination.isExpired,
      "contentSize" -> destination.contentSize,
      "numDownloads" -> destination.numDownloads,
      "maxDownloads" -> destination.maxDownloads
    )
  }

  def all(): List[Destination] = DB.withConnection { implicit c =>
    SQL("SELECT * FROM destination").as(destination *)
  }

  def allForUser(userSeqId: Long): List[Destination] = DB.withConnection { implicit c =>
    SQL("SELECT * FROM destination WHERE userSeqId = {userSeqId}").on(
        'userSeqId -> userSeqId
      )
      .as(destination *)
  }

  def currentForUser(userSeqId: Long): List[Destination] = DB.withConnection { implicit c =>
    SQL("SELECT * FROM destination WHERE (expirationTime > {twoDaysAgo} OR isExpired = false) AND userSeqId = {userSeqId}").on(
      'userSeqId -> userSeqId,
      'twoDaysAgo ->  new Timestamp(DateTime.now().minusDays(2).getMillis)
    )
      .as(destination *)
  }

  def create(originalUrl: String, fileName: String, contentType: String, seqId: Long, contentSize: Long): String = {
    val shortUrlHash = generateRandomUnusedHash()
    DB.withConnection { implicit c =>
      SQL("INSERT INTO destination (originalUrl, shortUrlHash, fileName, contentType, expirationTime, isExpired, userSeqId, isDeleted, uploadCompleted, contentSize ) " +
        "values ({originalUrl}, {shortUrlHash}, {fileName}, {contentType}, {expirationTime}, false, {userSeqId}, false, true, {contentSize})").on(
        'originalUrl -> originalUrl,
        'shortUrlHash -> shortUrlHash,
        'fileName -> fileName,
        'contentType -> contentType,
      // Changing the expiration time to a week for testing
        'expirationTime -> new Timestamp(DateTime.now().plusWeeks(1).getMillis()),
        'userSeqId -> seqId,
        'contentSize -> contentSize
      ).executeUpdate()
    }
    shortUrlHash
  }

  def getNumFilesUploadedForUser(userSeqId: Long): Long = DB.withConnection { implicit c =>
    SQL("SELECT count(*) FROM destination WHERE userSeqId={userSeqId}").on(
      'userSeqId -> userSeqId
    ).as(scalar[Long].single)
  }

  def getNumCurrentFilesUploadedForUser(userSeqId: Long): Long = DB.withConnection { implicit c =>
    SQL("SELECT count(*) FROM destination WHERE userSeqId={userSeqId} AND isExpired = false").on(
      'userSeqId -> userSeqId
    ).as(scalar[Long].single)
  }

  def getNumDownloadsFilesByUser(userSeqId: Long): Long = DB.withConnection { implicit c =>
    SQL("SELECT sum(numDownloads) FROM destination WHERE userSeqId={userSeqId}").on(
      'userSeqId -> userSeqId
    ).as(scalar[Long].single)
  }

  def getTotalSizeUploadedForUser(userSeqId: Long): Long = DB.withConnection { implicit c =>
    SQL("SELECT sum(contentSize) FROM destination WHERE userSeqId={userSeqId}").on(
      'userSeqId -> userSeqId
    ).as(scalar[Long].single)
  }

  def getCurrentSizeUploadedForUser(userSeqId: Long): Long = DB.withConnection { implicit c =>
    SQL("SELECT sum(contentSize) FROM destination WHERE userSeqId={userSeqId} AND isExpired = false").on(
      'userSeqId -> userSeqId
    ).as(scalar[Long].single)
  }

  def getFileById(fileId: Int): Option[Destination] = DB.withConnection { implicit c =>
    SQL("SELECT * FROM destination WHERE id={fileId}").on(
      'fileId -> fileId
    ).as(destination *).headOption
  }

  def incrementDownloadCount(key: String) = DB.withConnection { implicit c =>
    SQL("UPDATE destination SET numDownloads = numDownloads + 1 WHERE shortUrlHash = {key}")
      .on(
        'key -> key
      ).executeUpdate()
  }

//  def delete(id: Long) = DB.withConnection { implicit c =>
//    SQL("DELETE FROM destination WHERE id = {id}").on(
//      'id -> id
//    ).executeUpdate()
//  }

  def markFileAsDeleted(id: Int) = DB.withConnection { implicit c =>
    SQL("UPDATE destination SET isDeleted=true WHERE id = {id}")
      .on(
        'id -> id
      ).executeUpdate()
  }

  def deleteFile(id: Int) = {
    val bucket = S3("pigion")
    val destination = getFileById(id)
    destination match {
      case Some(d) => {
        val owner: User = Await.result(User.find(d.userSeqId), 10 seconds)
        val modifiedFileName = owner.userProfile.providerId + "/" + d.fileName
        bucket - modifiedFileName
        markFileAsDeleted(id)
      }
    }
  }

  def expireFile(id: Int) = DB.withConnection { implicit c =>
    SQL("UPDATE destination SET isExpired=true WHERE id = {id}")
      .on(
        'id -> id
      ).executeUpdate()
  }

  def expired = DB.withConnection { implicit c =>
    SQL("SELECT * FROM destination WHERE isExpired = true").as(destination *)
  }

  def expiredOverTwoDaysAgo = DB.withConnection { implicit c =>
    SQL("SELECT * FROM destination WHERE isExpired = true AND expirationTime < {twoDaysAgo}").on(
      'twoDaysAgo -> new Timestamp(DateTime.now().minusDays(2).getMillis)
    )
    .as(destination *)
  }

  def removeExpiredFromS3() {
    val bucket = S3("pigion")
    val expiredDestinations = expiredOverTwoDaysAgo
    expiredDestinations.foreach(destination => {

      val owner: User = Await.result(User.find(destination.userSeqId), 10 seconds)
      val modifiedFileName = owner.userProfile.providerId + "/" + destination.fileName
      Logger.info("modified file name is " + modifiedFileName)
      bucket - modifiedFileName
    })
  }

  def getUrlForHash(hash: String): String = {
    DB.withConnection { implicit c =>
      SQL("SELECT originalUrl FROM destination WHERE shortUrlHash={shortUrlHash}").on(
        'shortUrlHash -> hash
      ).as(scalar[String].single)
    }
  }

  def getDestinationForNonIncrementingHash(hash: String): Option[Destination] = {
    DB.withConnection { implicit c =>
      SQL("SELECT * FROM destination WHERE shortUrlHash={shortUrlHash}").on(
        'shortUrlHash -> hash
      ).as(destination *).headOption
    }
  }

  def getNextId(): Long = {
    DB.withConnection { implicit c =>
      SQL("SELECT nextval('destination_id_seq')").as(scalar[Long].single) + 1
    }
  }

  // Password
  def setPasswordForDestination(id: Long, password: String) = DB.withConnection { implicit c =>
    val encryptedPassword: String = BCrypt.hashpw(password, BCrypt.gensalt())
    SQL("UPDATE destination SET password={encryptedPassword} WHERE id = {id}")
      .on(
        'encryptedPassword -> encryptedPassword,
        'id -> id
      ).executeUpdate()
  }

  def passwordMatchesForDestination(destination: Destination, password: String): Boolean = {
    if (password == null) false
    else {
      destination.password match {
        case Some(p) => BCrypt.checkpw(password, destination.password.get)
        case _ => false
      }
    }
  }

  // Since we don't want the URL to be incrementing or even to be discrnable that one is in
  // fact "greater" than another, let's just keep generating random strings of 8 characters
  // until we find on that has not been used yet
  def generateRandomUnusedHash(): String = {
    // First generate the hash
      val newHash: String = generateRandomHash
      // Make sure it is unused
      val originalUrl = DB.withConnection { implicit c =>
        SQL("SELECT originalUrl FROM destination WHERE shortUrlHash={shortUrlHash}").on(
          'shortUrlHash -> newHash
        ).as(scalar[String].singleOpt)
      }
      if(originalUrl.isEmpty) {
        newHash
      } else {
        generateRandomUnusedHash()
      }
  }

  def generateRandomHash(): String = {
    var hashString = ""
    while(hashString.length < 8) {
      val idx = Random.nextInt(62)
      hashString = hashString + ALPHABET.charAt(idx)
    }

    hashString
  }

}
