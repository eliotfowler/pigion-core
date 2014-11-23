package controllers

import fly.play.s3._
import models.{Destination, MultipartUploadHandler, User}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.MimeTypes
import play.api.libs.iteratee.{Iteratee, Done}
import play.api.libs.json.Json
import play.api.mvc._
import securesocial.core.java.SecuredAction
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.{Authorization, RuntimeEnvironment}
import services.PigionUserService
import services.StreamingBodyParser.streamingBodyParser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import ExecutionContext.Implicits.global

class Files(override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  case class WithProvider(provider: String) extends Authorization[User] {
    def isAuthorized(user: User, request: RequestHeader) = {
      user.userProfile.providerId == provider
    }
  }

  object FileValidationAction extends FileValidationActionBuilder {
    def apply[A]() = new FileValidationActionBuilder()
  }

  class FileValidationActionBuilder(authorize: Option[Authorization[User]] = None) extends SecuredActionBuilder(authorize) {
    def validateFile[A](block: SecuredRequest[A] => Future[SimpleResult]): SecuredRequest[A] => Future[SimpleResult] = { securedRequest =>
      val fileSize = securedRequest.headers.get("Content-Length")
      // First we need to see what the user's permissions are
      val userService = new PigionUserService()
      val userUsage = userService.getUserUsage(securedRequest.user)
      userUsage.onComplete({
        case Success(maybeUsage) => {
          maybeUsage match {
            case Some(usage) =>
              val userCurrentUploads: Long = usage.currentActiveUploads.getOrElse(0)
              val userCurrentUploadBytes: Long = usage.currentActiveUploadBytes.getOrElse(0)
              val filePutsUserOverSizeLimit: Boolean = Integer.parseInt(fileSize.get) + userCurrentUploadBytes > securedRequest.user.userLevel.maxActiveUploadBytes
              val filePutsUserOverUploadLimit: Boolean = (userCurrentUploads + 1) > securedRequest.user.userLevel.maxActiveUploads

              if(!filePutsUserOverSizeLimit && !filePutsUserOverUploadLimit) {
                Logger.info(s"User id is ${securedRequest.user.userProfile.userId}")
                block(securedRequest)
              } else {
                Future.successful(BadRequest("User limit reached. Delete a file to free up some space for this upload."))
              }
            case _ =>
              Future.successful(InternalServerError("Couldn't find usage information"))
          }


        }
        case Failure(exception) => {
          //Do something with my error
          Future.successful(InternalServerError("Database Error"))
        }
      })
      Future.successful(InternalServerError("Error"))
    }

    override def invokeBlock[A](request: Request[A], block: (SecuredRequest[A]) => Future[SimpleResult]): Future[SimpleResult] = {
      invokeSecuredBlock(authorize, request, validateFile(block))
    }
  }



  def streamConstructor(filename: String) = {
    Option(new MultipartUploadHandler(filename))
  }

  def upload = SecuredAction(streamingBodyParser(streamConstructor)) { request =>
//    val params = request.body.asFormUrlEncoded // you can extract request parameters for whatever your app needs
    val result = request.body.files(0).ref
    if (result.isRight) {
    // streaming succeeded
      val fileName = result.right.get.filename
      val dateUploaded = new DateTime()
      val bucket = S3("pigion")
      val updatedFileName = request.user.userProfile.userId + "/" + dateUploaded.getMillis + "/" + fileName;
      // TODO: Check this
      bucket rename(fileName, updatedFileName, PUBLIC_READ)
      val url = bucket.url(updatedFileName)
      val fileContentType = MimeTypes.forFileName(fileName.toLowerCase).getOrElse("application/octet-stream")
      val seqId = request.user.userSeqId
      Ok(Json.obj(
        "shortUrl" -> Destination.create(url, updatedFileName, fileContentType, seqId,
          Integer.parseInt(request.headers.get("Content-Length").getOrElse("0"))))
      )
    } else {
    // file streaming failed
      Ok(s"Streaming error occurred: ${result.left.get.errorMessage}")
    }
  }
}



