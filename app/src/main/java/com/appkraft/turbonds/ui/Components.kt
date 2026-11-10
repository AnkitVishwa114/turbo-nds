package com.appkraft.turbonds.ui

import androidx.compose.animation.core.animateFloat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/** Gentle press feedback shared by every tappable card. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Every placeholder reads the same wall-clock phase, so skeleton rows shimmer in sync. Only the
 * draw phase reads the value; nothing recomposes while it runs.
 */
@Composable
private fun shimmerPhase(): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { phase.floatValue = (SystemClock.uptimeMillis() % 1500L) / 1500f }
    }
    return phase
}

@Composable
fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    val phase = shimmerPhase()
    Box(modifier.drawWithCache {
        val band = size.width.coerceAtLeast(160f)
        onDrawBehind {
            val start = -band + (size.width + band * 2) * phase.value
            drawRect(Raised)
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, ShimmerHighlight, Color.Transparent),
                    start = Offset(start, 0f),
                    end = Offset(start + band, size.height * 0.6f)
                )
            )
        }
    })
}

@Composable
private fun Bone(width: Float = 1f, height: Dp, modifier: Modifier = Modifier) {
    LoadingPlaceholder(modifier.fillMaxWidth(width).height(height).clip(RoundedCornerShape(5.dp)))
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 46.dp,
    container: Color = Accent,
    content: Color = OnAccent
) {
    val interaction = remember { MutableInteractionSource() }
    val background by animateColorAsState(if (enabled) container else container.copy(alpha = 0.35f), tween(180), label = "buttonColor")
    val small = height < 40.dp
    Surface(
        onClick = {
            Haptics.click()
            onClick()
        },
        enabled = enabled && !loading,
        modifier = modifier.height(height).pressScale(interaction, 0.96f),
        interactionSource = interaction,
        shape = RoundedCornerShape(if (small) 10.dp else 14.dp),
        color = background,
        contentColor = content
    ) {
        Row(Modifier.padding(horizontal = if (small) 12.dp else 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(if (small) 13.dp else 17.dp), color = content, strokeWidth = 2.dp)
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(if (small) 16.dp else 20.dp))
            }
            if (loading || icon != null) Spacer(Modifier.width(6.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = if (small) 12.5.sp else 14.sp, maxLines = 1)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 46.dp
) {
    PrimaryButton(text, onClick, modifier, icon, enabled, loading, height, container = Raised, content = TextPrimary)
}

@Composable
fun PlatformPill(label: String, selected: Boolean, accent: Color? = null, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val background by animateColorAsState(if (selected) Accent else Surface, tween(200), label = "filterBackground")
    val foreground by animateColorAsState(if (selected) OnAccent else TextPrimary.copy(alpha = 0.9f), tween(200), label = "filterText")
    val border by animateColorAsState(if (selected) Accent else Stroke, tween(200), label = "filterBorder")
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = Modifier.height(32.dp).semantics { this.selected = selected },
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = foreground,
        border = BorderStroke(1.dp, border)
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (accent != null) {
                Box(Modifier.size(6.dp).background(if (selected) OnAccent else accent, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Thin progress bar; indeterminate (a moving sweep) when [fraction] is null. */
@Composable
fun ProgressTrack(fraction: Float?, modifier: Modifier = Modifier, color: Color = Accent) {
    val animated by androidx.compose.animation.core.animateFloatAsState(fraction ?: 0f, tween(350), label = "progress")
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Raised)) {
        if (fraction != null) {
            ShineFill(animated, color)
        } else {
            val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "indeterminate")
            val offset = transition.animateFloat(
                -0.4f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
                label = "sweep"
            )
            Box(Modifier.fillMaxWidth(0.4f).fillMaxHeight().graphicsLayer { translationX = size.width / 0.4f * offset.value }
                .background(color, RoundedCornerShape(3.dp)))
        }
    }
}
