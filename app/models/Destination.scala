package models

class Destination {
  case class Destination(id: Long, originalUrl: String, shortUrlPath: String)

  object Destination {

    def all(): List[Destination] = Nil

    def create(originalUrl: String) {}

    def delete(id: Long) {}

  }
}
