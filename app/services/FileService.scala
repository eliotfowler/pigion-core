package services

import fly.play.s3._
import play.api.Play
import play.api.Play.current
import play.api.libs.MimeTypes

import scala.concurrent.{Awaitable, Await}
import scala.concurrent.duration._

trait FileService {

  //  def add(fileName: String, mimeType: String, content: Array[Byte], fileType: FileType): Validation[String, String]
  def initiateMultipartUpload(fileName: String): Option[BucketFileUploadTicket]
  def uploadPart(partNumber: Int, uploadTicket: BucketFileUploadTicket, content: Array[Byte]): Option[BucketFilePartUploadTicket]
  def completeMultipartUpload(uploadTicket: BucketFileUploadTicket, parts: List[BucketFilePartUploadTicket]): Unit
}

class FileServiceS3 extends FileService {
  //standard bucket
  val bucket = S3(Play.application.configuration.getString("aws.s3.bucket").get)

  /* The following functions are used by the MultipartUploadHandler class as a demo */
  def initiateMultipartUpload(fileName: String): Option[BucketFileUploadTicket] = {
    val fileContentType = MimeTypes.forFileName(fileName).getOrElse("application/octet-stream")
    val bucketFile = BucketFile(fileName, fileContentType)
    val future = bucket.initiateMultipartUpload(bucketFile)
    val value: BucketFileUploadTicket = await(future)
    println("initiating")
    Option(value)
  }

  def uploadPart(partNumber: Int, uploadTicket: BucketFileUploadTicket, content: Array[Byte]): Option[BucketFilePartUploadTicket] = {
    val filePart = BucketFilePart(partNumber, content)
    val future = bucket.uploadPart(uploadTicket, filePart)
    val value: BucketFilePartUploadTicket = await(future)
    println("uploading")
    Option(value)
  }

  def completeMultipartUpload(uploadTicket: BucketFileUploadTicket, parts: List[BucketFilePartUploadTicket]): Unit = {
    val future = bucket.completeMultipartUpload(uploadTicket, parts)
    println("finishing")
   await(future)
  }

  def await[T](a: Awaitable[T]): T = Await.result(a, Duration.Inf)
}

/**
 * FileType
 */
sealed trait FileType {

  def folder: String
  def acl: Option[ACL]
  def headers: Option[Map[String, String]] = None
}

case class ZIP(name: String) extends FileType {

  override val folder: String = {
    val buf = new StringBuilder ++= name ++= "/"
    buf.toString
  }
  override val acl: Option[ACL] = Some(AUTHENTICATED_READ)
}

case class File(name: String, contentType: String, content: Array[Byte])