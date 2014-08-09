package models

import services.FileServiceS3
import fly.play.s3.{BucketFilePartUploadTicket, BucketFileUploadTicket}
import java.io.OutputStream

class MultipartUploadHandler(fileName: String) extends OutputStream {

  def name: String = fileName;
  var fileService = new FileServiceS3();
  /* The number for pieces the file is broken into, needed while uploading to S3 */
  var part: Int = 1
  /* upLoad maintains which file is being uploaded, issued by S3 */
  val uploadTicket: Option[BucketFileUploadTicket]= fileService.initiateMultipartUpload(name)
  /* Contains the ETag for each part along with its number for the CompleteMultipartUpload call */
  var parts: List[BucketFilePartUploadTicket] = List()
  /* Each part must be atleast 5MB before it can be uploaded (restriction by S3), so cached before uploading */
  var cache: Array[Byte] = Array()

  private def performUpload = {
    parts = parts :+ fileService.uploadPart(part, uploadTicket.get, cache).get
  }

  override def write(data: Array[Byte]) = {
    if(cache.length >= 5242880) {
      performUpload
      part += 1
      cache = Array()
    }

    cache = cache ++ data
  }

  override def write(data: Int) = {
    cache :+ data
  }

  override def close = {
    if (cache.length > 0) performUpload
    fileService.completeMultipartUpload(uploadTicket.get, parts)
  }

}
