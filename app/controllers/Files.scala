package controllers

import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc._
import fly.play.s3._
import models.{MultipartUploadHandler, User, Destination, FlowData}
import play.api.mvc.BodyParsers.parse.Multipart.PartHandler
import play.api.mvc.BodyParsers.parse.Multipart.handleFilePart
import play.api.mvc.BodyParsers.parse.multipartFormData
import play.api.libs.iteratee.Iteratee
import java.io.ByteArrayOutputStream
import play.api.libs.{json, MimeTypes}
import securesocial.core.{Authorization, RuntimeEnvironment}
import scala.concurrent.{Future, ExecutionContext, Await, Awaitable}
import ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import play.api.mvc.BodyParsers.parse.Multipart.FileInfo
import fly.play.s3.BucketFilePartUploadTicket
import fly.play.s3.BucketFilePart
import play.api.mvc.MultipartFormData.FilePart
import fly.play.s3.BucketFile
import java.net.URLEncoder
import play.api.libs.json.Json
import services.StreamingBodyParser.streamingBodyParser

class Files(override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  case class WithProvider(provider: String) extends Authorization[User] {
    def isAuthorized(user: User, request: RequestHeader) = {
      user.userProfile.providerId == provider
    }
  }

  case class Item(id: Long) // replace with your real world item

  def SecuredItemAction(f: => Item => Request[AnyContent] => Result) =
    SecuredAction { implicit request =>
      val item = Some(new Item(7)) // replace with your real world item fetch
      item.map { item =>
        f(item)(request)
      }.getOrElse(NotFound)
    }

  object LoggingAction extends ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[SimpleResult]) = {
      Logger.info("Calling action ------- WOO!")
      block(request)
    }
  }

  object FileValidationAction extends SecuredActionBuilder {
    def invokeBlock[A](request: SecuredRequest[A], block: SecuredRequest[A] => Future[SimpleResult]) = {
      Logger.info("User id is " + request.user.userProfile.userId)
      block(request)
    }

    override def invokeBlock[A](request: Request[A], block: SecuredRequest[A] => Future[SimpleResult]) = {
      val securedResult: Option[SecuredRequest[A]] = request match {
        case r: SecuredRequest[A] => Option(r)
        case _ => None
      }
      Logger.info("Calling action ------- WOO!")
      securedResult match {
        case Some(r) =>
          block(r)
        case _ =>
          Future.successful(Forbidden)
      }
    }
  }


  def testAction = SecuredAction { request =>
    Ok("Test succeeded")
  }

  def streamConstructor(filename: String) = {
    Option(new MultipartUploadHandler(filename))
  }

  def upload = SecuredAction(streamingBodyParser(streamConstructor)) { request =>
    val params = request.body.asFormUrlEncoded // you can extract request parameters for whatever your app needs
    val result = request.body.files(0).ref
    if (result.isRight) {
    // streaming succeeded
      val fileName = result.right.get.filename
      val dateUploaded = new DateTime()
      val bucket = S3("pigion")
      val updatedFileName = request.user.userProfile.userId + "/" + dateUploaded.getMillis + "/" + fileName;
      // TODO: Check this
      bucket rename(fileName, updatedFileName, AUTHENTICATED_READ)
      val url = bucket.url(updatedFileName)
      val fileContentType = MimeTypes.forFileName(fileName.toLowerCase).getOrElse("application/octet-stream")
      val seqId = request.user.userSeqId
      Ok(Json.obj(
        "shortUrl" -> Destination.create(url, updatedFileName, fileContentType, seqId))
      )
    } else {
    // file streaming failed
      Ok(s"Streaming error occurred: ${result.left.get.errorMessage}")
    }
  }
}

