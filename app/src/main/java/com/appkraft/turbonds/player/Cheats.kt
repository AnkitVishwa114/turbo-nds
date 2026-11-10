package com.appkraft.turbonds.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appkraft.turbonds.ui.Accent
import com.appkraft.turbonds.ui.Coral
import com.appkraft.turbonds.ui.OnAccent
import com.appkraft.turbonds.ui.PrimaryButton
import com.appkraft.turbonds.ui.Raised
import com.appkraft.turbonds.ui.TextFaint
import com.appkraft.turbonds.ui.TextMuted
import com.appkraft.turbonds.ui.TextPrimary
import com.appkraft.turbonds.ui.Stroke as StrokeColor
import com.appkraft.turbonds.ui.Surface as PanelColor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One Action Replay cheat for a game. [code] is normalised to "XXXXXXXX YYYYYYYY" lines joined by "+". */
data class Cheat(val name: String, val code: String, val enabled: Boolean = true) {
    companion object {
        private val line = Regex("^[0-9A-F]{8} [0-9A-F]{8}$")

        /**
         * Accepts what people paste from cheat sites: any spacing, line breaks or "+" between
         * codes. Returns null when it isn't a valid Action Replay code.
         */
        fun normalise(raw: String): String? {
            val hex = raw.uppercase().replace(Regex("[^0-9A-F]"), "")
            if (hex.isEmpty() || hex.length % 16 != 0) return null
            val lines = hex.chunked(16).map { it.substring(0, 8) + " " + it.substring(8) }
            return if (lines.all { line.matches(it) }) lines.joinToString("+") else null
        }

        fun load(file: File): List<Cheat> = runCatching {
            if (!file.isFile) return emptyList()
            val array = JSONArray(file.readText())
            List(array.length()) { index ->
                val json = array.getJSONObject(index)
                Cheat(json.optString("name"), json.optString("code"), json.optBoolean("enabled", true))
            }.filter { it.code.isNotBlank() }
        }.getOrDefault(emptyList())

        fun save(file: File, cheats: List<Cheat>) {
            val array = JSONArray().apply { cheats.forEach { put(JSONObject().put("name", it.name).put("code", it.code).put("enabled", it.enabled)) } }
            val pending = File(file.parentFile, file.name + ".tmp")
            pending.writeText(array.toString())
            pending.renameTo(file)
        }
    }
}

/** Full-screen panel over the pause menu: the game's cheats with switches, and a form to add one. */
@Composable
internal fun CheatsPanel(cheats: List<Cheat>, onChange: (List<Cheat>) -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onClose() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(24.dp)).background(PanelColor).border(1.dp, StrokeColor, RoundedCornerShape(24.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cheats", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(34.dp).clip(CircleShape).background(Raised).clickable { onClose() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close cheats", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
            if (cheats.isEmpty()) {
                Text("No cheats yet. Add an Action Replay code below.", color = TextMuted, fontSize = 12.5.sp)
            }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(cheats) { index, cheat ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Raised).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(cheat.name.ifBlank { "Cheat ${index + 1}" }, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(cheat.code.substringBefore('+') + if (cheat.code.contains('+')) " …" else "", color = TextFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        Switch(cheat.enabled, { on -> onChange(cheats.toMutableList().also { it[index] = cheat.copy(enabled = on) }) },
                            colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = Accent, uncheckedThumbColor = TextMuted, uncheckedTrackColor = PanelColor, uncheckedBorderColor = StrokeColor))
                        Box(Modifier.size(36.dp).clip(CircleShape).clickable { onChange(cheats.toMutableList().also { it.removeAt(index) }) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete cheat", tint = Coral, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
            Text("Add a cheat", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Field(name, { name = it.take(40) }, "Name, e.g. Infinite money", singleLine = true)
            Field(code, { code = it.take(4000); problem = null }, "Code, e.g. 94000130 FCFF0000", singleLine = false, mono = true)
            problem?.let { Text(it, color = Coral, fontSize = 12.sp) }
            PrimaryButton("Add cheat", {
                val normalised = Cheat.normalise(code)
                if (normalised == null) problem = "That doesn't look like an Action Replay code."
                else {
                    onChange(cheats + Cheat(name.trim(), normalised))
                    name = ""; code = ""
                }
            }, icon = Icons.Rounded.Add, height = 42.dp, modifier = Modifier.fillMaxWidth())
            Text("Cheats can change your saves. Save to a slot before trying a new one.", color = TextFaint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, singleLine: Boolean, mono: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 44.dp, max = 120.dp).clip(RoundedCornerShape(12.dp)).background(Raised)
            .border(1.dp, StrokeColor, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        if (value.isEmpty()) Text(hint, color = TextFaint, fontSize = 13.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine,
            textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
            cursorBrush = SolidColor(Accent), modifier = Modifier.fillMaxWidth()
        )
    }
}
