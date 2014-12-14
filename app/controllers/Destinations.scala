package controllers

import _root_.java.io.File
import _root_.java.net.URL

import play.api.libs.iteratee.Enumerator
import play.api.mvc._
import models.{User, Destination}
import play.api.libs.json._
import play.api.data._
import play.api.data.Forms._
import securesocial.core._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

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
    val destination = Destination.getDestinationForNonIncrementingHash(key).orNull

    destination match {
      case null => NotFound
      case d if d.contentType.startsWith("image") => Ok(views.html.imageViewer(destination))
      case d if d.contentType == "url" => Redirect(destination.originalUrl)
      case _ => Ok(views.html.fileDownloader(destination))
    }
  }

  def downloadFileWithKey(key: String, fileName: String, password: String) = Action {
    val destination = Destination.getDestinationForNonIncrementingHash(key)
    destination match {
      case Some(d) =>
        if(d.password != null && !Destination.passwordMatchesForDestination(d, password)) {
          Unauthorized
        } else if(d.maxDownloads == -1 || d.numDownloads < d.maxDownloads) {
          Destination.incrementDownloadCount(key)
          val url = new URL(d.originalUrl)
          val dataContent: Enumerator[Array[Byte]] = Enumerator.fromStream(url.openStream())
          Ok.chunked(dataContent).withHeaders(
            "Content-Disposition" -> "attachment",
            "Content-Type" -> d.contentType,
            "Content-Size" -> d.contentSize.toString
          )
        } else {
          Gone
        }

      case _ => NotFound
    }
  }

  def getFileInfoForKey(key: String) = Action {
    val destination = Destination.getDestinationForNonIncrementingHash(key)

    destination match {
      case Some(d) => Ok(Json.toJson(d))
      case _ => NotFound
    }
  }

  def allFilesForUser = SecuredAction { implicit request =>
    val list = Json.toJson(Destination.allForUser(request.user.userSeqId))

    Ok(list)
  }

  def currentFilesForUser = SecuredAction { implicit request =>
    val list = Json.toJson(Destination.currentForUser(request.user.userSeqId))

    Ok(list)
  }
}
