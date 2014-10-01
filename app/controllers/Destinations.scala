package controllers

import play.api.mvc._
import models.{User, Destination}
import play.api.libs.json._
import play.api.data._
import play.api.data.Forms._
import securesocial.core._

class Destinations(override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  case class UrlData(originalUrl: String)

  val urlForm = Form(
    mapping(
      "originalurl" -> text
    )(UrlData.apply)(UrlData.unapply)
  )

  val singleForm = Form(
    single(
      "originalUrl" -> text
    )
  )

  def shortenUrl = SecuredAction { implicit request =>
    val originalUrl: String = (request.body.asJson.get \ "originalUrl").asOpt[String].get
    val userSeqId = request.user.userSeqId
    Ok(Json.obj(
      "originalUrl"->originalUrl,
      "shortUrl"->Destination.create(originalUrl, originalUrl, "url", userSeqId, 0)
    ))
  }

  def goToOriginalUrl(key: String) = Action {
    val destination = Destination.getDestinationForHash(key).orNull

    destination match {
      case null => NotFound
      case d if d.contentType.startsWith("image") => Ok(views.html.imageViewer(destination))
      case d if d.contentType == "url" => Redirect(destination.originalUrl)
      case _ => Ok(views.html.fileDownloader(destination))
    }
  }

  def getFileInfoForKey(key: String) = Action {
    val destination = Destination.getDestinationForHash(key)

    destination match {
      case Some(d) => Ok(Json.toJson(d))
      case _ => NotFound
    }
  }

  def all = SecuredAction {
    Ok(Json.toJson(Destination.all()))
  }
}
