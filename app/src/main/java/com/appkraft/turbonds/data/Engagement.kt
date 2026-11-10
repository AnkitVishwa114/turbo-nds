package com.appkraft.turbonds.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle

/** How much someone has used the app; decides when the Play rating sheet is worth asking for. */
data class Usage(
    val firstOpen: Long = 0,
    val launches: Int = 0,
    val foregroundMs: Long = 0,
    val gamesPlayed: Int = 0,
    val downloads: Int = 0,
    val lastReviewAsk: Long = 0,
    val reviewAsks: Int = 0
)

object ReviewPolicy {
    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60 * MINUTE
    const val MIN_GAME_SESSION_MS = MINUTE

    /**
     * Ask only someone who came back at least once, spent real time here and finished a few
     * plays or downloads. Google also rations the sheet, so keep asks rare and capped.
     */
    fun due(usage: Usage, now: Long): Boolean =
        usage.reviewAsks < 3 &&
            (usage.lastReviewAsk == 0L || now - usage.lastReviewAsk >= 30 * DAY) &&
            usage.launches >= 2 &&
            usage.foregroundMs >= 5 * MINUTE &&
            usage.gamesPlayed + usage.downloads >= 2
}

object Engagement {
    private const val MAX_SESSION_SLICE_MS = 30 * 60 * 1000L

    private var prefs: android.content.SharedPreferences? = null
    private var startedActivities = 0
    private var visibleSince = 0L
    private var playerOpenedAt = 0L
    private var backgroundSince = 0L

    /**
     * Called when the app comes back to the foreground after every screen was hidden, with the
     * activity that is starting and how long the app was away.
     */
    var onReturn: ((Activity, Long) -> Unit)? = null

    /** Counts this launch and tracks time while any of the app's screens (player included) is visible. */
    fun install(application: Application) {
        if (prefs != null) return
        val store = application.getSharedPreferences("engagement", Context.MODE_PRIVATE)
        prefs = store
        val now = System.currentTimeMillis()
        store.edit()
            .putLong("first_open", store.getLong("first_open", 0).takeIf { it > 0 } ?: now)
            .putInt("launches", store.getInt("launches", 0) + 1)
            .apply()
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) {
                    val now = System.currentTimeMillis()
                    visibleSince = now
                    if (backgroundSince > 0) {
                        val away = now - backgroundSince
                        backgroundSince = 0
                        onReturn?.invoke(activity, away)
                    }
                }
            }
            override fun onActivityStopped(activity: Activity) {
                if (--startedActivities <= 0) {
                    startedActivities = 0
                    bankVisibleTime()
                    visibleSince = 0
                    // A rotation or theme change stops and restarts at once; only a real exit counts.
                    if (!activity.isChangingConfigurations) backgroundSince = System.currentTimeMillis()
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun usage(now: Long = System.currentTimeMillis()): Usage {
        val store = prefs ?: return Usage()
        val live = if (visibleSince > 0) (now - visibleSince).coerceIn(0, MAX_SESSION_SLICE_MS) else 0
        return Usage(
            firstOpen = store.getLong("first_open", now),
            launches = store.getInt("launches", 0),
            foregroundMs = store.getLong("foreground_ms", 0) + live,
            gamesPlayed = store.getInt("games_played", 0),
            downloads = store.getInt("downloads", 0),
            lastReviewAsk = store.getLong("last_review_ask", 0),
            reviewAsks = store.getInt("review_asks", 0)
        )
    }

    fun reviewDue(): Boolean = ReviewPolicy.due(usage(), System.currentTimeMillis())

    fun reviewAsked() {
        val store = prefs ?: return
        store.edit().putLong("last_review_ask", System.currentTimeMillis()).putInt("review_asks", store.getInt("review_asks", 0) + 1).apply()
    }

    fun downloadStarted() = increment("downloads")

    fun playerOpened() {
        playerOpenedAt = System.currentTimeMillis()
    }

    /** Called when the main screen is back; how long the game session that just ended lasted (0 if none). */
    fun playerClosed(): Long {
        if (playerOpenedAt == 0L) return 0
        val played = System.currentTimeMillis() - playerOpenedAt
        playerOpenedAt = 0
        if (played >= ReviewPolicy.MIN_GAME_SESSION_MS) increment("games_played")
        return played
    }

    private fun increment(key: String) {
        val store = prefs ?: return
        store.edit().putInt(key, store.getInt(key, 0) + 1).apply()
    }

    private fun bankVisibleTime() {
        val store = prefs ?: return
        if (visibleSince == 0L) return
        val slice = (System.currentTimeMillis() - visibleSince).coerceIn(0, MAX_SESSION_SLICE_MS)
        store.edit().putLong("foreground_ms", store.getLong("foreground_ms", 0) + slice).apply()
    }
}
