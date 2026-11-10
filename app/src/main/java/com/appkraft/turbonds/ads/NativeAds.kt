package com.appkraft.turbonds.ads

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.appkraft.turbonds.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

/**
 * A small pool of native ads shown as cards inside the game lists. Ads are refreshed after an
 * hour (AdMob's guidance for holding native ads) and hidden while the user is ad-free.
 */
object NativeAds {
    private val unit = AdUnits.native
    private const val POOL_SIZE = 3
    private const val MAX_AGE_MS = AdPolicy.HOUR

    /** Loaded ads, read by the list cards. */
    val pool = mutableStateListOf<NativeAd>()
    private var loadedAt = 0L
    private var loading = false
    private var freshBatch = false

    /** Loads (or refreshes) the pool when it is empty or stale. Safe to call often. */
    fun ensure(context: Context) {
        if (!Ads.started || loading || unit.isBlank()) return
        val now = System.currentTimeMillis()
        if (pool.isNotEmpty() && now - loadedAt < MAX_AGE_MS) return
        loading = true
        freshBatch = true
        val loader = AdLoader.Builder(context.applicationContext, unit)
            .forNativeAd { ad ->
                // The first ad of a new batch replaces the stale ones.
                if (freshBatch) {
                    freshBatch = false
                    pool.forEach { it.destroy() }
                    pool.clear()
                    loadedAt = System.currentTimeMillis()
                }
                pool.add(ad)
                // A fresh ad marks the pool as current, so ensure() will not start another batch.
                loading = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                }
                override fun onAdLoaded() {
                    loading = false
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setRequestMultipleImages(false)
                    .build()
            )
            .build()
        loader.loadAds(AdRequest.Builder().build(), POOL_SIZE)
    }

    /** The ad for the n-th card on a screen, or null when none should show. */
    fun forSlot(slot: Int): NativeAd? {
        if (Ads.isAdFree || pool.isEmpty()) return null
        return pool[slot % pool.size]
    }
}
