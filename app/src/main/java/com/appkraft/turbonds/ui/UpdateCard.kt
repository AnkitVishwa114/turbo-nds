package com.appkraft.turbonds.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Required update: covers the whole app until the newer version is installed. Nothing behind
 * it can be touched, and Back leaves the app instead of closing the card.
 */
@Composable
fun UpdateGate(onUpdate: () -> Unit) {
    val activity = LocalActivity.current
    BackHandler { activity?.finish() }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
    Box(
        Modifier.fillMaxSize().background(Background)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding().padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        FloatingCard(
            Modifier.widthIn(max = 420.dp).graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 40.dp.toPx()
                scaleX = 0.96f + 0.04f * enter.value
                scaleY = scaleX
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppMark(44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CardTitle("Update required")
                    CardBody("Install the new version to keep playing.")
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 10.dp, end = 4.dp), thickness = 1.dp, color = Stroke.copy(alpha = 0.6f))
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = Accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Your games and saves stay safe",
                    color = TextFaint,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    modifier = Modifier.weight(1f)
                )
                CardPrimaryButton("Update", onUpdate, icon = Icons.Rounded.Download, modifier = Modifier.height(36.dp))
            }
        }
    }
}
