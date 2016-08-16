package org.sfbike.util

import android.app.Activity
import android.content.DialogInterface
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import org.sfbike.log

object GooglePlayUtil {

    fun checkGooglePlayServicesAvailability(activity: Activity,
                                            onCancelListener: DialogInterface.OnCancelListener, onKeyListener: DialogInterface.OnKeyListener) {

        val result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity)
        when (result) {
            ConnectionResult.SERVICE_DISABLED, ConnectionResult.SERVICE_INVALID, ConnectionResult.SERVICE_MISSING, ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                log.d("Google Play Services: Raising dialog for user recoverable error " + result)
                val dialog = GooglePlayServicesUtil.getErrorDialog(result, activity, 0)
                dialog.setOnCancelListener(onCancelListener)
                dialog.setOnKeyListener(onKeyListener)
                dialog.show()
            }
            ConnectionResult.SUCCESS -> {
                // We are fine - proceed
                log.d("Google Play Services: Everything fine, proceeding")
            }
            else -> {
                // The rest are unrecoverable codes that developer configuration error or what have you
                throw RuntimeException("Google Play Services status code indicates unrecoverable error: " + result)
            }
        }
    }

}
