package models

import play.api.libs.MimeTypes
import fly.play.s3._
import scala.concurrent.Future
import fly.play.s3.Bucket
import fly.play.s3.BucketFileUploadTicket
import fly.play.s3.BucketFile

case class FlowData(flowChunkNumber: Long, flowChunkSize: Long, flowCurrentChunkSize: Long,
                    flowTotalSize: Long, flowTotalChunks: Long, flowIdentifier: String, flowFileName: String)

object FlowData {
  def sendAsInitialRequestToS3(flowData: FlowData, bucket: Bucket):Future[BucketFileUploadTicket] = {
    bucket initiateMultipartUpload BucketFile(flowData.flowFileName, MimeTypes.forFileName(flowData.flowFileName).get)
  }

  def uploadPartToS3(flowData: FlowData, bucket: Bucket, uploadTicket :BucketFileUploadTicket):Future[BucketFilePartUploadTicket] = {
//    bucket uploadPart (uploadTicket, BucketFilePart(flowData.flowChunkNumber.toInt, ))
    null
  }


}