package com.appkraft.turbonds.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appkraft.turbonds.R

/** One full set of app colours. Bars sit on [ink], cards on [surface], controls on [raised]. */
data class Palette(
    val isLight: Boolean,
    val ink: Color,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val stroke: Color,
    val accent: Color,
    val accentDeep: Color,
    val aqua: Color,
    val amber: Color,
    val coral: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textFaint: Color,
    val artwork: Color
)

/** Turbo NDS dark theme: deep console-black surfaces, turbo-orange accent, cool steel text. */
val DarkPalette = Palette(
    isLight = false,
    ink = Color(0xFF0A0D13),
    background = Color(0xFF0D1118),
    surface = Color(0xFF151A23),
    raised = Color(0xFF1E2531),
    stroke = Color(0xFF2A3240),
    accent = Color(0xFFFF6B2C),
    accentDeep = Color(0xFFE5541A),
    aqua = Color(0xFF4FD1FF),
    amber = Color(0xFFFFC23D),
    coral = Color(0xFFFF5D73),
    textPrimary = Color(0xFFF2F5FA),
    textMuted = Color(0xFFA9B3C1),
    textFaint = Color(0xFF7A8597),
    artwork = Color(0xFF1A202B)
)

val LightPalette = Palette(
    isLight = true,
    ink = Color(0xFFFFFFFF),
    background = Color(0xFFF3F5F9),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFE8ECF2),
    stroke = Color(0xFFD5DBE4),
    accent = Color(0xFFD9480F),
    accentDeep = Color(0xFFB83A0B),
    aqua = Color(0xFF0B7FA8),
    amber = Color(0xFFC98200),
    coral = Color(0xFFD63B53),
    textPrimary = Color(0xFF0D121C),
    textMuted = Color(0xFF414C5D),
    textFaint = Color(0xFF5F6A7B),
    artwork = Color(0xFFE6EAF0)
)

/**
 * Colours are read through snapshot state, so every screen updates when the theme changes
 * without threading a palette through each call. The player pins [override] to dark.
 */
object AppColors {
    var selected by mutableStateOf(DarkPalette)
    var override by mutableStateOf<Palette?>(null)
    val current: Palette get() = override ?: selected
}

val Ink get() = AppColors.current.ink
val Background get() = AppColors.current.background
val Surface get() = AppColors.current.surface
val Raised get() = AppColors.current.raised
val Stroke get() = AppColors.current.stroke
val Accent get() = AppColors.current.accent
val AccentDeep get() = AppColors.current.accentDeep
val Aqua get() = AppColors.current.aqua
val Amber get() = AppColors.current.amber
val Coral get() = AppColors.current.coral
val TextPrimary get() = AppColors.current.textPrimary
val TextMuted get() = AppColors.current.textMuted
val TextFaint get() = AppColors.current.textFaint
val ArtworkBackground get() = AppColors.current.artwork

/** Content on the accent: white on the deep light-theme orange, near-black on the bright dark-theme orange. */
val OnAccent get() = if (AppColors.current.isLight) Color.White else Color(0xFF1A0B03)

/** Hairline around artwork so icons keep a crisp edge on either background. */
val ImageEdge get() = if (AppColors.current.isLight) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.06f)

/** Moving highlight for skeleton shimmer; brighter on light surfaces so it stays visible. */
val ShimmerHighlight get() = if (AppColors.current.isLight) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.08f)

/**
 * Outfit ships as one variable font whose default instance is Thin (100). Each weight must be
 * requested through the `wght` axis, or every style renders thin with faked bold.
 */
@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: Int) = Font(
    R.font.outfit,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val AppFont = FontFamily(outfit(300), outfit(400), outfit(500), outfit(600), outfit(700), outfit(800))

/** Saved theme choice: "system", "dark" or "light". New installs follow the system; users can switch in Settings. */
object ThemePreference {
    private const val FILE = "app_settings"
    private const val KEY = "theme"
    var mode by mutableStateOf("system")
        private set

    fun load(context: Context) {
        mode = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "system") ?: "system"
    }

    fun set(context: Context, value: String) {
        mode = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    }
}

@Composable
fun rememberIsDarkTheme(): Boolean = when (ThemePreference.mode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    headlineSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp),
    titleSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontFamily = AppFont, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = AppFont, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp),
    labelSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, lineHeight = 13.sp, letterSpacing = 0.5.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

private fun Palette.scheme() = if (isLight) lightColorScheme(
    primary = accent, onPrimary = OnAccent, secondary = aqua, onSecondary = OnAccent,
    background = background, onBackground = textPrimary, surface = surface, onSurface = textPrimary,
    surfaceVariant = raised, onSurfaceVariant = textMuted, surfaceContainer = surface, surfaceContainerHigh = surface,
    surfaceContainerHighest = raised, inverseSurface = Color(0xFF1B2330), inverseOnSurface = Color.White,
    outline = stroke, outlineVariant = stroke, error = coral
) else darkColorScheme(
    primary = accent, onPrimary = OnAccent, secondary = aqua, onSecondary = OnAccent,
    background = background, onBackground = textPrimary, surface = surface, onSurface = textPrimary,
    surfaceVariant = raised, onSurfaceVariant = textMuted, surfaceContainer = surface, surfaceContainerHigh = raised,
    surfaceContainerHighest = raised, inverseSurface = raised, inverseOnSurface = textPrimary,
    outline = stroke, outlineVariant = stroke, error = coral
)

@Composable
fun TurboTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors.current.scheme(), typography = typography, shapes = shapes) {
        CompositionLocalProvider(LocalHapticFeedback provides Haptics.feedback, content = content)
    }
}
