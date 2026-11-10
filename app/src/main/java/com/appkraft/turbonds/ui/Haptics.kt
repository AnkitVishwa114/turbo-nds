package com.appkraft.turbonds.ui

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Touch vibration that works the same on every phone. View haptics are skipped by many OEM skins
 * and by the system "touch feedback" switch, so this drives the vibrator directly: predefined
 * tick/click effects where the motor supports them (Android 10+), short one-shots everywhere else.
 * Silent no-op on devices without a vibrator. VIBRATE is a normal permission, so nothing is asked.
 */
object Haptics {
    private const val FILE = "app_settings"
    private const val KEY = "haptics"
    private var vibrator: Vibrator? = null
    private var lastAt = 0L

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (vibrator == null) {
            val app = context.applicationContext
            vibrator = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION") app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
            }.getOrNull()
        }
        enabled = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        if (value) click()
    }

    /** Light tick for selection changes: tabs, chips, toggles, list taps. */
    fun tick() = play(Strength.TICK)

    /** Firmer click for actions: buttons, play, download. */
    fun click() = play(Strength.CLICK)

    /** Heavy click for important moments such as starting a game. */
    fun heavy() = play(Strength.HEAVY)

    /** In-game buttons: always crisp, follows the player's own vibration switch instead of the app one. */
    fun gameTap() = play(Strength.GAME, force = true)

    /**
     * Explicit short pulses rather than the system "tick" effect: on many phones (Xiaomi, Oppo,
     * Vivo) the predefined tick is so weak, or so often unsupported, that taps felt dead.
     */
    private enum class Strength(val millis: Long, val amplitude: Int) {
        TICK(14, 110),
        GAME(18, 170),
        CLICK(20, 170),
        HEAVY(32, 235)
    }

    /** Game usage follows the phone's media/game vibration setting, which is on by default. */
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    private fun play(strength: Strength, force: Boolean = false) {
        if (!enabled && !force) return
        val motor = vibrator ?: return
        // Rapid scrolling through chips can fire many events; keep them from smearing into a buzz.
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < 30) return
        lastAt = now
        runCatching {
            if (!motor.hasVibrator()) return
            val amplitude = if (motor.hasAmplitudeControl()) strength.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            @Suppress("DEPRECATION")
            motor.vibrate(VibrationEffect.createOneShot(strength.millis, amplitude), attributes)
        }
    }

    /** Lets every existing `LocalHapticFeedback` call in the UI use this vibrator. */
    val feedback = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            when (hapticFeedbackType) {
                HapticFeedbackType.LongPress, HapticFeedbackType.Confirm -> click()
                HapticFeedbackType.Reject -> heavy()
                else -> tick()
            }
        }
    }
}
