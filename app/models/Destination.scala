package models

import anorm._
import anorm.SqlParser._
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
                       contentSize: Long, numDownloads: Int, maxDownloads: Int)

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
      get[Int]("maxDownloads") map {
      case id ~ userSeqId ~ originalUrl ~ shortUrlHash ~ fileName ~
        contentType ~ expirationTime ~ isExpired ~ isDeleted ~
        uploadCompleted ~ contentSize ~ numDownloads ~ maxDownloads =>
        Destination(id, userSeqId, originalUrl, shortUrlHash, fileName,
          contentType, expirationTime, isExpired, isDeleted, uploadCompleted, contentSize, numDownloads, maxDownloads)
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

  def create(originalUrl: String, fileName: String, contentType: String, seqId: Long, contentSize: Long): String = {
//    val shortUrlHash: String = dehydrate(getNextId())
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

  def incrementDownloadCount(key: String) = DB.withConnection { implicit c =>
    SQL("UPDATE destination SET numDownloads = numDownloads + 1 WHERE shortUrlHash = {key}")
      .on(
        'key -> key
      ).executeUpdate()
  }

  def delete(id: Long) = DB.withConnection { implicit c =>
    SQL("DELETE FROM destination WHERE id = {id}").on(
      'id -> id
    ).executeUpdate()
  }

  def expired() = DB.withConnection { implicit c =>
    SQL("SELECT * FROM destination WHERE isExpired = true").as(destination *)
  }

  def removeExpiredFromS3() {
    val bucket = S3("pigion")
    val expiredDestinations = expired()
    expiredDestinations.foreach(destination => {

      val owner: User = Await.result(User.find(destination.userSeqId), 10 seconds)
      val modifiedFileName = owner.userProfile.providerId + "/" + destination.fileName
      Logger.info("modified file name is " + modifiedFileName)
      bucket - modifiedFileName
//      delete(destination.id)
    })
  }

  def getUrlForHash(hash: String): String = {
    DB.withConnection { implicit c =>
      SQL("SELECT originalUrl FROM destination WHERE shortUrlHash={shortUrlHash}").on(
        'shortUrlHash -> hash
      ).as(scalar[String].single)
    }
  }

//  def getDestinationForHash(hash: String): Option[Destination] = {
//    DB.withConnection { implicit c =>
//      SQL("SELECT * FROM destination WHERE id={id}").on(
//        'id -> saturate(hash)
//      ).as(destination *).headOption
//    }
//  }

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

  // Take a url hash and figure out what it's id in the database is
//  def saturate(key: String): Int = {
//    key.foldLeft(0)((r,c) => r + ALPHABET.indexOf(c) * math.pow(ALPHABET.size, key.size - key.indexOf(c) - 1).toInt)
//  }
//
//  // Given the next id in the sequence for this table,
//  // generate the url hash
//  def dehydrate(id: Long): String = {
//    _dehydrate(id, List[Long]()).map(x => ALPHABET.charAt(x.toInt)).mkString
//  }
//
//  def _dehydrate(id:Long, digits:List[Long]): List[Long] = {
//    val remainder = id % BASE
//    if(id < BASE) remainder +: digits
//    else _dehydrate(id/BASE, remainder +: digits)
//  }

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
