package com.appkraft.turbonds.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appkraft.turbonds.BuildConfig
import com.appkraft.turbonds.LibraryViewModel
import com.appkraft.turbonds.Screen
import com.appkraft.turbonds.ads.AdUnits
import com.appkraft.turbonds.ads.Ads
import com.appkraft.turbonds.data.PlayStore
import kotlinx.coroutines.launch

/** Public pages on the Turbo NDS website (privacy policy and GPL source code). */
const val WEBSITE = "https://turbonds.site"
const val PRIVACY_URL = "$WEBSITE/privacy/"
const val SOURCE_URL = "$WEBSITE/source/"

@Composable
fun SettingsScreen(viewModel: LibraryViewModel, actions: AppActions) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val state = viewModel.state
    var cacheBytes by remember { mutableLongStateOf(viewModel.store.cacheBytes()) }

    val exportSaves = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { viewModel.saves.exportAll(context, uri) }
                .onSuccess { viewModel.showNotice(if (it == 0) "There are no saves to back up yet" else "Backed up $it save files") }
                .onFailure { viewModel.showNotice("The backup could not be written. Try another location.") }
        }
    }
    val importSaves = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { viewModel.saves.importAll(context, uri) }
                .onSuccess { viewModel.showNotice(if (it == 0) "No Turbo NDS saves found in that file" else "Restored $it save files") }
                .onFailure { viewModel.showNotice("That file is not a Turbo NDS backup.") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings")
        LazyColumn(contentPadding = PaddingValues(start = Dimens.Edge, end = Dimens.Edge, bottom = Dimens.Section), verticalArrangement = Arrangement.spacedBy(Dimens.Tight)) {
            item { GroupLabel("Your games") }
            item {
                SettingsGroup {
                    state.folders.forEachIndexed { index, folder ->
                        if (index > 0) GroupDivider()
                        SettingsRow(Icons.Rounded.Folder, viewModel.store.folderLabel(folder), "Tap the icon to stop using this folder",
                            trailing = {
                                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove folder", tint = TextMuted,
                                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(50)).clickableTick { viewModel.removeFolder(folder) })
                            })
                    }
                    if (state.folders.isNotEmpty()) GroupDivider()
                    if (state.files.isNotEmpty()) {
                        SettingsRow(Icons.Rounded.Description, "${state.files.size} game file${if (state.files.size == 1) "" else "s"} added one by one",
                            null)
                        GroupDivider()
                    }
                    SettingsRow(Icons.Rounded.CreateNewFolder, "Add a folder", null, onClick = actions.addFolder)
                    GroupDivider()
                    SettingsRow(Icons.AutoMirrored.Rounded.NoteAdd, "Add game files", null, onClick = actions.addFiles)
                    GroupDivider()
                    SettingsRow(Icons.Rounded.Refresh, "Rescan library", if (state.scanning) "Scanning…" else "${state.games.size} games", onClick = { viewModel.rescan() })
                }
            }

            item { GroupLabel("Appearance") }
            item {
                SettingsGroup {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Theme", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                                PlatformPill(label, ThemePreference.mode == key) { ThemePreference.set(context, key) }
                            }
                        }
                    }
                    GroupDivider()
                    SettingsRow(Icons.Rounded.Vibration, "Touch vibration", null,
                        trailing = { ToggleSwitch(Haptics.enabled) { Haptics.setEnabled(context, it) } },
                        onClick = { Haptics.setEnabled(context, !Haptics.enabled) })
                }
            }

            item { GroupLabel("Saves") }
            item {
                SettingsGroup {
                    SettingsRow(Icons.Rounded.Backup, "Back up saves", "Save to Drive or move to a new phone",
                        onClick = { exportSaves.launch("TurboNDS-saves.zip") })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.Restore, "Restore saves", null,
                        onClick = { importSaves.launch(arrayOf("application/zip", "application/octet-stream")) })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.CleaningServices, "Clear game copies",
                        "${Formatter.formatShortFileSize(context, cacheBytes)} · saves are kept",
                        onClick = {
                            viewModel.store.clearCache()
                            cacheBytes = viewModel.store.cacheBytes()
                            viewModel.showNotice("Game copies cleared")
                        })
                }
            }

            if (!AdUnits.notConfigured) item { GroupLabel("Ads") }
            if (!AdUnits.notConfigured) item {
                SettingsGroup {
                    val adFree = Ads.adFreeUntil > System.currentTimeMillis()
                    SettingsRow(Icons.Rounded.HourglassTop, "Go ad-free for 1 hour",
                        if (adFree) "Ad-free until ${android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(Ads.adFreeUntil))}" else "Watch one short video",
                        onClick = {
                            activity?.let { host -> Ads.showAdFreeReward(host) { earned -> viewModel.showNotice(rewardNotice(host, earned)) } }
                        })
                    if (Ads.privacyOptionsRequired) {
                        GroupDivider()
                        SettingsRow(Icons.Rounded.Shield, "Ad privacy options", null, onClick = { activity?.let(Ads::showPrivacyOptions) })
                    }
                }
            }

            item { GroupLabel("Turbo NDS") }
            item {
                SettingsGroup {
                    SettingsRow(Icons.Rounded.StarRate, "Rate Turbo NDS", null, onClick = { PlayStore.openListing(context) })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.PrivacyTip, "Privacy policy", null, trailing = { OpenIcon() }, onClick = { open(context, PRIVACY_URL) })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.Code, "Source code and licences", null, onClick = { viewModel.navigate(Screen.ABOUT) })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.Info, "Version", BuildConfig.VERSION_NAME)
                }
            }
            item {
                Text("Not affiliated with Nintendo. No games included.",
                    style = MaterialTheme.typography.bodySmall, color = TextFaint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val notices = remember { runCatching { context.assets.open("licenses/NOTICE.txt").bufferedReader().readText() }.getOrDefault("") }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Source code and licences", onBack)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppMark(52.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Turbo NDS ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text("by AppKraft Studio", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            Text("Turbo NDS is open source under GPL-3.0.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            PrimaryButton("Get the source code", { open(context, SOURCE_URL) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, height = 44.dp, modifier = Modifier.fillMaxWidth())
            SettingsGroup {
                Text(notices, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

private fun open(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickableTick(onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).border(1.dp, Stroke.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
        content = content
    )
}

@Composable
private fun GroupDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 54.dp).height(1.dp).background(Stroke.copy(alpha = 0.5f)))
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String?, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickableTick(onClick) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
private fun OpenIcon() {
    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = TextFaint, modifier = Modifier.size(18.dp))
}

@Composable
private fun ToggleSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(checked, onChange, colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = Accent,
        uncheckedThumbColor = TextMuted, uncheckedTrackColor = Raised, uncheckedBorderColor = Stroke))
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
