package com.appkraft.turbonds.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Turbo NDS mark, identical to the launcher icon (concept 9): a white handheld DS with a
 * glowing orange top screen and a turbo bolt on deep navy, drawn so it is crisp at any size.
 */
@Composable
fun AppMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val k = this.size.width / 100f
        fun rr(color: Color, left: Float, top: Float, right: Float, bottom: Float, radius: Float) =
            drawRoundRect(color, Offset(left * k, top * k), Size((right - left) * k, (bottom - top) * k), CornerRadius(radius * k))
        fun dot(color: Color, cx: Float, cy: Float) = drawCircle(color, 2.5f * k, Offset(cx * k, cy * k))
        val navy = Color(0xFF141A26)
        val orange = Color(0xFFFF5A1F)
        val ink = Color(0xFF1C2030)
        drawRoundRect(navy, cornerRadius = CornerRadius(23f * k))
        rr(Color.White, 16f, 14f, 84f, 48f, 8f)
        rr(orange, 29f, 19f, 71f, 43f, 3f)
        drawPath(Path().apply {
            moveTo(53f * k, 21.5f * k); lineTo(45f * k, 30.5f * k); lineTo(50f * k, 30.5f * k)
            lineTo(47.5f * k, 37.5f * k); lineTo(56f * k, 28f * k); lineTo(51f * k, 28f * k); close()
        }, Color.White)
        rr(Color(0xFFC9D0DA), 18f, 48f, 82f, 52f, 0f)
        rr(Color.White, 14f, 52f, 86f, 86f, 9f)
        rr(ink, 38f, 57f, 62f, 80f, 3f)
        rr(ink, 23f, 63f, 27f, 76f, 0f)
        rr(ink, 18.5f, 67.5f, 31.5f, 71.5f, 0f)
        dot(ink, 76f, 63f); dot(orange, 81f, 69f); dot(ink, 76f, 75f); dot(ink, 71f, 69f)
    }
}

/** Mark + wordmark at the same height on one line. */
@Composable
fun Wordmark(height: Dp = 28.dp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AppMark(height)
        Spacer(Modifier.width(8.dp))
        Text("Turbo", color = TextPrimary, fontSize = (height.value * 0.68f).sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
        Text(" NDS", color = Accent, fontSize = (height.value * 0.68f).sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
    }
}
