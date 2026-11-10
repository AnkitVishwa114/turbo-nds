package com.appkraft.turbonds.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview

/** Google Play services used by the app: the rating sheet, update checks and the store page. */
object PlayStore {
    /**
     * Shows Google's own rating sheet over the app. Play decides whether it actually appears and
     * never reports the stars, so callers only learn that the flow finished.
     */
    suspend fun requestReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.launchReview(activity, manager.requestReview())
    }

    /** The newer version code Play has for this install, or null (also when not installed from Play). */
    suspend fun availableUpdate(context: Context): Int? = runCatching {
        val info = AppUpdateManagerFactory.create(context).requestAppUpdateInfo()
        info.availableVersionCode().takeIf { info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE }
    }.getOrNull()

    /** Opens this app's page in the Play Store app, falling back to the web listing. */
    fun openListing(context: Context) {
        val id = context.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id"))
            .setPackage("com.android.vending")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
