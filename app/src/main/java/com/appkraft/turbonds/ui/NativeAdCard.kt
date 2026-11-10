package com.appkraft.turbonds.ui

import android.content.Context
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.appkraft.turbonds.R
import com.appkraft.turbonds.ads.NativeAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * One native ad drawn like a game row: icon, an "Ad" badge, headline, one line of text and a
 * call-to-action pill. Takes no space at all when no ad is loaded or the user is ad-free.
 */
@Composable
fun NativeAdCard(slot: Int, modifier: Modifier = Modifier) {
    val ad = NativeAds.forSlot(slot) ?: return
    val colors = NativeAdColors(
        surface = Surface, stroke = Stroke, title = TextPrimary, body = TextMuted,
        accent = Accent, onAccent = OnAccent, badge = Amber, artwork = ArtworkBackground
    )
    AndroidView(
        factory = { context -> NativeAdRow(context) },
        update = { row -> row.bind(ad, colors) },
        modifier = modifier.fillMaxWidth()
    )
}

private data class NativeAdColors(
    val surface: Color, val stroke: Color, val title: Color, val body: Color,
    val accent: Color, val onAccent: Color, val badge: Color, val artwork: Color
)

/** The classic View tree AdMob needs for native ads, built in code to match the Compose rows. */
private class NativeAdRow(context: Context) : FrameLayout(context) {
    private val adView = NativeAdView(context)
    private val card = LinearLayout(context)
    private val icon = ImageView(context)
    private val badge = TextView(context)
    private val headline = TextView(context)
    private val body = TextView(context)
    private val action = TextView(context)
    private var bound: NativeAd? = null

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    private fun dpi(value: Float) = dp(value).toInt()

    private val font: Typeface? = runCatching { ResourcesCompat.getFont(context, R.font.outfit) }.getOrNull()

    private fun TextView.style(sizeSp: Float, weight: Int, lines: Int) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        font?.let { typeface = it }
        // Outfit is a variable font; pick the weight on its axis rather than faking bold.
        fontVariationSettings = "'wght' $weight"
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    init {
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dpi(10f), dpi(10f), dpi(12f), dpi(10f))

        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        icon.clipToOutline = true
        icon.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, dp(12f))
        }
        card.addView(icon, LinearLayout.LayoutParams(dpi(52f), dpi(52f)))

        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val titleLine = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        badge.text = "Ad"
        badge.style(10f, 700, 1)
        badge.setPadding(dpi(5f), dpi(2f), dpi(5f), dpi(2f))
        titleLine.addView(badge, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dpi(6f) })
        headline.style(14f, 600, 1)
        titleLine.addView(headline, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        texts.addView(titleLine)
        body.style(12.5f, 400, 2)
        texts.addView(body, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(4f) })
        card.addView(texts, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dpi(12f)
            marginEnd = dpi(10f)
        })

        action.style(13f, 700, 1)
        action.gravity = Gravity.CENTER
        action.setPadding(dpi(14f), dpi(8f), dpi(14f), dpi(8f))
        card.addView(action, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        adView.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        adView.headlineView = headline
        adView.bodyView = body
        adView.iconView = icon
        adView.callToActionView = action
        addView(adView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(ad: NativeAd, colors: NativeAdColors) {
        card.background = GradientDrawable().apply {
            cornerRadius = dp(16f)
            setColor(colors.surface.toArgb())
            setStroke(dpi(1f), colors.stroke.copy(alpha = 0.7f).toArgb())
        }
        badge.setTextColor(colors.badge.toArgb())
        badge.background = GradientDrawable().apply {
            cornerRadius = dp(5f)
            setColor(colors.badge.copy(alpha = 0.16f).toArgb())
        }
        headline.setTextColor(colors.title.toArgb())
        body.setTextColor(colors.body.toArgb())
        action.setTextColor(colors.onAccent.toArgb())
        action.background = GradientDrawable().apply {
            cornerRadius = dp(100f)
            setColor(colors.accent.toArgb())
        }
        icon.setBackgroundColor(colors.artwork.toArgb())

        if (bound === ad) return
        bound = ad
        headline.text = ad.headline
        val text = ad.body ?: ad.advertiser ?: ad.store
        body.text = text
        body.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        val cta = ad.callToAction
        action.text = cta
        action.visibility = if (cta.isNullOrBlank()) View.GONE else View.VISIBLE
        val drawable = ad.icon?.drawable
        icon.setImageDrawable(drawable)
        icon.visibility = if (drawable == null) View.GONE else View.VISIBLE
        // Registers the views with this ad; must come after the assets are set.
        adView.setNativeAd(ad)
    }
}
