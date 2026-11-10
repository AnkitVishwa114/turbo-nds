package com.appkraft.turbonds.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appkraft.turbonds.library.GameEntry
import com.appkraft.turbonds.library.GameStore
import java.util.concurrent.ConcurrentHashMap

/** A game's icon (from the cartridge banner) and its signature colour, cached per game id. */
private data class Art(val icon: ImageBitmap?, val color: Color)

private val artCache = ConcurrentHashMap<String, Art>()

private fun loadArt(game: GameEntry, store: GameStore): Art = artCache.getOrPut(game.id) {
    val bitmap = if (game.hasIcon) runCatching { BitmapFactory.decodeFile(store.iconFile(game.id).absolutePath) }.getOrNull() else null
    val color = bitmap?.let { signatureColor(IntArray(it.width * it.height).also { px -> it.getPixels(px, 0, it.width, 0, 0, it.width, it.height) }) }
        ?: fallbackColor(game.title)
    Art(bitmap?.asImageBitmap(), color)
}

/** Forget cached art (after a rescan the icons may have changed). */
fun clearArtCache() = artCache.clear()

/**
 * The most characteristic colour of a pixel-art icon: opaque pixels weighted by how colourful
 * they are, then lifted to a vivid, readable tone. Gives every game tile its own colour.
 */
internal fun signatureColor(pixels: IntArray): Color? {
    var r = 0.0; var g = 0.0; var b = 0.0; var weight = 0.0
    for (p in pixels) {
        if (p ushr 24 < 128) continue
        val pr = p shr 16 and 0xFF; val pg = p shr 8 and 0xFF; val pb = p and 0xFF
        val max = maxOf(pr, pg, pb); val min = minOf(pr, pg, pb)
        val saturation = if (max == 0) 0.0 else (max - min).toDouble() / max
        val w = 0.15 + saturation * saturation
        r += pr * w; g += pg * w; b += pb * w; weight += w
    }
    if (weight == 0.0) return null
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt(), hsv)
    hsv[1] = hsv[1].coerceIn(0.45f, 0.85f)
    hsv[2] = 0.88f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** A stable colour for games without an icon, picked from the title. */
private fun fallbackColor(title: String): Color {
    val hue = (title.lowercase().hashCode() and 0x7FFFFFFF) % 360
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.85f)))
}

/**
 * Icon and colour, decoded off the main thread so lists scroll smoothly. Null only for the
 * first frame of a game not seen yet; cached afterwards.
 */
@Composable
private fun rememberArt(game: GameEntry, store: GameStore): Art? {
    val state = produceState(initialValue = artCache[game.id], game.id, game.hasIcon) {
        if (value == null) value = withContext(Dispatchers.IO) { loadArt(game, store) }
    }
    return state.value
}

/** The game's signature colour, for accents around it (sheet header, hero card). */
@Composable
fun rememberGameColor(game: GameEntry, store: GameStore): Color = rememberArt(game, store)?.color ?: Accent

/**
 * Game artwork: the 32×32 DS icon scaled with nearest-neighbour (crisp pixels) on a plate
 * tinted with the game's own colour.
 */
@Composable
fun GameArt(game: GameEntry, store: GameStore, modifier: Modifier = Modifier, corner: Dp = 14.dp, iconScale: Float = 0.62f) {
    val wasCached = remember(game.id) { artCache.containsKey(game.id) }
    val art = rememberArt(game, store)
    val shape = RoundedCornerShape(corner)
    if (art == null) {
        LoadingPlaceholder(modifier.clip(shape))
        return
    }
    // Fade the icon in the first time it appears.
    val shown = remember(game.id) { Animatable(if (wasCached) 1f else 0f) }
    LaunchedEffect(game.id) { shown.animateTo(1f, tween(220)) }
    val light = AppColors.current.isLight
    val plate = art.color.copy(alpha = if (light) 0.20f else 0.24f).compositeOver(ArtworkBackground)
    Box(
        modifier.clip(shape).background(plate).border(1.dp, art.color.copy(alpha = if (light) 0.28f else 0.22f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (art.icon != null) {
            Image(art.icon, contentDescription = null, filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(iconScale).graphicsLayer { alpha = shown.value; scaleX = 0.92f + 0.08f * shown.value; scaleY = scaleX })
        } else {
            Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = art.color, modifier = Modifier.fillMaxSize(iconScale * 0.62f))
        }
    }
}
