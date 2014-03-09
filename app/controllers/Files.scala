package controllers

import play.api.mvc.{Action, Controller}
import fly.play.s3.{BucketFilePartUploadTicket, BucketFilePart, BucketFile, S3}
import models.FlowData
import play.api.mvc.BodyParsers.parse.Multipart.PartHandler
import play.api.mvc.BodyParsers.parse.Multipart.handleFilePart
import play.api.mvc.BodyParsers.parse.Multipart.FileInfo
import play.api.mvc.BodyParsers.parse.multipartFormData
import play.api.mvc.MultipartFormData.FilePart
import play.api.libs.iteratee.Iteratee
import java.io.ByteArrayOutputStream
import play.api.mvc.MultipartFormData
import play.api.libs.MimeTypes
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.Awaitable
import scala.concurrent.Future
import scala.concurrent.duration.Duration

object Files extends Controller {

  var partUploadTickets: Seq[BucketFilePartUploadTicket] = Seq()

  def upload = Action(multipartFormDataAsBytes) { request =>
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
    val fileName = flowData.flowIdentifier
    val fileContentType = MimeTypes.forFileName(flowData.flowFileName).get
    val bucketFile = BucketFile(fileName, fileContentType)

    val uploadTicket = await(bucket.initiateMultipartUpload(bucketFile))

    // For each of the file parts, send them to the S3
    request.body.files foreach {
      case FilePart(key, filename, contentType, bytes) => {
        // Upload the data
        val filePart = BucketFilePart(flowData.flowChunkNumber.toInt, bytes)
        val partUploadTicket = await(bucket.uploadPart(uploadTicket, filePart))
//        partUploadTickets :+ partUploadTicket
        partUploadTickets = Seq(partUploadTicket)
      }
    }

    val result = await(bucket completeMultipartUpload (uploadTicket, partUploadTickets))

    val file = await(bucket get fileName)

    Ok("")
  }

  def sendStartRequest = TODO

  def sendFinishedRequest = TODO

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

  def fileUploader = Action(multipartFormDataAsBytes) { request =>
    request.body.files foreach {
      case FilePart(key, filename, contentType, bytes) => // do something
    }
    Ok("done")
  }

  def await[T](a: Awaitable[T]): T =
    Await.result(a, Duration.Inf)
}
