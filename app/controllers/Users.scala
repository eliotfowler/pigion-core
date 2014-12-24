package controllers

import models.{Destination, User}
import securesocial.core.RuntimeEnvironment
import play.api.libs.json.{JsNull,Json,JsString,JsValue}

class Users(override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  def getFilesInfoForUser = SecuredAction { implicit request =>
    val user = request.user
    val numTotalUploads: Long = Destination.getNumFilesUploadedForUser(user.userSeqId)
    val numCurrentUploads: Long = Destination.getNumCurrentFilesUploadedForUser(user.userSeqId)
    val numDownloads: Long = Destination.getNumDownloadsFilesByUser(user.userSeqId)
    val totalSizeUploaded: Long = Destination.getTotalSizeUploadedForUser(user.userSeqId)
    val currentSizeUploaded: Long = Destination.getCurrentSizeUploadedForUser(user.userSeqId)
    Ok(Json.obj(
      "numTotalUploads" -> numTotalUploads,
      "numCurrentUploads" -> numCurrentUploads,
      "numDownloads" -> numDownloads,
      "totalSizeUploaded" -> totalSizeUploaded,
      "currentSizeUploaded" -> currentSizeUploaded)
    )
  }

  def getUserInfo = SecuredAction { implicit request =>
    Ok(Json.toJson(request.user))
  }

}
