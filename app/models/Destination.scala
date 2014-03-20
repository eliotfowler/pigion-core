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


case class Destination(id: Long, userSeqId: Long, originalUrl: String, shortUrlHash: String, fileName:String, contentType: String, expirationTime: DateTime, isExpired: Boolean)

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
    get[Boolean]("isExpired") map {
      case id ~ userSeqId ~ originalUrl ~ shortUrlHash ~ fileName ~ contentType ~ expirationTime ~ isExpired =>
        Destination(id, userSeqId, originalUrl, shortUrlHash, fileName, contentType, expirationTime, isExpired)
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
      "shortUrlHash" -> destination.shortUrlHash,
      "fileName" -> destination.fileName,
      "contentType" -> destination.contentType,
      "expirationTime" -> destination.expirationTime,
      "isExpired" -> destination.isExpired
    )
  }

  def all(): List[Destination] = DB.withConnection { implicit c =>
    SQL("SELECT * FROM destination").as(destination *)
  }

  def create(originalUrl: String, fileName: String, contentType: String, seqId: Long): String = {
    val shortUrlHash: String = dehydrate(getNextId())
    DB.withConnection { implicit c =>
      SQL("INSERT INTO destination (originalUrl, shortUrlHash, fileName, contentType, expirationTime, isExpired, userSeqId) " +
        "values ({originalUrl}, {shortUrlHash}, {fileName}, {contentType}, {expirationTime}, false, {userSeqId})").on(
        'originalUrl -> originalUrl,
        'shortUrlHash -> shortUrlHash,
        'fileName -> fileName,
        'contentType -> contentType,
        'expirationTime -> new Timestamp(DateTime.now().plusMinutes(10).getMillis()),
        'userSeqId -> seqId
      ).executeUpdate()
    }
    shortUrlHash
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

      val owner: User = User.find(destination.userSeqId).get
      val modifiedFileName = owner.identityId + "/" + destination.fileName
      Logger.info("modified file name is " + modifiedFileName)
      bucket - modifiedFileName
//      delete(destination.id)
    })
  }

  def getUrlForHash(hash: String): String = {
    DB.withConnection { implicit c =>
      SQL("SELECT originalUrl FROM destination WHERE id={id}").on(
        'id -> saturate(hash)
      ).as(scalar[String].single)
    }
  }

  def getDestinationForHash(hash: String): Option[Destination] = {
    DB.withConnection { implicit c =>
      SQL("SELECT * FROM destination WHERE id={id}").on(
        'id -> saturate(hash)
      ).as(destination *).headOption
    }
  }

  def getNextId(): Long = {
    DB.withConnection { implicit c =>
      SQL("SELECT nextval('destination_id_seq')").as(scalar[Long].single) + 1
    }
  }

  // Take a url hash and figure out what it's id in the database is
  def saturate(key: String): Int = {
    key.foldLeft(0)((r,c) => r + ALPHABET.indexOf(c) * math.pow(ALPHABET.size, key.size - key.indexOf(c) - 1).toInt)
  }

  // Given the next id in the sequence for this table,
  // generate the url hash
  def dehydrate(id: Long): String = {
    _dehydrate(id, List[Long]()).map(x => ALPHABET.charAt(x.toInt)).mkString
  }

  def _dehydrate(id:Long, digits:List[Long]): List[Long] = {
    val remainder = id % BASE
    if(id < BASE) remainder +: digits
    else _dehydrate(id/BASE, remainder +: digits)
  }
}
