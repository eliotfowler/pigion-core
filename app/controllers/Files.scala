package controllers

import play.api.mvc.Controller
import fly.play.s3._
import models.{User, Destination, FlowData}
import play.api.mvc.BodyParsers.parse.Multipart.PartHandler
import play.api.mvc.BodyParsers.parse.Multipart.handleFilePart
import play.api.mvc.BodyParsers.parse.multipartFormData
import play.api.libs.iteratee.Iteratee
import java.io.ByteArrayOutputStream
import play.api.mvc.MultipartFormData
import play.api.libs.MimeTypes
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.Awaitable
import scala.concurrent.duration.Duration
import play.api.mvc.BodyParsers.parse.Multipart.FileInfo
import fly.play.s3.BucketFilePartUploadTicket
import fly.play.s3.BucketFilePart
import play.api.mvc.MultipartFormData.FilePart
import fly.play.s3.BucketFile
import java.net.URLEncoder
import securesocial.core._

object Files extends Controller with SecureSocial {

  case class WithProvider(provider: String) extends Authorization {
    def isAuthorized(user: Identity) = {
      user.identityId.providerId == provider
    }
  }

  var partUploadTickets: Seq[BucketFilePartUploadTicket] = Seq()

  def upload = SecuredAction(multipartFormDataAsBytes) { request =>
    // First get the user
    val user: Option[User] = User.find(request.user.identityId)

    // Turns Map(String, Seq[String]) into Map(String, String)
    val body = request.body.dataParts.map { case (k,Seq(v)) => (k,v) }

    // Map the incoming request to a FlowData object
    val flowData = FlowData(body.get("flowChunkNumber").get.toLong,
      body.get("flowChunkSize").get.toLong,
      body.get("flowCurrentChunkSize").get.toLong,
      body.get("flowTotalSize").get.toLong,
      body.get("flowTotalChunks").get.toLong,
      body.get("flowIdentifier").get,
      body.get("flowFilename").get)

    // Get the s3 bucket
    val bucket = S3("pigion")

    // Now we need to figure out if this is the beginning of an upload, an in progress upload, or the end of an upload
    val fileName = URLEncoder.encode(request.user.identityId + "/" + flowData.flowFileName, "UTF-8")
    val fileContentType = MimeTypes.forFileName(flowData.flowFileName.toLowerCase).getOrElse("application/octet-stream")
    val bucketFile = BucketFile(fileName, fileContentType)

    val uploadTicket = await(bucket.initiateMultipartUpload(bucketFile))

    // For each of the file parts, send them to the S3
    request.body.files foreach {
      case FilePart(key, filename, contentType, bytes) => {
        // Upload the data
        val filePart = BucketFilePart(flowData.flowChunkNumber.toInt, bytes)
        val partUploadTicket = await(bucket.uploadPart(uploadTicket, filePart))
        partUploadTickets = Seq(partUploadTicket)
      }
    }

    await(bucket completeMultipartUpload (uploadTicket, partUploadTickets))

    // We only want to create a URL from the last one
    if(flowData.flowChunkNumber == flowData.flowTotalChunks) {
      val url = bucket.url(fileName)
      val seqId = user match {
        case Some(u) => u.seqId
        case _ => -1
      }
      Ok(Destination.create(url,flowData.flowFileName, fileContentType, seqId))
    } else {
      Ok
    }
   }

  def handleFilePartAsByteArray: PartHandler[FilePart[Array[Byte]]] =
    handleFilePart {
      case FileInfo(partName, filename, contentType) =>
        // simply write the data to the ByteArrayOutputStream
        Iteratee.fold[Array[Byte], ByteArrayOutputStream](
          new ByteArrayOutputStream()) { (os, data) =>
          os.write(data)
          os
        }.map { os =>
          os.close()
          os.toByteArray
        }
    }

  def multipartFormDataAsBytes:play.api.mvc.BodyParser[MultipartFormData[Array[Byte]]] = multipartFormData(handleFilePartAsByteArray)

  def await[T](a: Awaitable[T]): T = Await.result(a, Duration.Inf)
}
