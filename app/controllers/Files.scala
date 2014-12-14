package controllers

import fly.play.s3._
import models.{UserUsage, Destination, MultipartUploadHandler, User}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.MimeTypes
import play.api.libs.iteratee.{Iteratee, Done}
import play.api.libs.json.Json
import play.api.mvc._
import securesocial.core.Authorization
import securesocial.core.authenticator.Authenticator
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.{Authorization, RuntimeEnvironment}
import services.PigionUserService
import services.StreamingBodyParser.streamingBodyParser

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

import ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Files(override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  case class WithProvider(provider: String) extends Authorization[User] {
    def isAuthorized(user: User, request: RequestHeader) = {
      user.userProfile.providerId == provider
    }
  }

  def HasEnoughSpace(action: EssentialAction): EssentialAction =  EssentialAction { request =>
    val authUser:Option[Authenticator[User]] = Await.result(env.authenticatorService.fromRequest(request), 10 seconds)

    val valid: Boolean = authUser match {
      case Some(auth) => {
        val updatedAuth = Await.result(authUser.get.touch, 10 seconds)
        val user = updatedAuth.user
        val fileSize = request.headers.get(play.api.http.HeaderNames.CONTENT_LENGTH)
        val userService = new PigionUserService()
        val maybeUsage = Await.result(userService.getUserUsage(user), 10 seconds)
        maybeUsage match {
          case Some(usage) =>
            val userCurrentUploads: Long = usage.currentActiveUploads.getOrElse(0)
            val userCurrentUploadBytes: Long = usage.currentActiveUploadBytes.getOrElse(0)
            val filePutsUserOverSizeLimit: Boolean = Integer.parseInt(fileSize.get) + userCurrentUploadBytes > user.userLevel.maxActiveUploadBytes
            val filePutsUserOverUploadLimit: Boolean = (userCurrentUploads + 1) > user.userLevel.maxActiveUploads

            if (!filePutsUserOverSizeLimit && !filePutsUserOverUploadLimit) {
              Logger.info(s"User id is ${user.userProfile.userId}")
              true
            } else {
              false
            }
          case _ => false
        }
      }
      case _  => {
        false
      }
    }

    if(valid) {
      action(request)
    } else {
      Done(BadRequest("User limit reached. Delete a file to free up some space for this upload."))
    }
  }

  def streamConstructor(filename: String) = {
    Option(new MultipartUploadHandler(filename))
  }

  def upload = HasEnoughSpace {
    SecuredAction(streamingBodyParser(streamConstructor)) { request =>
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


  def addPassword(fileId: Long, password: String) = SecuredAction { implicit request =>
    val destination: Option[Destination] = Destination.getFileById(fileId.toInt)

    destination match {
      case Some(d) => {
        if(d.userSeqId != request.user.userSeqId) {
          BadRequest
        }
        Destination.setPasswordForDestination(fileId, password)
        Ok
      }
      case _ => NotFound
    }
  }
  def expireFile(fileId: Int) = SecuredAction { implicit request =>
    val destination = Destination.getFileById(fileId)
    destination match {
      case Some(file) => {
        if(file.userSeqId == request.user.userSeqId) {
          Destination.expireFile(fileId)
          Ok
        } else {
          BadRequest(s"You don't own this file.")
        }
      }

      case _ => NotFound
    }
  }

  def deleteFile(fileId: Int) = SecuredAction { implicit request =>
    val destination = Destination.getFileById(fileId)
    destination match {
      case Some(file) => {
        if(file.userSeqId == request.user.userSeqId) {
          Destination.deleteFile(fileId)
          Ok
        } else {
          BadRequest(s"You don't own this file.")
        }
      }

      case _ => NotFound
    }
  }
}



