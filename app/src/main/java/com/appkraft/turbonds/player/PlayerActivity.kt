package com.appkraft.turbonds.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.appkraft.turbonds.library.SaveStore
import com.appkraft.turbonds.ui.Accent
import com.appkraft.turbonds.ui.AppColors
import com.appkraft.turbonds.ui.Coral
import com.appkraft.turbonds.ui.DarkPalette
import com.appkraft.turbonds.ui.Haptics
import com.appkraft.turbonds.ui.OnAccent
import com.appkraft.turbonds.ui.PlatformPill
import com.appkraft.turbonds.ui.PrimaryButton
import com.appkraft.turbonds.ui.Raised
import com.appkraft.turbonds.ui.SecondaryButton
import com.appkraft.turbonds.ui.TextFaint
import com.appkraft.turbonds.ui.TextMuted
import com.appkraft.turbonds.ui.TextPrimary
import com.appkraft.turbonds.ui.TurboTheme
import com.appkraft.turbonds.ui.Stroke as StrokeColor
import com.appkraft.turbonds.ui.Surface as PanelColor
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The DS player. Lessons from the top DS apps' 1★ reviews are built in: no ads here at all,
 * saves written safely and often, a resume point on every exit or background, backups of the
 * in-game save before risky actions, true multi-touch buttons and layouts for both orientations.
 */
class PlayerActivity : ComponentActivity() {
    private var engine: GLRetroView? = null
    private val engineOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private lateinit var saves: File
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pressed = mutableSetOf<Int>()
    private var ready by mutableStateOf(false)
    private var menu by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var notice by mutableStateOf<String?>(null)
    private var resumed = false
    private var destroyed = false
    private var snapshotPending by mutableStateOf(false)
    private var pauseRequested = false
    private val preferences by lazy { getSharedPreferences("player", Context.MODE_PRIVATE) }
    private var scale by mutableFloatStateOf(1f)
    private var opacity by mutableFloatStateOf(0.65f)
    private var haptics by mutableStateOf(true)
    private var swapButtons by mutableStateOf(false)
    private var fastForward by mutableStateOf(false)
    private var ffSpeed by mutableIntStateOf(2)
    private var muteFastForward by mutableStateOf(true)
    private var filter by mutableStateOf("sharp")
    private var gamepadActive by mutableStateOf(false)
    private var portrait by mutableStateOf(false)
    private var landscapeLayout by mutableStateOf("left-right")
    private var portraitLayout by mutableStateOf("top-bottom")
    private var swapped by mutableStateOf(false)
    /** Landscape: game uses the whole screen and the controls float over it. */
    private var fullScreen by mutableStateOf(true)
    /** "auto", "portrait" or "landscape". */
    private var orientationMode by mutableStateOf("auto")
    /** On-screen controls hidden by the user (for touch-only games or a clean view). */
    private var controlsHidden by mutableStateOf(false)
    /** Holding A or B repeats the press, like turbo buttons on a pad. */
    private var turbo by mutableStateOf(false)
    private val turboJobs = mutableMapOf<Int, kotlinx.coroutines.Job>()
    private var cheats by mutableStateOf<List<Cheat>>(emptyList())
    private var cheatSlots = 0
    private var cheatsOpen by mutableStateOf(false)
    private var slotTimes by mutableStateOf(List(SaveStore.SLOTS) { 0L })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppColors.override = DarkPalette
        Haptics.init(this)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
        preferSixtyHz()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        loadPreferences()
        portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change -> if (change < 0 && ready) pauseGame() }
            .build()
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val gameFile = intent.getStringExtra(EXTRA_PATH)?.let(::File)
        val store = SaveStore(this)
        saves = store.folder(id)
        refreshSlots()
        if (id.isBlank() || gameFile == null || !gameFile.isFile) {
            error = "This game is not ready. Go back and tap it again."
        } else {
            store.rotateBackups(id)
            try {
                val data = GLRetroViewData(this).apply {
                    coreFilePath = File(applicationInfo.nativeLibraryDir, "libmelondsds.so").absolutePath
                    gameFilePath = gameFile.absolutePath
                    systemDirectory = File(filesDir, "system").apply { mkdirs() }.absolutePath
                    savesDirectory = saves.absolutePath
                    saveRAMState = File(saves, SaveStore.BATTERY).takeIf { it.isFile && it.length() < 32L * 1024 * 1024 }?.readBytes()
                    preferLowLatencyAudio = true
                    skipDuplicateFrames = true
                    shader = shaderFor(filter)
                    variables = arrayOf(
                        Variable("melonds_number_of_screen_layouts", "1"),
                        Variable("melonds_screen_layout1", currentLayout()),
                        Variable("melonds_hybrid_small_screen", "both"),
                        Variable("melonds_touch_mode", "touch"),
                        Variable("melonds_boot_mode", "direct"),
                        Variable("melonds_sysfile_mode", "builtin"),
                        Variable("melonds_console_mode", "ds"),
                        Variable("melonds_render_mode", "software"),
                        Variable("melonds_threaded_renderer", "enabled"),
                        Variable("melonds_jit_enable", "enabled"),
                        Variable("melonds_screen_gap", "0"),
                        Variable("melonds_network_mode", "disabled"),
                        Variable("melonds_firmware_language", "auto"),
                        Variable("melonds_mic_input", "noise"),
                        Variable("melonds_show_current_layout", "disabled"),
                        Variable("melonds_show_bios_warnings", "disabled")
                    )
                }
                val view = GLRetroView(this, data)
                engine = view
                lifecycleScope.launch {
                    view.getGLRetroErrors().collect { code ->
                        when (code) {
                            GLRetroView.ERROR_CHEAT -> { notice = "One cheat code isn't valid"; return@collect }
                            GLRetroView.ERROR_SERIALIZATION -> { notice = "That save state could not be used"; return@collect }
                        }
                        ready = false
                        error = when (code) {
                            GLRetroView.ERROR_LOAD_GAME -> "This file can't be opened. It may be damaged or DSi-only."
                            GLRetroView.ERROR_LOAD_LIBRARY -> "The emulator could not start on this device."
                            GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "This device does not support the graphics mode the emulator needs."
                            else -> "The game stopped. Your last saved progress is kept."
                        }
                    }
                }
                lifecycleScope.launch {
                    view.getGLRetroEvents().collect { event ->
                        if (event == GLRetroView.GLRetroEvents.SurfaceCreated) {
                            ready = true
                            cheats = Cheat.load(File(saves, CHEATS_FILE))
                            applyCheats()
                            if (!preferences.getBoolean("tipShown", false)) {
                                preferences.edit().putBoolean("tipShown", true).apply()
                                notice = "Tap the menu button for saves and settings"
                            }
                            val resume = File(saves, SaveStore.AUTOSAVE)
                            if (resume.isFile && resume.length() < 96L * 1024 * 1024) {
                                // DS states are tens of MB; read them off the main thread so the screen never stalls.
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val bytes = runCatching { resume.readBytes() }.getOrNull() ?: return@launch
                                    view.queueEvent {
                                        val ok = runCatching { view.unserializeState(bytes, false) }.getOrDefault(false)
                                        mainHandler.post { notice = if (ok) "Continued where you left off" else null }
                                    }
                                }
                            }
                        }
                    }
                }
                engineOwner.lifecycle.addObserver(view)
                engineOwner.registry.currentState = Lifecycle.State.CREATED
                lifecycleScope.launch {
                    // Never leave a black screen: big ROMs load slowly on budget phones, so allow 45 s.
                    delay(45_000)
                    if (!ready && error == null && !destroyed) error = "This game is taking too long to start. Try again."
                }
            } catch (_: Throwable) {
                error = "The emulator could not start. Please open the game again."
            }
        }
        setContent { TurboTheme { PlayerScreen(title) } }
        lifecycleScope.launch {
            // Write the in-game save often, so a crash or a killed app loses at most half a minute.
            while (true) {
                delay(30_000)
                if (ready && !menu && resumed) saveSnapshot(state = null)
            }
        }
    }

    private fun loadPreferences() {
        scale = preferences.getFloat("scale", 1f).coerceIn(0.75f, 1.25f)
        opacity = preferences.getFloat("opacity", 0.65f).coerceIn(0.25f, 0.95f)
        haptics = preferences.getBoolean("haptics", true)
        swapButtons = preferences.getBoolean("swapButtons", false)
        ffSpeed = preferences.getInt("ffSpeed", 2).coerceIn(2, 4)
        muteFastForward = preferences.getBoolean("muteFastForward", true)
        filter = preferences.getString("filter", "sharp") ?: "sharp"
        landscapeLayout = preferences.getString("landscapeLayout", "left-right") ?: "left-right"
        portraitLayout = preferences.getString("portraitLayout", "top-bottom") ?: "top-bottom"
        fullScreen = preferences.getBoolean("fullScreen", true)
        turbo = preferences.getBoolean("turbo", false)
        orientationMode = preferences.getString("orientation", "auto") ?: "auto"
        applyOrientation()
    }

    private fun applyOrientation() {
        requestedOrientation = when (orientationMode) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    private fun setOrientation(mode: String) {
        orientationMode = mode
        preferences.edit().putString("orientation", mode).apply()
        applyOrientation()
    }

    /** Quick rotate: lock to the other orientation right away. */
    private fun rotateNow() {
        setOrientation(if (portrait) "landscape" else "portrait")
    }

    private fun saveFullScreen(value: Boolean) {
        fullScreen = value
        preferences.edit().putBoolean("fullScreen", value).apply()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nowPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        if (nowPortrait != portrait) {
            // A finger held during the turn would otherwise leave a button stuck down.
            releaseButtons()
            portrait = nowPortrait
            applyLayout()
            notice = when {
                portrait -> "Portrait mode"
                fullScreen -> "Landscape · full screen"
                else -> "Landscape mode"
            }
        }
    }

    // ---- Screen layouts ----------------------------------------------------------------------

    private fun currentLayout(): String {
        val base = if (portrait) portraitLayout else landscapeLayout
        return if (!swapped) base else when (base) {
            "left-right" -> "right-left"
            "top-bottom" -> "bottom-top"
            "hybrid-top" -> "hybrid-bottom"
            "top" -> "bottom"
            else -> base
        }
    }

    /** Width ÷ height of what the core draws for the current layout (DS screen 256×192). */
    private fun layoutRatio(): Float = when (if (portrait) portraitLayout else landscapeLayout) {
        "left-right" -> 512f / 192f
        "top-bottom" -> 256f / 384f
        "hybrid-top" -> 768f / 384f
        else -> 256f / 192f
    }

    private fun applyLayout() {
        engine?.updateVariables(Variable("melonds_screen_layout1", currentLayout()))
    }

    private fun swapScreens() {
        if (!ready) return
        swapped = !swapped
        applyLayout()
        notice = if (swapped) "Screens swapped" else "Screens restored"
    }

    private fun chooseLayout(layout: String) {
        swapped = false
        if (portrait) {
            portraitLayout = layout
            preferences.edit().putString("portraitLayout", layout).apply()
        } else {
            landscapeLayout = layout
            preferences.edit().putString("landscapeLayout", layout).apply()
        }
        applyLayout()
    }

    // ---- System bars, refresh rate ----------------------------------------------------------

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** On 90/120 Hz panels a 60 Hz mode keeps DS frame pacing even and saves battery. */
    private fun preferSixtyHz() {
        runCatching {
            @Suppress("DEPRECATION") val display = windowManager.defaultDisplay
            val current = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .minByOrNull { abs(it.refreshRate - 60f) } ?: return
            if (abs(best.refreshRate - 60f) < 1.5f && best.modeId != current.modeId) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
            }
        }
    }

    // ---- Lifecycle (engine destroyed only after its render thread stopped) ---------------------

    override fun onResume() {
        AppColors.override = DarkPalette
        super.onResume()
        resumed = true
        if (!menu && error == null) resumeEngine()
    }

    override fun onPause() {
        resumed = false
        pauseGame()
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        releaseButtons()
        val view = engine
        if (view != null && view.isAttachedToWindow) {
            // Let a save queued on pause finish on the render thread before shutting down.
            val idle = CountDownLatch(1)
            view.queueEvent { idle.countDown() }
            idle.await(1500, TimeUnit.MILLISECONDS)
            // Freeing the core while the render thread still drew a frame crashed; wait for the
            // detach, which joins the render thread first.
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) = destroyEngine()
            })
        } else destroyEngine()
        audio.abandonAudioFocusRequest(focus)
        super.onDestroy()
    }

    private fun destroyEngine() {
        if (engineOwner.registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            engineOwner.registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private fun resumeEngine() {
        if (destroyed || error != null || !resumed) return
        pauseRequested = false
        audio.requestAudioFocus(focus)
        engineOwner.registry.currentState = Lifecycle.State.RESUMED
    }

    /** Opening the menu, leaving or backgrounding always writes the save and a resume point. */
    private fun pauseGame() {
        releaseButtons()
        menu = true
        pauseRequested = true
        if (engineOwner.registry.currentState == Lifecycle.State.RESUMED) {
            if (ready && !snapshotPending) saveSnapshot(state = SaveStore.AUTOSAVE, pauseAfter = true)
            else if (!ready) engineOwner.registry.currentState = Lifecycle.State.CREATED
        }
        audio.abandonAudioFocusRequest(focus)
    }

    // ---- Saving ------------------------------------------------------------------------------

    /** Writes the in-game save, plus a state file named [state] when given. Runs on the GL thread. */
    private fun saveSnapshot(state: String?, pauseAfter: Boolean = false, message: String? = null, done: (() -> Unit)? = null) {
        val view = engine ?: return
        if (!ready || snapshotPending) return
        snapshotPending = true
        view.queueEvent {
            val result = runCatching {
                val ram = view.serializeSRAM(false)
                if (ram.isNotEmpty()) atomicWrite(File(saves, SaveStore.BATTERY), ram)
                if (state != null) {
                    val bytes = view.serializeState(false)
                    if (bytes.isNotEmpty()) atomicWrite(File(saves, state), bytes)
                }
            }
            mainHandler.post {
                snapshotPending = false
                if (destroyed) return@post
                if (pauseAfter || pauseRequested) engineOwner.registry.currentState = Lifecycle.State.CREATED
                if (result.isFailure) notice = "Save failed. Free some space and try again."
                else if (message != null) notice = message
                refreshSlots()
                done?.invoke()
            }
        }
    }

    private fun slotFile(slot: Int) = File(saves, "slot$slot.state")

    private fun refreshSlots() {
        if (!::saves.isInitialized) return
        slotTimes = List(SaveStore.SLOTS) { index -> slotFile(index + 1).takeIf { it.isFile }?.lastModified() ?: 0L }
    }

    private fun saveToSlot(slot: Int) {
        menu = false
        resumeEngine()
        saveSnapshot(state = "slot$slot.state", message = "Saved to slot $slot")
    }

    /**
     * Loading a state also rewinds the in-game save (it is part of the state), which cost
     * melonDS users real progress. Back up the current in-game save first, every time.
     */
    private fun loadSlot(slot: Int) = loadStateFile(slotFile(slot), "Slot $slot")

    private fun quickSave() {
        if (!ready || snapshotPending) return
        saveSnapshot(state = QUICK_FILE, message = "Quick saved")
    }

    private fun quickLoad() = loadStateFile(File(saves, QUICK_FILE), "Quick save")

    private fun loadStateFile(file: File, label: String) {
        if (!file.isFile) { notice = "$label is empty"; return }
        File(saves, SaveStore.BATTERY).takeIf { it.isFile }?.let { it.copyTo(File(saves, "${SaveStore.BATTERY}.before-load"), overwrite = true) }
        menu = false
        resumeEngine()
        val view = engine ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val bytes = runCatching { file.readBytes() }.getOrNull()
            view.queueEvent {
                val ok = bytes != null && runCatching { view.unserializeState(bytes, false) }.getOrDefault(false)
                mainHandler.post { notice = if (ok) "$label loaded" else "This save could not be loaded" }
            }
        }
    }

    private fun restartGame() {
        menu = false
        resumeEngine()
        engine?.queueEvent { runCatching { engine?.reset(false) } }
        notice = "Game restarted"
    }

    // ---- Input -------------------------------------------------------------------------------

    private fun toggleFastForward() {
        if (!ready || menu) return
        fastForward = !fastForward
        engine?.frameSpeed = if (fastForward) ffSpeed else 1
        engine?.audioEnabled = !(fastForward && muteFastForward)
        notice = if (fastForward) "Fast-forward ${ffSpeed}×" else "Normal speed"
    }

    private fun key(code: Int, down: Boolean) {
        if (!ready || menu || error != null) return
        val mapped = if (swapButtons) when (code) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            else -> code
        } else code
        if (turbo && mapped in turboKeys) {
            if (down && turboJobs[mapped]?.isActive != true) {
                pressed.add(mapped)
                turboJobs[mapped] = lifecycleScope.launch {
                    // About 15 presses a second: fast enough for menus and battles, still read by every game.
                    while (true) {
                        engine?.sendKeyEvent(KeyEvent.ACTION_DOWN, mapped); delay(34)
                        engine?.sendKeyEvent(KeyEvent.ACTION_UP, mapped); delay(34)
                    }
                }
            } else if (!down) {
                turboJobs.remove(mapped)?.cancel()
                if (pressed.remove(mapped)) engine?.sendKeyEvent(KeyEvent.ACTION_UP, mapped)
            }
            return
        }
        if (down) {
            if (pressed.add(mapped)) engine?.sendKeyEvent(KeyEvent.ACTION_DOWN, mapped)
        } else if (pressed.remove(mapped)) engine?.sendKeyEvent(KeyEvent.ACTION_UP, mapped)
    }

    // ---- Cheats (Action Replay codes, stored per game next to its saves) -----------------------

    private fun applyCheats() {
        val view = engine ?: return
        if (!ready) return
        val count = maxOf(cheatSlots, cheats.size)
        for (index in 0 until count) {
            val cheat = cheats.getOrNull(index)
            runCatching { view.setCheat(index, cheat?.enabled == true, cheat?.code ?: "") }
        }
        cheatSlots = cheats.size
    }

    private fun updateCheats(list: List<Cheat>) {
        cheats = list
        Cheat.save(File(saves, CHEATS_FILE), list)
        applyCheats()
    }

    private fun releaseButtons() {
        turboJobs.values.forEach { it.cancel() }
        turboJobs.clear()
        pressed.toList().forEach { engine?.sendKeyEvent(KeyEvent.ACTION_UP, it) }
        pressed.clear()
    }

    // Overriding (and calling super on) an Activity method is fine; lint mistakes it for a restricted call.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) {
            if (!gamepadActive && event.action == KeyEvent.ACTION_DOWN && event.keyCode != KeyEvent.KEYCODE_BACK) gamepadActive = true
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE || event.keyCode == KeyEvent.KEYCODE_BUTTON_START && menu) {
                if (event.action == KeyEvent.ACTION_DOWN && !snapshotPending) {
                    if (menu) { menu = false; resumeEngine() } else pauseGame()
                }
                return true
            }
            if (event.keyCode in controllerKeys) {
                // Controllers label buttons Xbox-style; the DS uses the Nintendo layout.
                val mapped = when (event.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
                    KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
                    KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
                    KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
                    else -> event.keyCode
                }
                key(mapped, event.action == KeyEvent.ACTION_DOWN)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK && !menu) {
            val x = event.getAxisValue(MotionEvent.AXIS_HAT_X).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_Y)
            key(KeyEvent.KEYCODE_DPAD_LEFT, x < -0.35f); key(KeyEvent.KEYCODE_DPAD_RIGHT, x > 0.35f)
            key(KeyEvent.KEYCODE_DPAD_UP, y < -0.35f); key(KeyEvent.KEYCODE_DPAD_DOWN, y > 0.35f)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    // ---- UI ----------------------------------------------------------------------------------

    @Composable
    private fun PlayerScreen(title: String) {
        BackHandler { if (error != null || menu && !snapshotPending) finish() else if (!menu) pauseGame() }
        LaunchedEffect(notice) { if (notice != null) { delay(2200); notice = null } }
        val showControls = !gamepadActive && !controlsHidden
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    // Any touch brings the on-screen controls back after a gamepad hid them.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (gamepadActive && event.changes.any { it.pressed }) gamepadActive = false
                        }
                    }
                }
                .windowInsetsPadding(WindowInsets.displayCutout)
        ) {
            GameAndControls(showControls)

            AnimatedVisibility(
                notice != null && !menu, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it }, exit = fadeOut(tween(200))
            ) {
                Text(
                    notice.orEmpty(), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(PanelColor.copy(alpha = 0.92f), RoundedCornerShape(50))
                        .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            AnimatedVisibility(!ready && error == null && engine != null, enter = fadeIn(), exit = fadeOut(tween(350))) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.appkraft.turbonds.ui.BrandSpinner(68.dp, Modifier.padding(bottom = 8.dp))
                        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 24.dp))
                        Text("Starting game…", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }

            AnimatedVisibility(
                menu && error == null,
                enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.96f),
                exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.98f)
            ) { PauseMenu(title) }

            if (cheatsOpen) CheatsPanel(cheats, onChange = ::updateCheats, onClose = { cheatsOpen = false })

            error?.let { message ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.widthIn(max = 420.dp).padding(24.dp).background(PanelColor, RoundedCornerShape(24.dp))
                            .border(1.dp, StrokeColor, RoundedCornerShape(24.dp)).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Coral, modifier = Modifier.size(30.dp))
                        Text("Game couldn't start", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(message, color = TextMuted, fontSize = 13.sp)
                        PrimaryButton("Back to library", { finish() }, height = 42.dp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
        }
    }

    /**
     * The game view and the controls for the current orientation. The GL view is placed from a
     * single call site and only moved and resized: removing it from the window on rotation (two
     * separate layouts did that) shut down its GL thread and left the game screen black.
     */
    @Composable
    private fun GameAndControls(showControls: Boolean) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ratio = layoutRatio()
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val landscapeUnit = (screenHeight.value / 360f).coerceIn(0.85f, 1.2f) * scale
            val sidePad = (128 * landscapeUnit).dp
            val side = sidePad + 28.dp
            val gameWidth: Dp
            val gameTop: Dp
            when {
                portrait -> {
                    val maxGameHeight = if (showControls) screenHeight * 0.6f else screenHeight
                    gameWidth = minOf(screenWidth, maxGameHeight * ratio)
                    gameTop = if (showControls) 0.dp else (screenHeight - gameWidth / ratio) / 2
                }
                fullScreen || !showControls -> {
                    gameWidth = minOf(screenWidth, screenHeight * ratio)
                    gameTop = (screenHeight - gameWidth / ratio) / 2
                }
                else -> {
                    gameWidth = minOf(screenWidth - side * 2, screenHeight * ratio)
                    gameTop = (screenHeight - gameWidth / ratio) / 2
                }
            }
            val gameHeight = gameWidth / ratio
            val gameLeft = (screenWidth - gameWidth) / 2

            Box(Modifier.offset(gameLeft, gameTop).size(gameWidth, gameHeight)) {
                engine?.let { view -> AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) }
            }

            AnimatedVisibility(showControls, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
                when {
                    portrait -> PortraitControls(Modifier.fillMaxSize().padding(top = gameHeight + gameTop), screenWidth)
                    fullScreen -> OverlayControls(landscapeUnit)
                    else -> SideControls(side, sidePad, landscapeUnit)
                }
            }
            if (!showControls) GamepadShortcuts(Modifier.align(Alignment.TopStart))
        }
    }

    /** Small round actions shared by every layout: menu, swap screens, rotate, fast-forward. */
    @Composable
    private fun QuickActions(size: Dp = 36.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(Icons.Rounded.Menu, "Open menu", size = size) { pauseGame() }
            GlassIcon(Icons.Rounded.Save, "Quick save", size = size) { quickSave() }
            GlassIcon(Icons.Rounded.Restore, "Quick load", size = size) { quickLoad() }
            GlassIcon(Icons.Rounded.SwapVert, "Swap screens", size = size) { swapScreens() }
            GlassIcon(Icons.Rounded.ScreenRotation, if (portrait) "Switch to landscape" else "Switch to portrait", size = size) { rotateNow() }
            GlassIcon(Icons.Rounded.FastForward, if (fastForward) "Normal speed" else "Fast forward", active = fastForward,
                badge = if (fastForward) "${ffSpeed}×" else null, size = size) { toggleFastForward() }
            GlassIcon(Icons.Rounded.VisibilityOff, "Hide controls", size = size) { controlsHidden = true; notice = "Controls hidden" }
        }
    }

    /** Landscape full screen: the game fills the screen, controls float in the thumb zones. */
    @Composable
    private fun OverlayControls(unit: Float) {
        val pad = (122 * unit).dp
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 14.dp, vertical = 10.dp)) {
            HoldButton("L", KeyEvent.KEYCODE_BUTTON_L1, Modifier.align(Alignment.TopStart).width((72 * unit).dp).height((34 * unit).dp), RoundedCornerShape(50))
            HoldButton("R", KeyEvent.KEYCODE_BUTTON_R1, Modifier.align(Alignment.TopEnd).width((72 * unit).dp).height((34 * unit).dp), RoundedCornerShape(50))
            Box(Modifier.align(Alignment.TopCenter)) { QuickActions(34.dp) }
            DPad(Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 4.dp).size(pad))
            FaceButtons(Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp).size(pad))
            Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                HoldButton("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, Modifier.width((62 * unit).dp).height((26 * unit).dp), RoundedCornerShape(50))
                MicButton(unit)
                HoldButton("START", KeyEvent.KEYCODE_BUTTON_START, Modifier.width((62 * unit).dp).height((26 * unit).dp), RoundedCornerShape(50))
            }
        }
    }

    /** Landscape with full screen off: screens in the middle, controls beside them. */
    @Composable
    private fun SideControls(side: Dp, pad: Dp, unit: Float) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.CenterStart).width(side).fillMaxHeight().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIcon(Icons.Rounded.Menu, "Open menu") { pauseGame() }
                    HoldButton("L", KeyEvent.KEYCODE_BUTTON_L1, Modifier.width((64 * unit).dp).height((32 * unit).dp), RoundedCornerShape(50))
                }
                DPad(Modifier.size(pad))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HoldButton("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, Modifier.width((62 * unit).dp).height((26 * unit).dp), RoundedCornerShape(50))
                    MicButton(unit)
                }
            }
            Column(Modifier.align(Alignment.CenterEnd).width(side).fillMaxHeight().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HoldButton("R", KeyEvent.KEYCODE_BUTTON_R1, Modifier.width((64 * unit).dp).height((32 * unit).dp), RoundedCornerShape(50))
                    GlassIcon(Icons.Rounded.FastForward, if (fastForward) "Normal speed" else "Fast forward", active = fastForward,
                        badge = if (fastForward) "${ffSpeed}×" else null) { toggleFastForward() }
                }
                FaceButtons(Modifier.size(pad))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HoldButton("START", KeyEvent.KEYCODE_BUTTON_START, Modifier.width((62 * unit).dp).height((26 * unit).dp), RoundedCornerShape(50))
                    GlassIcon(Icons.Rounded.ScreenRotation, "Switch to portrait", size = 30.dp) { rotateNow() }
                }
            }
        }
    }

    /** Portrait: both screens on top at full width, a full controller below. */
    @Composable
    private fun PortraitControls(modifier: Modifier, screenWidth: Dp) {
        val unit = (screenWidth.value / 400f).coerceIn(0.8f, 1.2f) * scale
        val pad = (124 * unit).dp
        Column(modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                HoldButton("L", KeyEvent.KEYCODE_BUTTON_L1, Modifier.width((72 * unit).dp).height((32 * unit).dp), RoundedCornerShape(50))
                HoldButton("R", KeyEvent.KEYCODE_BUTTON_R1, Modifier.width((72 * unit).dp).height((32 * unit).dp), RoundedCornerShape(50))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QuickActions(32.dp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                DPad(Modifier.size(pad))
                FaceButtons(Modifier.size(pad))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                HoldButton("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, Modifier.width((66 * unit).dp).height((26 * unit).dp), RoundedCornerShape(50))
                Spacer(Modifier.width(10.dp))
                MicButton(unit)
                Spacer(Modifier.width(10.dp))
                HoldButton("START", KeyEvent.KEYCODE_BUTTON_START, Modifier.width((66 * unit).dp).height((26 * unit).dp), RoundedCornerShape(50))
            }
        }
    }

    @Composable
    private fun GamepadShortcuts(modifier: Modifier) {
        Row(modifier.padding(10.dp).graphicsLayer { alpha = 0.6f }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (controlsHidden) GlassIcon(Icons.Rounded.Visibility, "Show controls", active = true) { controlsHidden = false }
            GlassIcon(Icons.Rounded.Menu, "Open menu") { pauseGame() }
            GlassIcon(Icons.Rounded.Save, "Quick save") { quickSave() }
            GlassIcon(Icons.Rounded.Restore, "Quick load") { quickLoad() }
            GlassIcon(Icons.Rounded.FastForward, "Fast forward", active = fastForward, badge = if (fastForward) "${ffSpeed}×" else null) { toggleFastForward() }
        }
    }

    @Composable
    private fun MicButton(unit: Float) {
        // The DS microphone without the microphone permission: holding this sends simulated noise.
        HoldButton("MIC", KeyEvent.KEYCODE_BUTTON_L2, Modifier.width((50 * unit).dp).height((26 * unit).dp), RoundedCornerShape(50), icon = Icons.Rounded.Mic)
    }

    @Composable
    private fun PauseMenu(title: String) {
        val resume = { if (!snapshotPending) { menu = false; resumeEngine() } }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { resume() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val panel = Modifier.widthIn(max = 720.dp).background(PanelColor, RoundedCornerShape(24.dp))
                .border(1.dp, StrokeColor, RoundedCornerShape(24.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
            if (portrait) {
                Column(panel.fillMaxWidth().fillMaxHeight(0.92f).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MenuActions(title, resume)
                    MenuSettings()
                }
            } else {
                Row(panel.fillMaxHeight()) {
                    Column(Modifier.weight(0.46f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MenuActions(title, resume)
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 18.dp).background(StrokeColor))
                    Column(Modifier.weight(0.54f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MenuSettings()
                    }
                }
            }
        }
    }

    @Composable
    private fun MenuActions(title: String, resume: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PAUSED", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
            PrimaryButton("Resume", { resume() }, icon = Icons.Rounded.PlayArrow, enabled = !snapshotPending, height = 42.dp, modifier = Modifier.fillMaxWidth())
            SettingLabel("Save states")
            val format = remember { DateFormat.getTimeFormat(this@PlayerActivity) }
            val dateFormat = remember { DateFormat.getMediumDateFormat(this@PlayerActivity) }
            (1..SaveStore.SLOTS).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { slot ->
                        val time = slotTimes[slot - 1]
                        SlotCard(slot, if (time > 0) "${dateFormat.format(Date(time))} ${format.format(Date(time))}" else "Empty",
                            canLoad = time > 0, modifier = Modifier.weight(1f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Cheats", { cheatsOpen = true }, icon = Icons.Rounded.Code, enabled = ready, height = 40.dp, modifier = Modifier.weight(1f))
                SecondaryButton("Restart", { restartGame() }, icon = Icons.Rounded.RestartAlt, enabled = ready && !snapshotPending, height = 40.dp, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (snapshotPending) "Saving…" else "Save & exit", { finish() }, icon = Icons.AutoMirrored.Rounded.ExitToApp,
                    enabled = !snapshotPending, height = 40.dp, container = Coral.copy(alpha = 0.16f), content = Coral, modifier = Modifier.weight(1f))
            }
            notice?.let { Text(it, color = Accent, fontSize = 12.sp) }
            Text("Auto-saves every 30 seconds", color = TextFaint, fontSize = 11.sp)
        }
    }

    @Composable
    private fun SlotCard(slot: Int, label: String, canLoad: Boolean, modifier: Modifier) {
        Column(modifier.clip(RoundedCornerShape(14.dp)).background(Raised).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Slot $slot", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlatformPill("Save", false) { if (ready && !snapshotPending) saveToSlot(slot) }
                if (canLoad) PlatformPill("Load", false) { if (ready && !snapshotPending) loadSlot(slot) }
            }
        }
    }

    @Composable
    private fun MenuSettings() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            SettingLabel(if (portrait) "Screen layout (portrait)" else "Screen layout (landscape)")
            val options = if (portrait) listOf("top-bottom" to "Stacked", "top" to "One screen")
            else listOf("left-right" to "Side by side", "hybrid-top" to "Big + small", "top" to "One screen")
            val current = if (portrait) portraitLayout else landscapeLayout
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (key, label) -> PlatformPill(label, current == key) { chooseLayout(key) } }
            }
            SettingLabel("Screen orientation")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("auto" to "Auto", "portrait" to "Portrait", "landscape" to "Landscape").forEach { (key, label) ->
                    PlatformPill(label, orientationMode == key) { setOrientation(key) }
                }
            }
            SettingSwitch("Full screen in landscape", fullScreen) { saveFullScreen(it) }
            SettingSlider("Controls size", "${(scale * 100).roundToInt()}%", scale, 0.75f..1.25f, { scale = it }) {
                preferences.edit().putFloat("scale", scale).apply()
            }
            SettingSlider("Controls opacity", "${(opacity * 100).roundToInt()}%", opacity, 0.25f..0.95f, { opacity = it }) {
                preferences.edit().putFloat("opacity", opacity).apply()
            }
            SettingLabel("Fast-forward speed")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 3, 4).forEach { speed ->
                    PlatformPill("${speed}×", ffSpeed == speed) {
                        ffSpeed = speed
                        preferences.edit().putInt("ffSpeed", speed).apply()
                        if (fastForward) engine?.frameSpeed = speed
                    }
                }
            }
            SettingLabel("Screen filter")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("sharp" to "Sharp", "smooth" to "Smooth", "lcd" to "LCD").forEach { (key, label) ->
                    PlatformPill(label, filter == key) {
                        filter = key
                        preferences.edit().putString("filter", key).apply()
                        engine?.shader = shaderFor(key)
                    }
                }
            }
            SettingSwitch("Mute sound in fast-forward", muteFastForward) {
                muteFastForward = it
                preferences.edit().putBoolean("muteFastForward", it).apply()
                engine?.audioEnabled = !(fastForward && it)
            }
            SettingSwitch("Button vibration", haptics) { haptics = it; preferences.edit().putBoolean("haptics", it).apply() }
            SettingSwitch("Swap A / B", swapButtons) { swapButtons = it; preferences.edit().putBoolean("swapButtons", it).apply() }
            SettingSwitch("Turbo A and B (hold to repeat)", turbo) { releaseButtons(); turbo = it; preferences.edit().putBoolean("turbo", it).apply() }
        }
    }

    @Composable
    private fun SettingLabel(text: String) {
        Text(text, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }

    @Composable
    private fun SettingSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(value, color = TextPrimary, fontSize = 12.sp)
            }
            Slider(
                value = current, onValueChange = onChange, valueRange = range, onValueChangeFinished = onDone,
                modifier = Modifier.height(30.dp),
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Raised)
            )
        }
    }

    @Composable
    private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked, onChange, colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = Accent,
                uncheckedThumbColor = TextMuted, uncheckedTrackColor = Raised, uncheckedBorderColor = StrokeColor))
        }
    }

    @Composable
    private fun GlassIcon(icon: ImageVector, label: String, active: Boolean = false, badge: String? = null, size: Dp = 38.dp, onClick: () -> Unit) {
        Box(
            Modifier.size(size).graphicsLayer { alpha = maxOf(opacity, 0.6f) }.clip(CircleShape)
                .background(if (active) Accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = if (active) 0f else 0.14f), CircleShape)
                .clickable(role = Role.Button, onClickLabel = label) {
                    if (haptics) Haptics.gameTap()
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            if (badge != null) Text(badge, color = OnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            else Icon(icon, contentDescription = label, tint = if (active) OnAccent else Color.White, modifier = Modifier.size(size * 0.5f))
        }
    }

    /**
     * X/Y/A/B as one touch area: every finger is tracked, and a thumb resting between two buttons
     * presses both (run + jump), which single-button controls in other DS apps could not do.
     */
    @Composable
    private fun FaceButtons(modifier: Modifier) {
        val buttons = remember {
            listOf(
                FaceButton("X", KeyEvent.KEYCODE_BUTTON_X, 0.5f, 0.17f),
                FaceButton("Y", KeyEvent.KEYCODE_BUTTON_Y, 0.17f, 0.5f),
                FaceButton("A", KeyEvent.KEYCODE_BUTTON_A, 0.83f, 0.5f),
                FaceButton("B", KeyEvent.KEYCODE_BUTTON_B, 0.5f, 0.83f)
            )
        }
        var active by remember { mutableStateOf(emptySet<Int>()) }
        DisposableEffect(Unit) { onDispose { buttons.forEach { key(it.code, false) } } }
        Box(modifier
            .graphicsLayer { alpha = if (active.isEmpty()) opacity else maxOf(opacity, 0.92f) }
            .semantics { contentDescription = "Buttons X, Y, A and B" }
            .pointerInput(Unit) {
                val fingers = mutableMapOf<PointerId, Set<Int>>()
                fun hits(x: Float, y: Float): Set<Int> {
                    val radius = size.width * 0.17f
                    val distances = buttons.map { it to hypot(x - it.x * size.width, y - it.y * size.height) }.sortedBy { it.second }
                    val (first, nearest) = distances[0]
                    if (nearest > radius * 1.7f) return emptySet()
                    val (second, next) = distances[1]
                    // Between two neighbouring buttons: press both.
                    return if (next < radius * 1.45f && next - nearest < radius * 0.45f) setOf(first.code, second.code) else setOf(first.code)
                }
                fun sync() {
                    val all = fingers.values.flatten().toSet()
                    buttons.forEach { key(it.code, it.code in all) }
                    if (all != active) {
                        if (haptics && (all - active).isNotEmpty()) Haptics.gameTap()
                        active = all
                    }
                }
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) fingers[change.id] = hits(change.position.x, change.position.y) else fingers.remove(change.id)
                        }
                        sync()
                    }
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.width * 0.17f
                buttons.forEach { button ->
                    val center = Offset(button.x * size.width, button.y * size.height)
                    val on = button.code in active
                    drawCircle(if (on) Accent else ControlFill, radius, center)
                    drawCircle(if (on) Accent else Color.White.copy(alpha = 0.16f), radius, center, style = Stroke(1.5.dp.toPx()))
                }
            }
            buttons.forEach { button ->
                val on = button.code in active
                Text(button.label, color = if (on) OnAccent else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(BiasAlignment(button.x * 2 - 1, button.y * 2 - 1)))
            }
        }
    }

    private data class FaceButton(val label: String, val code: Int, val x: Float, val y: Float)

    @Composable
    private fun HoldButton(label: String, code: Int, modifier: Modifier, shape: Shape = CircleShape, icon: ImageVector? = null) {
        var held by remember { mutableStateOf(false) }
        val press by animateFloatAsState(if (held) 0.9f else 1f, spring(dampingRatio = 0.6f, stiffness = 1600f), label = "press")
        DisposableEffect(code) { onDispose { key(code, false) } }
        Box(modifier
            .graphicsLayer { scaleX = press; scaleY = press; alpha = if (held) maxOf(opacity, 0.92f) else opacity }
            .clip(shape)
            .background(if (held) Accent else ControlFill)
            .border(1.5.dp, if (held) Accent else Color.White.copy(alpha = 0.16f), shape)
            .semantics {
                contentDescription = "Game button $label"
                role = Role.Button
                onClick {
                    key(code, true)
                    mainHandler.postDelayed({ key(code, false) }, 100)
                    true
                }
            }
            .pointerInput(code) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    held = true; key(code, true)
                    if (haptics) Haptics.gameTap()
                    try { do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed }) }
                    finally { held = false; key(code, false) }
                }
            }, contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, tint = if (held) OnAccent else Color.White, modifier = Modifier.size(15.dp))
            else Text(label, color = if (held) OnAccent else Color.White, fontSize = if (label.length > 1) 10.sp else 18.sp,
                fontWeight = FontWeight.Bold, letterSpacing = if (label.length > 1) 0.8.sp else 0.sp)
        }
    }

    @Composable
    private fun DPad(modifier: Modifier) {
        var active by remember { mutableStateOf(emptySet<Int>()) }
        val directions = listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
        Box(modifier.graphicsLayer { alpha = if (active.isEmpty()) opacity else maxOf(opacity, 0.92f) }
            .semantics { contentDescription = "Direction pad" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun update(x: Float, y: Float) {
                        val dx = x / size.width - 0.5f
                        val dy = y / size.height - 0.5f
                        val next = mutableSetOf<Int>()
                        if (hypot(dx, dy) > 0.08f) {
                            val angle = (Math.toDegrees(atan2(dy, dx).toDouble()) + 360) % 360
                            fun near(center: Double, half: Double) = abs((angle - center + 540) % 360 - 180) <= half
                            // Cardinals get 60° each and diagonals 30°, so walking in a grid rarely slips diagonal.
                            when {
                                near(0.0, 30.0) -> next += KeyEvent.KEYCODE_DPAD_RIGHT
                                near(90.0, 30.0) -> next += KeyEvent.KEYCODE_DPAD_DOWN
                                near(180.0, 30.0) -> next += KeyEvent.KEYCODE_DPAD_LEFT
                                near(270.0, 30.0) -> next += KeyEvent.KEYCODE_DPAD_UP
                                near(45.0, 15.0) -> { next += KeyEvent.KEYCODE_DPAD_RIGHT; next += KeyEvent.KEYCODE_DPAD_DOWN }
                                near(135.0, 15.0) -> { next += KeyEvent.KEYCODE_DPAD_DOWN; next += KeyEvent.KEYCODE_DPAD_LEFT }
                                near(225.0, 15.0) -> { next += KeyEvent.KEYCODE_DPAD_LEFT; next += KeyEvent.KEYCODE_DPAD_UP }
                                else -> { next += KeyEvent.KEYCODE_DPAD_UP; next += KeyEvent.KEYCODE_DPAD_RIGHT }
                            }
                        }
                        directions.forEach { key(it, it in next) }
                        if (next != active) {
                            if (haptics && next.isNotEmpty()) Haptics.gameTap()
                            active = next
                        }
                    }
                    update(down.position.x, down.position.y)
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer?.pressed != true) break
                            update(pointer.position.x, pointer.position.y)
                        } while (true)
                    } finally {
                        directions.forEach { key(it, false) }
                        active = emptySet()
                    }
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                val arm = size.minDimension / 3f
                val radius = CornerRadius(arm * 0.3f)
                drawCircle(Color.White.copy(alpha = 0.04f), radius = size.minDimension / 2f)
                drawRoundRect(ControlFill, topLeft = Offset(arm, 0f), size = Size(arm, size.height), cornerRadius = radius)
                drawRoundRect(ControlFill, topLeft = Offset(0f, arm), size = Size(size.width, arm), cornerRadius = radius)
                val stroke = Stroke(width = 1.5.dp.toPx())
                drawRoundRect(Color.White.copy(alpha = 0.16f), topLeft = Offset(arm, 0f), size = Size(arm, size.height), cornerRadius = radius, style = stroke)
                drawRoundRect(Color.White.copy(alpha = 0.16f), topLeft = Offset(0f, arm), size = Size(size.width, arm), cornerRadius = radius, style = stroke)
                drawRect(ControlFill, topLeft = Offset(arm + 1.5.dp.toPx(), arm + 1.5.dp.toPx()), size = Size(arm - 3.dp.toPx(), arm - 3.dp.toPx()))
                val highlight = Accent.copy(alpha = 0.9f)
                if (KeyEvent.KEYCODE_DPAD_UP in active) drawRoundRect(highlight, Offset(arm, 0f), Size(arm, arm), radius)
                if (KeyEvent.KEYCODE_DPAD_DOWN in active) drawRoundRect(highlight, Offset(arm, arm * 2), Size(arm, arm), radius)
                if (KeyEvent.KEYCODE_DPAD_LEFT in active) drawRoundRect(highlight, Offset(0f, arm), Size(arm, arm), radius)
                if (KeyEvent.KEYCODE_DPAD_RIGHT in active) drawRoundRect(highlight, Offset(arm * 2, arm), Size(arm, arm), radius)
                val c = size.width / 2f
                val tip = arm * 0.22f
                fun arrow(cx: Float, cy: Float, dx: Float, dy: Float, on: Boolean) {
                    val path = Path().apply {
                        moveTo(cx + dx * tip, cy + dy * tip)
                        lineTo(cx - dx * tip * 0.6f + dy * tip, cy - dy * tip * 0.6f + dx * tip)
                        lineTo(cx - dx * tip * 0.6f - dy * tip, cy - dy * tip * 0.6f - dx * tip)
                        close()
                    }
                    drawPath(path, if (on) OnAccent else Color.White.copy(alpha = 0.7f))
                }
                arrow(c, arm * 0.5f, 0f, -1f, KeyEvent.KEYCODE_DPAD_UP in active)
                arrow(c, arm * 2.5f, 0f, 1f, KeyEvent.KEYCODE_DPAD_DOWN in active)
                arrow(arm * 0.5f, c, -1f, 0f, KeyEvent.KEYCODE_DPAD_LEFT in active)
                arrow(arm * 2.5f, c, 1f, 0f, KeyEvent.KEYCODE_DPAD_RIGHT in active)
                drawCircle(Color.Black.copy(alpha = 0.25f), radius = arm * 0.22f, center = Offset(c, size.height / 2f))
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PATH = "path"
        private const val QUICK_FILE = "quick.state"
        private const val CHEATS_FILE = "cheats.json"
        private val turboKeys = setOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B)

        private val controllerKeys = setOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)

        fun launch(context: Context, id: String, title: String, path: String) {
            context.startActivity(Intent(context, PlayerActivity::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_PATH, path)
                .apply { if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }

        private val ControlFill = Color(0xFF1C2430)

        private fun shaderFor(key: String): ShaderConfig = when (key) {
            "smooth" -> ShaderConfig.Default
            "lcd" -> ShaderConfig.LCD
            else -> ShaderConfig.Sharp
        }

        private fun atomicWrite(file: File, bytes: ByteArray) {
            val pending = File(file.parentFile, file.name + ".tmp")
            pending.outputStream().use { output -> output.write(bytes); output.fd.sync() }
            check(pending.renameTo(file)) { "Save could not be written." }
        }
    }
}
