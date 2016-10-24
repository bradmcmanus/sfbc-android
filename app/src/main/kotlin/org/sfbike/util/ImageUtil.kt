package org.sfbike.util

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.InputStream

object ImageUtil {

    fun uriFromIntent(intent: Intent): Uri? {
        fun handleSendImage(intent: Intent): Uri? {
            try {
                val uri: Uri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                return uri

            } catch (e: Exception) {
                log("unable to load image")
            }
            return null
        }

        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if (type.startsWith("image/")) {
               return handleSendImage(intent)
            }
        }
        return null
    }

    fun latLngFromUri(context: Context, uri: Uri?): LatLng? {
        if (uri == null) {
            return null
        }

        val path: String
        if (uri.scheme == "content") {
            path = getRealPathFromURI(context, uri)
        } else {
            path = File(uri.path).name
        }

        val exif = ExifInterface(path)
        val latlng = floatArrayOf(-200.0f, -200.0f)
        exif.getLatLong(latlng)

        if (GeoUtil.isValidGpsCoordinate(latlng[0].toDouble(), latlng[1].toDouble())) {
            return LatLng(latlng[0].toDouble(), latlng[1].toDouble())
        } else {
            return null
        }
    }

    /**
     * Gets the real path from file
     * @param context
     * *
     * @param contentUri
     * *
     * @return path
     */
    fun getRealPathFromURI(context: Context, contentUri: Uri): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, contentUri)) {
            return getPathForV19AndUp(context, contentUri)
        } else {
            return getPathForPreV19(context, contentUri)
        }
    }

    /**
     * Handles pre V19 uri's
     * @param context
     * *
     * @param contentUri
     * *
     * @return
     */
    fun getPathForPreV19(context: Context, contentUri: Uri): String {
        var res: String = ""

        val proj = arrayOf<String>(MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(contentUri, proj, null, null, null)
        if (cursor.moveToFirst()) {
            val column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            res = cursor.getString(column_index)
        }
        cursor.close()

        return res
    }

    /**
     * Handles V19 and up uri's
     * @param context
     * *
     * @param contentUri
     * *
     * @return path
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun getPathForV19AndUp(context: Context, contentUri: Uri): String {
        val wholeID = DocumentsContract.getDocumentId(contentUri)

        // Split at colon, use second item in the array
        val id = wholeID.split(":".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()[1]
        val column = arrayOf<String>(MediaStore.Images.Media.DATA)

        // where id is equal to
        val sel = MediaStore.Images.Media._ID + "=?"
        val cursor = context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                column, sel, arrayOf<String>(id), null)

        var filePath = ""
        val columnIndex = cursor.getColumnIndex(column[0])
        if (cursor.moveToFirst()) {
            filePath = cursor.getString(columnIndex)
        }

        cursor.close()
        return filePath
    }

}

fun File.copyInputStreamToFile(inputStream: InputStream) {
    inputStream.use { input ->
        this.outputStream().use { fileOut ->
            input.copyTo(fileOut)
        }
    }
}
