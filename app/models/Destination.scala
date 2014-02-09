package models

import anorm._
import anorm.SqlParser._
import play.api.db.DB

class Destination {
  val BASE: Int = 62

  val UPPERCASE_OFFSET: Int = 55
  val LOWERCASE_OFFSET: Int = 61
  val DIGIT_OFFSET:Int = 48

  val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

  case class Destination(id: Long, originalUrl: String, shortUrlHash: String)

  object Destination {

    val destination = {
      get[Long]("id") ~
      get[String]("originalUrl") ~
      get[String]("shortUrlHash") map {
        case id ~ originalUrl ~ shortUrlHash => Destination(id, originalUrl, shortUrlHash)
      }
    }

    def all(): List[Destination] = DB.withConnection { implicit c =>
      SQL("SELECT * FROM destination").as(destination *)
    }

    def create(originalUrl: String) {
      val shortUrlHash: String = {}
      DB.withConnection { implicit c =>
        SQL("INSERT INTO description (originalUrl, shortUrlHash) values ({originalUrl}, {shortUrlHash})").on(
          'originalUrl -> originalUrl,
          'shortUrlHash -> shortUrlHash
        ).executeUpdate()
      }
    }

    def delete(id: Long) {}

    def getNextId(): Long = {
      DB.withConnection { implicit c =>
        SQL("SELECT CURVAL('description_id_seq')")
      }.as(scalar[Long].single) + 1
    }

    // Take a url hash and figure out what it's url is
    def saturate(key: String): Int = {
      key.foldLeft(0)((r,c) => r + math.pow(ALPHABET.indexOf(c), key.indexOf(c)).toInt)
    }

    // Given the next id in the sequence for this table,
    // generate the url hash
    def dehydrate(id: Long): String = {
      val bigIntId: BigInt = id;
      bigIntId.toString(62);
    }

  }
}
