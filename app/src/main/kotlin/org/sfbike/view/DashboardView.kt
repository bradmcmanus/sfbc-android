package org.sfbike.view

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.util.AttributeSet
import android.view.View
import android.widget.*
import org.sfbike.R
import org.sfbike.data.District
import org.sfbike.data.Station
import org.sfbike.util.bindView

class DashboardView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    val thumbIv: ImageView by bindView(R.id.thumb_iv)

    val districtTv: TextView by bindView(R.id.district_tv)
    val stationTv: TextView by bindView(R.id.station_tv)

    val tweetStationBtn: Button by bindView(R.id.tweet_station_button)
    val tweetSupeBtn: Button by bindView(R.id.tweet_supe_button)
    val callStationBtn: Button by bindView(R.id.call_station_button)
    val callSupeBtn: Button by bindView(R.id.call_supe_button)
    val emailStationBtn: Button by bindView(R.id.email_station_button)
    val emailSupeBtn: Button by bindView(R.id.email_supe_button)

    var district: District? = null
    var station: Station? = null
    var imageUri: Uri? = null

    init {
        View.inflate(context, R.layout.view_dashboard, this)
        orientation = LinearLayout.VERTICAL
        isClickable = true
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        tweetSupeBtn.setOnClickListener { tweetAt(district!!.twitter) }
        tweetStationBtn.setOnClickListener { tweetAt(station!!.twitter) }
        callStationBtn.setOnClickListener { launchDialer(station!!.phone_number) }
        callSupeBtn.setOnClickListener { launchDialer(district!!.phone) }
        emailStationBtn.setOnClickListener { launchEmail(station!!) }
        emailSupeBtn.setOnClickListener { launchEmail(district!!) }
    }

    fun bindDistrict(district: District?) {
        this.district = district
        districtTv.text = "District ${district?.id}\n${district?.name}"
    }

    fun bindStation(station: Station?) {
        this.station = station
        stationTv.text = "${station?.name}\nStation"
    }

    fun bindImage(uri: Uri?) {
        thumbIv.setImageURI(uri)
    }

    //region Helpers

    private fun launchEmail(station: Station) {
        val email = station.email
        val subject = "Feedback about neighborhood in ${station.name} Station"
        val body = "Hello,\n\n"
        launchEmail(email, subject, body)
    }

    private fun launchEmail(district: District) {
        val email = district.email
        val subject = "My Concerns about District ${district.id}"
        val body = "Hello,\n\n"
        launchEmail(email, subject, body)
    }

    private fun launchEmail(email: String, subject: String, body: String) {
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
        emailIntent.putExtra(Intent.EXTRA_TEXT, body)
        context.startActivity(Intent.createChooser(emailIntent, "Send email..."))
    }

    @Suppress("DEPRECATION")
    private fun launchDialer(number: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:${PhoneNumberUtils.formatNumber(number)}")
        context.startActivity(intent)
    }

    private fun launchTwitter(handle: String) {
        val intent: Intent?
        try {
            // get the Twitter app if possible
            context.packageManager.getPackageInfo("com.twitter.android", 0)
            intent = Intent(Intent.ACTION_VIEW, Uri.parse("twitter://user?user_id=$handle"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } catch (e: Exception) {
            // no Twitter app, revert to browser
            intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/$handle"))
        }

        context.startActivity(intent)
    }

    private fun tweetAt(handle: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_TEXT, "Hey @$handle ")
        if (imageUri is Uri) intent.putExtra(Intent.EXTRA_STREAM, imageUri!!)

        val packManager = context.packageManager
        val resolvedInfoList = packManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        var resolved = false
        for (resolveInfo in resolvedInfoList) {
            if (resolveInfo.activityInfo.packageName.startsWith("com.twitter.android")) {
                intent.setClassName(resolveInfo.activityInfo.packageName, "com.twitter.android.composer.ComposerActivity")
                resolved = true
                break
            }
        }
        if (resolved) {
            context.startActivity(intent)
        } else {
            Toast.makeText(this.context, "Twitter app isn't found", Toast.LENGTH_LONG).show()
        }
    }

    //endregion

}
