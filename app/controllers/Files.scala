package controllers

import play.api.mvc.{Action, Controller}
import fly.play.s3._
import models.{Destination, FlowData}
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
import play.api.mvc.BodyParsers.parse.Multipart.FileInfo
import fly.play.s3.BucketFilePartUploadTicket
import fly.play.s3.BucketFilePart
import play.api.mvc.MultipartFormData.FilePart
import fly.play.s3.BucketFile
import java.net.URLEncoder
import play.Logger

object Files extends Controller {

  var partUploadTickets: Seq[BucketFilePartUploadTicket] = Seq()

  def upload = Action(multipartFormDataAsBytes) { request =>
    System.out.println("here")
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
    val fileName = flowData.flowFileName
    val fileContentType = MimeTypes.forFileName(flowData.flowFileName).getOrElse(".txt")
    val bucketFile = BucketFile(URLEncoder.encode(fileName, "UTF-8"), fileContentType)

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

    val uploadResult = await(bucket completeMultipartUpload (uploadTicket, partUploadTickets))

    // We only want to create a URL from the last one
    if(flowData.flowChunkNumber == flowData.flowTotalChunks) {
      val url = bucket.url(URLEncoder.encode(flowData.flowFileName, "UTF-8"))
      Destination.create(url)
      Ok(Destination.create(url))
    } else {
      Ok("uploading")
    }
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

  def await[T](a: Awaitable[T]): T = Await.result(a, Duration.Inf)

  def notifyOfUpload = Action { implicit request =>

    Ok("")
  }
}
