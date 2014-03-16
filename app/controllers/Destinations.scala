package controllers

import play.api._
import play.api.mvc._
import models.Destination
import play.api.libs.json._
import java.net.{URLEncoder, URL}
import play.api.data._
import play.api.data.Forms._
import fly.play.s3.{BucketFile, S3}
import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration.Duration
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Awaitable
import com.sksamuel.scrimage.Image
import com.sksamuel.scrimage.ScaleMethod.Bicubic
import java.io.{File, OutputStream}

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

  def getImageFromS3(destination: Destination) = Action.async {
  val bucket = S3("pigion")
  val result = bucket get URLEncoder.encode(destination.fileName, "UTF-8")
  result.map {
    case BucketFile(name, contentType, content, acl, headers) => {
      Ok(Image(content).bound(250,250).write)
    }
    case _ => BadRequest("Unable to retrieve file.")
  }
}


  def displayContentFromKey(key: String) = Action.async {
    val destination = Destination.getDestinationForHash(key).get
    val bucket = S3("pigion")
    val result = bucket get URLEncoder.encode(destination.fileName, "UTF-8")
    result.map {
      case BucketFile(name, contentType, content, acl, headers) => {
        val out:File = null

//        Ok(views.html.imageViewer(Image(content).bound(250,250).write))
        Ok("test")
      }
      case _ => BadRequest("Unable to retrieve file.")
    }
  }

  def all = Action {
    Ok(Json.toJson(Destination.all()))
  }

}
