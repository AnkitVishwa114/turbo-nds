package com.appkraft.turbonds.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.appkraft.turbonds.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * When each ad may appear. Full-screen ads come only at natural breaks and are spaced out; the
 * rewarded "ad-free hour" is offered again every hour the user is not ad-free.
 */
object AdPolicy {
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    /** Minimum time between any two full-screen ads (interstitial or app open). */
    const val MIN_GAP_MS = 4 * MINUTE
    const val DAILY_CAP = 7
    /** Interstitials and app open ads together never pass this per day. */
    const val FULL_SCREEN_DAILY_CAP = 8
    const val AD_FREE_REWARD_MS = HOUR
    /** Only a game session at least this long earns an ad on the way back. */
    const val MIN_GAME_SESSION_MS = 2 * MINUTE

    /** The ad-free offer may come back this often (so once the hour runs out, it is offered again). */
    const val OFFER_GAP_MS = 30 * MINUTE
    const val OFFER_DAILY_CAP = 10

    /** The pre-game offer is stricter: only after an ad today, an hour apart, a few times a day. */
    const val PLAY_OFFER_GAP_MS = HOUR
    const val PLAY_OFFER_DAILY_CAP = 3

    /** App open ads: on a cold start, or when coming back after this long away. */
    const val APP_OPEN_AWAY_MS = 30 * MINUTE
    const val APP_OPEN_GAP_MS = HOUR
    const val APP_OPEN_DAILY_CAP = 3
    /** A loaded app open ad goes stale after four hours. */
    const val APP_OPEN_MAX_AGE_MS = 4 * HOUR

    fun interstitialDue(now: Long, lastFullScreen: Long, shownToday: Int, adFreeUntil: Long): Boolean =
        now >= adFreeUntil &&
            (lastFullScreen == 0L || now - lastFullScreen >= MIN_GAP_MS) &&
            shownToday < DAILY_CAP

    fun adFreeOfferDue(now: Long, adFreeUntil: Long, lastOffered: Long, offersToday: Int): Boolean =
        now >= adFreeUntil &&
            (lastOffered == 0L || now - lastOffered >= OFFER_GAP_MS) &&
            offersToday < OFFER_DAILY_CAP

    fun underFullScreenCap(interstitialsToday: Int, appOpensToday: Int): Boolean =
        interstitialsToday + appOpensToday < FULL_SCREEN_DAILY_CAP

    /** Before a game: only someone who has seen an ad today, at most hourly and three times a day. */
    fun playOfferDue(adsSeenToday: Int, now: Long, lastPlayOffer: Long, playOffersToday: Int): Boolean =
        adsSeenToday > 0 &&
            (lastPlayOffer == 0L || now - lastPlayOffer >= PLAY_OFFER_GAP_MS) &&
            playOffersToday < PLAY_OFFER_DAILY_CAP

    /**
     * Never on the very first launch. Otherwise on a cold start or a return after a real break,
     * spaced from other full-screen ads and capped per day.
     */
    fun appOpenDue(
        now: Long,
        launches: Int,
        coldStart: Boolean,
        awayMs: Long,
        lastAppOpen: Long,
        lastFullScreen: Long,
        shownToday: Int,
        adFreeUntil: Long
    ): Boolean =
        launches >= 2 &&
            now >= adFreeUntil &&
            (coldStart || awayMs >= APP_OPEN_AWAY_MS) &&
            (lastAppOpen == 0L || now - lastAppOpen >= APP_OPEN_GAP_MS) &&
            (lastFullScreen == 0L || now - lastFullScreen >= MIN_GAP_MS) &&
            shownToday < APP_OPEN_DAILY_CAP
}

/**
 * AdMob ads behind Google's consent form (UMP). Debug builds use Google's test ad units so
 * testing never counts as real traffic.
 */
object Ads {
    private val interstitialUnit = AdUnits.interstitial
    private val rewardedUnit = AdUnits.rewarded
    private val appOpenUnit = AdUnits.appOpen

    private var consent: ConsentInformation? = null
    /** True once consent allows ads and the SDK has been started. */
    var started = false
        private set
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var appOpen: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var loadingAppOpen = false
    private var showing = false
    /** Set once Google's rating sheet appeared; no full-screen ad for the rest of that session. */
    var quietSession = false

    /** True in regions where users must be able to change their ad consent from Settings. */
    var privacyOptionsRequired by mutableStateOf(false)
        private set
    var adFreeUntil by mutableLongStateOf(0L)
        private set
    /** The floating "go ad-free" card is up. */
    var offerAdFree by mutableStateOf(false)
        private set
    /** The card is up because the previous ad-free hour just ran out. */
    var offerIsRenewal by mutableStateOf(false)
        private set
    /** An app open ad is loaded and fresh; the launch screen waits briefly for it. */
    var appOpenReady by mutableStateOf(false)
        private set

    val isAdFree: Boolean get() = System.currentTimeMillis() < adFreeUntil
    val rewardedReady: Boolean get() = rewarded != null

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ads", Context.MODE_PRIVATE)

    /** Asks for consent where the law requires it, then starts the SDK. Call from the main activity. */
    fun gatherConsent(activity: Activity) {
        // No ads, no consent form: nothing is asked or collected while ads are off.
        if (AdUnits.notConfigured) return
        adFreeUntil = prefs(activity).getLong("ad_free_until", 0)
        val info = consent ?: UserMessagingPlatform.getConsentInformation(activity).also { consent = it }
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    privacyOptionsRequired = info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    if (info.canRequestAds()) start(activity)
                }
            },
            { if (info.canRequestAds()) start(activity) }
        )
        // A consent answer from an earlier launch lets ads start right away.
        if (info.canRequestAds()) start(activity)
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    private fun start(activity: Activity) {
        if (started || AdUnits.notConfigured) return
        started = true
        val app = activity.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(app) { }
            withContext(Dispatchers.Main) {
                loadAppOpen(app)
                loadInterstitial(app)
                loadRewarded(app)
                NativeAds.ensure(app)
            }
        }
    }

    // ---- Loading -------------------------------------------------------------------------

    private fun loadInterstitial(context: Context) {
        if (!started || interstitial != null || loadingInterstitial || interstitialUnit.isBlank()) return
        loadingInterstitial = true
        InterstitialAd.load(context, interstitialUnit, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                loadingInterstitial = false
                interstitial = ad
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                loadingInterstitial = false
            }
        })
    }

    private fun loadRewarded(context: Context) {
        if (!started || rewarded != null || loadingRewarded || rewardedUnit.isBlank()) return
        loadingRewarded = true
        RewardedAd.load(context, rewardedUnit, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                loadingRewarded = false
                rewarded = ad
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                loadingRewarded = false
            }
        })
    }

    private fun appOpenFresh(now: Long = System.currentTimeMillis()) =
        appOpen != null && now - appOpenLoadedAt < AdPolicy.APP_OPEN_MAX_AGE_MS

    private fun loadAppOpen(context: Context) {
        if (!started || appOpenFresh() || loadingAppOpen || appOpenUnit.isBlank()) return
        appOpen = null
        appOpenReady = false
        loadingAppOpen = true
        AppOpenAd.load(context, appOpenUnit, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                loadingAppOpen = false
                appOpen = ad
                appOpenLoadedAt = System.currentTimeMillis()
                appOpenReady = true
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                loadingAppOpen = false
            }
        })
    }

    // ---- Daily bookkeeping ----------------------------------------------------------------

    private fun dayKey(now: Long): Int = Calendar.getInstance().run {
        timeInMillis = now
        get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
    }

    /** Today's count for [key], resetting when the day changes. */
    private fun todayCount(context: Context, key: String, now: Long): Int {
        val store = prefs(context)
        return if (store.getInt("${key}_day", 0) == dayKey(now)) store.getInt("${key}_count", 0) else 0
    }

    private fun countToday(context: Context, key: String, now: Long) {
        prefs(context).edit()
            .putInt("${key}_day", dayKey(now))
            .putInt("${key}_count", todayCount(context, key, now) + 1)
            .apply()
    }

    private fun lastFullScreen(context: Context) = prefs(context).getLong("last_shown", 0)

    // ---- Interstitial ----------------------------------------------------------------------

    /** Shows an interstitial only if one is ready and the policy allows it; never waits for a load. */
    fun showInterstitialIfDue(activity: Activity) {
        val ad = interstitial
        if (ad == null) { loadInterstitial(activity.applicationContext); return }
        if (showing || quietSession) return
        val app = activity.applicationContext
        val now = System.currentTimeMillis()
        val interstitials = todayCount(app, "interstitial", now)
        if (!AdPolicy.interstitialDue(now, lastFullScreen(app), interstitials, adFreeUntil)) return
        if (!AdPolicy.underFullScreenCap(interstitials, todayCount(app, "app_open", now))) return
        interstitial = null
        showing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                finished(app)
                // Right after an ad is the moment the ad-free hour sounds best.
                requestAdFreeOffer(app)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) = finished(app)
        }
        prefs(app).edit().putLong("last_shown", now).apply()
        countToday(app, "interstitial", now)
        ad.show(activity)
    }

    // ---- App open --------------------------------------------------------------------------

    /** Whether an app open ad could be shown now; the launch screen waits for one only then. */
    fun appOpenWanted(context: Context, launches: Int, coldStart: Boolean, awayMs: Long): Boolean {
        if (!started && consent?.canRequestAds() != true) return false
        if (showing || quietSession) return false
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val appOpens = todayCount(app, "app_open", now)
        return AdPolicy.underFullScreenCap(todayCount(app, "interstitial", now), appOpens) &&
            AdPolicy.appOpenDue(
                now, launches, coldStart, awayMs,
                prefs(app).getLong("last_app_open", 0), lastFullScreen(app),
                appOpens, adFreeUntil
            )
    }

    /** Shows the app open ad if one is loaded and due. Returns true when it was shown. */
    fun showAppOpenIfDue(activity: Activity, launches: Int, coldStart: Boolean, awayMs: Long): Boolean {
        val app = activity.applicationContext
        if (!appOpenFresh()) { loadAppOpen(app); return false }
        if (!appOpenWanted(app, launches, coldStart, awayMs)) return false
        val ad = appOpen ?: return false
        appOpen = null
        appOpenReady = false
        showing = true
        val now = System.currentTimeMillis()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finished(app)
            override fun onAdFailedToShowFullScreenContent(error: AdError) = finished(app)
        }
        prefs(app).edit().putLong("last_shown", now).putLong("last_app_open", now).apply()
        countToday(app, "app_open", now)
        ad.show(activity)
        return true
    }

    // ---- Rewarded: the ad-free hour ---------------------------------------------------------

    /** Whether the ad-free hour can be offered right now (a video is ready and the offer is due). */
    fun canOfferAdFree(context: Context): Boolean {
        if (!started || rewarded == null || showing) return false
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        return AdPolicy.adFreeOfferDue(now, adFreeUntil, prefs(app).getLong("last_offer", 0), todayCount(app, "offer", now))
    }

    /** Whether to ask "Play without ads?" before a game starts (stricter than the other offers). */
    fun canOfferAdFreeBeforePlay(context: Context): Boolean {
        if (!canOfferAdFree(context)) return false
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val adsSeenToday = todayCount(app, "interstitial", now) + todayCount(app, "app_open", now)
        return AdPolicy.playOfferDue(adsSeenToday, now, prefs(app).getLong("last_play_offer", 0), todayCount(app, "play_offer", now))
    }

    /** Called when the pre-game offer appeared; it also counts as an ad-free offer. */
    fun playOfferShown(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        prefs(app).edit().putLong("last_play_offer", now).apply()
        countToday(app, "play_offer", now)
        adFreeOfferShown(app)
    }

    /** Puts the floating offer card up when the offer is due. [renewal] when the last hour just ended. */
    fun requestAdFreeOffer(context: Context, renewal: Boolean = false) {
        if (offerAdFree) return
        if (!canOfferAdFree(context)) { loadRewarded(context.applicationContext); return }
        offerIsRenewal = renewal
        offerAdFree = true
    }

    /** Called when an offer (card or pre-game dialog) actually appeared on screen. */
    fun adFreeOfferShown(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        prefs(app).edit().putLong("last_offer", now).apply()
        countToday(app, "offer", now)
    }

    fun dismissAdFreeOffer() {
        offerAdFree = false
    }

    /** Plays a rewarded video; a completed view turns off full-screen and list ads for an hour. */
    fun showAdFreeReward(activity: Activity, onResult: (Boolean) -> Unit) {
        offerAdFree = false
        val ad = rewarded
        if (ad == null || showing) {
            loadRewarded(activity.applicationContext)
            onResult(false)
            return
        }
        rewarded = null
        showing = true
        var earned = false
        val app = activity.applicationContext
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                finished(app)
                onResult(earned)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                finished(app)
                onResult(false)
            }
        }
        ad.show(activity) {
            earned = true
            // Stacks on any time still left, so watching early never wastes the current hour.
            val until = maxOf(System.currentTimeMillis(), adFreeUntil) + AdPolicy.AD_FREE_REWARD_MS
            adFreeUntil = until
            prefs(app).edit().putLong("ad_free_until", until).apply()
        }
    }

    private fun finished(context: Context) {
        showing = false
        loadInterstitial(context)
        loadRewarded(context)
        loadAppOpen(context)
    }
}
