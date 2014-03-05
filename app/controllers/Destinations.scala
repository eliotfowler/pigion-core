package controllers

import play.api._
import play.api.mvc._
import models.Destination
import play.api.libs.json._
import java.net.URL
import play.api.data._
import play.api.data.Forms._

object Destinations extends Controller {

  case class UrlData(originalUrl: String)

  val urlForm = Form(
    mapping(
      "originalurl" -> text//.verifying(!_.startsWith("javascript")).verifying(!_.startsWith("data"))
    )(UrlData.apply)(UrlData.unapply)
  )

  val singleForm = Form(
    single(
      "originalUrl" -> text
    )
  )

  def shortenUrl = Action { implicit request =>
    Logger.info("request body is " + request.body.toString)
    val originalUrl: String = (request.body.asJson.get \ "originalUrl").asOpt[String].get
    val url: URL = new URL(originalUrl)
    Ok(Json.obj(
      "originalUrl"->originalUrl,
      "shortUrl"->Destination.create(originalUrl)
    ))
  }

  def goToOriginalUrl(key: String) = Action {
    Redirect(Destination.getUrlForHash(key))
  }

}
