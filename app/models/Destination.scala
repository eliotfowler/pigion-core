package models

import anorm._
import anorm.SqlParser._
import play.api.db.DB
import play.api.Play.current
import play.api.libs.json.{Writes, Json}

case class Destination(id: Long, originalUrl: String, shortUrlHash: String, fileName:String, contentType: String)

object Destination {

  val BASE: Int = 62

  val UPPERCASE_OFFSET: Int = 55
  val LOWERCASE_OFFSET: Int = 61
  val DIGIT_OFFSET:Int = 48

  val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

  val destination = {
    get[Long]("id") ~
    get[String]("originalUrl") ~
    get[String]("shortUrlHash") ~
    get[String]("fileName") ~
    get[String]("contentType") map {
      case id ~ originalUrl ~ shortUrlHash ~ fileName ~ contentType => Destination(id, originalUrl, shortUrlHash, fileName, contentType)
    }
  }

  implicit val locationWrites = new Writes[Destination] {
    def writes(destination: Destination) = Json.obj(
      "id" -> destination.id,
      "shortUrlHash" -> destination.shortUrlHash,
      "fileName" -> destination.fileName,
      "contentType" -> destination.contentType
    )
  }

  def all(): List[Destination] = DB.withConnection { implicit c =>
    SQL("SELECT * FROM destination").as(destination *)
  }

  def create(originalUrl: String, fileName: String, contentType: String): String = {
    val shortUrlHash: String = dehydrate(getNextId())
    DB.withConnection { implicit c =>
      SQL("INSERT INTO destination (originalUrl, shortUrlHash, fileName, contentType) " +
        "values ({originalUrl}, {shortUrlHash}, {fileName}, {contentType})").on(
        'originalUrl -> originalUrl,
        'shortUrlHash -> shortUrlHash,
        'fileName -> fileName,
        'contentType -> contentType
      ).executeUpdate()
    }
    shortUrlHash
  }

  def delete(id: Long) = DB.withConnection { implicit c =>
    SQL("DELETE FROM destination WHERE id = {id}").on(
      'id -> id
    ).executeUpdate()
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
