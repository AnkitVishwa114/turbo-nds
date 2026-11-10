package com.appkraft.turbonds.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Snappy = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Launch screen: the mark and name fade in once, centred, and a single thin bar at the bottom
 * shows loading. One loader only; nothing spins around the logo.
 */
@Composable
fun BrandSplash(modifier: Modifier = Modifier) {
    val shown = remember { Animatable(0f) }
    val bar = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { shown.animateTo(1f, tween(450, easing = Snappy)) }
        launch { kotlinx.coroutines.delay(150); bar.animateTo(1f, tween(1250, easing = FastOutSlowInEasing)) }
    }
    Box(modifier.fillMaxSize().background(Background)) {
        Column(
            Modifier.align(Alignment.Center).offset(y = (-20).dp).graphicsLayer {
                alpha = shown.value
                scaleX = 0.96f + 0.04f * shown.value
                scaleY = scaleX
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppMark(88.dp)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Turbo", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont, letterSpacing = (-0.5).sp)
                Text(" NDS", color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont, letterSpacing = (-0.5).sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("DS EMULATOR", color = TextFaint, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 3.sp))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp)
                .width(112.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Raised)
                .graphicsLayer { alpha = shown.value }
        ) {
            Box(Modifier.fillMaxWidth(bar.value).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Accent))
        }
    }
}

/**
 * Orange ring that charges around the mark (0..1), with a faint track and a bright head,
 * then keeps a slow idle spin so the screen never looks frozen.
 */
@Composable
fun TurboRing(size: Dp, charge: Float, modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "ring")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "turn")
    val accent = Accent
    val track = Stroke
    Canvas(modifier.size(size)) {
        val width = 3.dp.toPx()
        val inset = width / 2
        val arcSize = Size(this.size.width - width, this.size.height - width)
        drawArc(track.copy(alpha = 0.6f), 0f, 360f, false, Offset(inset, inset), arcSize, style = DrawStroke(width))
        val sweep = 40f + 280f * charge
        val start = -90f + turn * charge
        drawArc(
            Brush.sweepGradient(listOf(accent.copy(alpha = 0f), accent, accent), center),
            start, sweep, false, Offset(inset, inset), arcSize, style = DrawStroke(width, cap = StrokeCap.Round)
        )
    }
}

/** Compact branded spinner (mark inside a spinning ring) for in-app loading moments. */
@Composable
fun BrandSpinner(size: Dp = 64.dp, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "spinner")
    val breathe by pulse.animateFloat(0.94f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        TurboRing(size, charge = 0.55f)
        AppMark(size * 0.56f, Modifier.graphicsLayer { scaleX = breathe; scaleY = breathe })
    }
}

/** Filled part of a progress bar with a light sweep running across it, like a charging meter. */
@Composable
internal fun ShineFill(fraction: Float, color: Color = Accent) {
    val shine = rememberInfiniteTransition(label = "shine")
    val x by shine.animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "x")
    Box(
        Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color)
            .drawWithCache {
                val band = size.width.coerceAtLeast(40f) * 0.45f
                onDrawWithContent {
                    drawContent()
                    val start = size.width * x - band
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), start, start + band * 2))
                }
            }
    )
}

private val risen = HashSet<Any>()

/**
 * Cards rise and fade in the first time they appear, a little staggered by [index]. Items seen
 * before (scrolling back, returning to a tab) show at once, so nothing replays and feels slow.
 */
@Composable
fun Modifier.riseIn(key: Any, index: Int): Modifier {
    val first = remember(key) { risen.add(key) }
    val shown = remember(key) { Animatable(if (first) 0f else 1f) }
    LaunchedEffect(key) {
        if (shown.value < 1f) {
            kotlinx.coroutines.delay((index % 8) * 45L)
            shown.animateTo(1f, tween(380, easing = Snappy))
        }
    }
    return this.graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * 16.dp.toPx()
    }
}
