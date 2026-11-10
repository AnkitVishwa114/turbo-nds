package com.appkraft.turbonds

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appkraft.turbonds.ads.Ads
import com.appkraft.turbonds.data.Engagement
import com.appkraft.turbonds.ui.AppColors
import com.appkraft.turbonds.ui.DarkPalette
import com.appkraft.turbonds.ui.Haptics
import com.appkraft.turbonds.ui.LightPalette
import com.appkraft.turbonds.ui.ThemePreference
import com.appkraft.turbonds.ui.TurboApp
import com.appkraft.turbonds.ui.TurboTheme
import com.appkraft.turbonds.ui.BrandSplash
import com.appkraft.turbonds.ui.rememberIsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The Compose launch screen owns the branding; dismiss the empty system layer on its first frame.
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        ThemePreference.load(this)
        Engagement.install(application)
        // Coming back after a real break may show an app open ad over the library (never over a game).
        Engagement.onReturn = { started, away ->
            if (started is MainActivity && !started.isFinishing) {
                Ads.showAppOpenIfDue(started, Engagement.usage().launches, coldStart = false, awayMs = away)
            }
        }
        Haptics.init(this)
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        AppColors.selected = if (ThemePreference.mode == "light" || ThemePreference.mode == "system" && !systemDark) LightPalette else DarkPalette
        applySystemBars(AppColors.selected === DarkPalette)
        setContent {
            val dark = rememberIsDarkTheme()
            SideEffect { AppColors.selected = if (dark) DarkPalette else LightPalette }
            LaunchedEffect(dark) { applySystemBars(dark) }
            TurboTheme {
                val viewModel: LibraryViewModel = viewModel()
                var showingSplash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                LaunchedEffect(showingSplash) {
                    if (showingSplash) {
                        delay(1450)
                        // A returning user may get an app open ad over the launch screen; wait briefly for it.
                        val launches = Engagement.usage().launches
                        if (Ads.appOpenWanted(this@MainActivity, launches, coldStart = true, awayMs = 0)) {
                            withTimeoutOrNull(2_500) { snapshotFlow { Ads.appOpenReady }.first { it } }
                            Ads.showAppOpenIfDue(this@MainActivity, launches, coldStart = true, awayMs = 0)
                        }
                        showingSplash = false
                    }
                }
                Crossfade(targetState = showingSplash, animationSpec = tween(380), label = "launch") { splash ->
                    if (splash) BrandSplash()
                    else TurboApp(viewModel)
                }
            }
        }
        // Consent and ads start only once this window is fully built (starting them earlier crashed
        // EmulatorGames 1.3.0 at launch on some phones).
        window.decorView.post {
            if (!isFinishing && !isDestroyed) Ads.gatherConsent(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // The player pins a dark palette while it runs; drop it once the library is back.
        AppColors.override = null
    }

    private fun applySystemBars(dark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
