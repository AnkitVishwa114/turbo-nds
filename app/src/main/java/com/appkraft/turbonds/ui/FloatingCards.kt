package com.appkraft.turbonds.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared shell for the floating cards (update, ad-free offer): one look across the app. */
@Composable
fun FloatingCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val light = AppColors.current.isLight
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Surface,
        contentColor = TextPrimary,
        border = BorderStroke(1.dp, if (light) Stroke.copy(alpha = 0.7f) else Stroke.copy(alpha = 0.55f)),
        shadowElevation = if (light) 14.dp else 6.dp,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), content = content)
    }
}

/** Card title: tight, confident, no subtitle fluff. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = (-0.1).sp))
}

@Composable
fun CardBody(text: String) {
    Text(text, color = TextMuted, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp))
}

/**
 * Small round close button. With [progress] it also draws a thin ring that empties as the
 * card's auto-dismiss timer runs, so the user can see how long it stays.
 */
@Composable
fun CardCloseButton(onClick: () -> Unit, progress: Float? = null) {
    val ring = Accent
    val track = Stroke
    Surface(
        onClick = { Haptics.tick(); onClick() },
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        color = Raised.copy(alpha = if (AppColors.current.isLight) 0.7f else 0.9f),
        contentColor = TextMuted
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (progress != null) {
                Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                    val stroke = 2.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = DrawStroke(stroke))
                    drawArc(ring, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = DrawStroke(stroke, cap = StrokeCap.Round))
                }
            }
            Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(15.dp))
        }
    }
}

/** Main action of a floating card: solid brand pill with an optional leading icon. */
@Composable
fun CardPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { Haptics.tick(); onClick() },
        modifier = modifier.height(38.dp).pressScale(interaction, 0.95f),
        interactionSource = interaction,
        shape = CircleShape,
        color = Accent,
        contentColor = OnAccent
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp))
        }
    }
}
