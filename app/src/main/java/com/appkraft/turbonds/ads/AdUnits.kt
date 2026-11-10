package com.appkraft.turbonds.ads

import com.appkraft.turbonds.BuildConfig

/**
 * Ad unit IDs. Debug builds always use Google's test units. Release IDs must come from the
 * Turbo NDS app in AdMob (never another app's units); while a release ID is blank that format
 * is simply not loaded, so a release build can never show test ads.
 */
object AdUnits {
    /** Ads are switched off for now (no SDK start, no consent form, no ad cards). Set true to bring them back. */
    const val ENABLED = false

    private const val RELEASE_INTERSTITIAL = ""
    private const val RELEASE_REWARDED = ""
    private const val RELEASE_APP_OPEN = ""
    private const val RELEASE_NATIVE = ""

    val interstitial = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/1033173712" else RELEASE_INTERSTITIAL
    val rewarded = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/5224354917" else RELEASE_REWARDED
    val appOpen = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/9257395921" else RELEASE_APP_OPEN
    val native = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/2247696110" else RELEASE_NATIVE

    /** True while ads are off or the Turbo NDS AdMob units are not filled in: the ads SDK is then not started at all. */
    val notConfigured: Boolean get() = !ENABLED || listOf(interstitial, rewarded, appOpen, native).all { it.isBlank() }
}
