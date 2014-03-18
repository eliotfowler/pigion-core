package controllers

import play.api.mvc._
import models.Destination
import play.api.libs.json._
import play.api.data._
import play.api.data.Forms._
import securesocial.core._

object Destinations extends Controller with SecureSocial {

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
    Ok(Json.obj(
      "originalUrl"->originalUrl,
      "shortUrl"->Destination.create(originalUrl, originalUrl, "url")
    ))
  }

  def goToOriginalUrl(key: String) = Action {
    val destination = Destination.getDestinationForHash(key).getOrElse(null)

    destination match {
      case null => NotFound
      case d if d.contentType.startsWith("image") => Ok(views.html.imageViewer(destination))
      case d if d.contentType == "url" => Redirect(destination.originalUrl)
      case _ => Ok(views.html.fileDownloader(destination))
    }
  }

  def all = SecuredAction {
    Ok(Json.toJson(Destination.all()))
  }
}
