package controllers

import models.{Destination, User}
import securesocial.core.RuntimeEnvironment
import play.api.libs.json.{JsNull,Json,JsString,JsValue}

class Users(override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  def getFilesInfoForUser = SecuredAction { implicit request =>
    val user = request.user
    val numTotalUploads = Destination.getNumFilesUploadedForUser(user.userSeqId)
    val numCurrentUploads = Destination.getNumCurrentFilesUploadedForUser(user.userSeqId)
    val numDownloads = Destination.getNumDownloadsFilesByUser(user.userSeqId)
    val totalSizeUploaded = Destination.getTotalSizeUploadedForUser(user.userSeqId)
    val currentSizeUploaded = Destination.getCurrentSizeUploadedForUser(user.userSeqId)
    Ok(Json.obj(
      "numTotalUploads" -> numTotalUploads,
        "numCurrentUploads" -> numCurrentUploads,
        "numDownloads" -> numDownloads,
        "totalSizeUploaded" -> totalSizeUploaded,
        "currentSizeUploaded" -> currentSizeUploaded)
    )
  }

}
