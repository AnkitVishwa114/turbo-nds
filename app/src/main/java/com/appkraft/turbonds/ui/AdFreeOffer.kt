package com.appkraft.turbonds.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** How long the offer stays before it leaves on its own. */
private const val OFFER_VISIBLE_MS = 12_000

/**
 * Floating card offering one rewarded video for an ad-free hour. [renewal] when the previous
 * hour has just run out. A ring around the close button shows the time left on screen.
 */
@Composable
fun AdFreeOffer(renewal: Boolean, onShown: () -> Unit, onWatch: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val remaining = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        onShown()
        remaining.animateTo(0f, tween(OFFER_VISIBLE_MS, easing = LinearEasing))
        onClose()
    }
    FloatingCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PremiumBadgeIcon()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardTitle(if (renewal) "Your ad-free hour is over" else "Go ad-free for 1 hour", Modifier.weight(1f, fill = false))
                    if (!renewal) {
                        Spacer(Modifier.width(6.dp))
                        FreeTag()
                    }
                }
                CardBody(if (renewal) "One more video for another hour." else "One short video, no ads for an hour.")
            }
            Spacer(Modifier.width(6.dp))
            CardCloseButton(onClose, progress = remaining.value)
        }
        CardPrimaryButton(
            if (renewal) "Watch and extend" else "Watch video",
            onWatch,
            icon = Icons.Rounded.PlayArrow,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, end = 4.dp)
        )
    }
}

/**
 * Asked once in a while right before a game starts: watch one video for an ad-free hour, or
 * just play. Either choice starts the game; dismissing it counts as "Play now".
 */
@Composable
fun PlayAdFreeDialog(gameTitle: String, onShown: () -> Unit, onWatch: () -> Unit, onPlay: () -> Unit) {
    LaunchedEffect(Unit) { onShown() }
    Dialog(onDismissRequest = onPlay) {
        Surface(shape = RoundedCornerShape(24.dp), color = Surface, contentColor = TextPrimary, border = BorderStroke(1.dp, Stroke)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PremiumBadgeIcon()
                    Spacer(Modifier.weight(1f))
                    FreeTag()
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Play without ads", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Watch one short video for an ad-free hour.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Watch video", onWatch, icon = Icons.Rounded.PlayArrow, height = 46.dp, modifier = Modifier.fillMaxWidth())
                    SecondaryButton("Play now", onPlay, icon = Icons.Rounded.SportsEsports, height = 46.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PremiumBadgeIcon() {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Amber.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = Amber, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun FreeTag() {
    Text(
        "FREE",
        color = Accent,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Accent.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
