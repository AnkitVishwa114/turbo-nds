package com.appkraft.turbonds.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.appkraft.turbonds.LibraryTab
import com.appkraft.turbonds.LibraryViewModel
import com.appkraft.turbonds.Screen
import com.appkraft.turbonds.library.GameEntry

private val Gutter = Dimens.Edge

@Composable
fun HomeScreen(viewModel: LibraryViewModel, actions: AppActions) {
    val state = viewModel.state
    val resume = remember(state.games) { state.continueGame(viewModel.saves) }
    val hero = resume ?: state.recentlyPlayed.firstOrNull() ?: state.recentlyAdded.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        TopBar(null) { AddGamesButton(actions.chooseHowToAdd) }
        LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = Dimens.Section), verticalArrangement = Arrangement.spacedBy(Dimens.Section)) {
            if (state.scanning && state.games.isEmpty()) item { HomeSkeleton() }
            else if (state.games.isEmpty()) item { EmptyLibrary(actions, onRescan = { viewModel.rescan() }) }
            hero?.let { game ->
                item(key = "hero") {
                    HeroCard(game, viewModel, resuming = game == resume, onPlay = { actions.play(game) }, onDetails = { actions.openGame(game) })
                }
            }
            shelf("Recently played", Icons.Rounded.History, state.recentlyPlayed.filter { it != hero }.take(12), viewModel, actions) {
                viewModel.setTab(LibraryTab.RECENT); viewModel.navigate(Screen.LIBRARY)
            }
            shelf("Favourites", Icons.Rounded.Favorite, state.favorites.take(12), viewModel, actions) {
                viewModel.setTab(LibraryTab.FAVORITES); viewModel.navigate(Screen.LIBRARY)
            }
            if (state.games.isNotEmpty()) item(key = "native-ad") { NativeAdCard(slot = 0, modifier = Modifier.padding(horizontal = Gutter)) }
            shelf("Recently added", Icons.Rounded.NewReleases, state.recentlyAdded.take(12), viewModel, actions) {
                viewModel.setTab(LibraryTab.ALL); viewModel.navigate(Screen.LIBRARY)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shelf(
    title: String, icon: ImageVector, games: List<GameEntry>, viewModel: LibraryViewModel, actions: AppActions, onSeeAll: () -> Unit
) {
    if (games.isEmpty()) return
    item(key = "shelf-$title") {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Item)) {
            SectionTitle(title, icon, onSeeAll)
            LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(Dimens.Item)) {
                itemsIndexed(games, key = { _, it -> "$title-${it.id}" }) { index, game -> ShelfCard(game, viewModel, actions, Modifier.animateItem().riseIn("$title-${game.id}", index)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector, onSeeAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            Row(
                Modifier.clip(CircleShape).clickableTick(onSeeAll).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("See all", style = MaterialTheme.typography.labelMedium, color = Accent)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Accent, modifier = Modifier.padding(start = 4.dp).size(14.dp))
            }
        }
    }
}

/** The big card at the top: the game to jump back into, in its own colour. */
@Composable
private fun HeroCard(game: GameEntry, viewModel: LibraryViewModel, resuming: Boolean, onPlay: () -> Unit, onDetails: () -> Unit) {
    val color = rememberGameColor(game, viewModel.store)
    val interaction = remember { MutableInteractionSource() }
    val light = AppColors.current.isLight
    Surface(
        onClick = { Haptics.tick(); onDetails() }, interactionSource = interaction,
        modifier = Modifier.padding(horizontal = Gutter).fillMaxWidth().pressScale(interaction, 0.99f),
        shape = RoundedCornerShape(Dimens.CardRadius + 2.dp), color = color.copy(alpha = if (light) 0.07f else 0.09f).compositeOver(Surface), contentColor = TextPrimary,
        border = BorderStroke(1.dp, color.copy(alpha = if (light) 0.35f else 0.3f)),
        shadowElevation = if (light) 6.dp else 0.dp
    ) {
        Box {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameArt(game, viewModel.store, Modifier.size(92.dp), corner = 18.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(color, active = resuming)
                            Spacer(Modifier.width(6.dp))
                            Text(if (resuming) "CONTINUE" else if (game.lastPlayed > 0) "LAST PLAYED" else "READY TO PLAY",
                                style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 1.2.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(game.title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (game.subtitle.isNotBlank()) Text(game.subtitle.substringBefore(" · "), style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaChip(Icons.Rounded.Schedule, if (game.playTimeMs > 0) formatPlayTime(game.playTimeMs) else "New")
                    if (game.lastPlayed > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(DateUtils.getRelativeTimeSpanString(game.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            style = MaterialTheme.typography.bodySmall, color = TextFaint)
                    }
                    Spacer(Modifier.weight(1f))
                    PrimaryButton(if (resuming) "Continue" else "Play", onPlay, icon = Icons.Rounded.PlayArrow, height = 40.dp)
                }
            }
        }
    }
}

/** Small status dot; breathes gently while a game is waiting to be continued. */
@Composable
private fun PulseDot(color: Color, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(1f, if (active) 0.35f else 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha")
    Box(Modifier.size(7.dp).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(color))
}

/** Placeholder layout while the first scan runs: hero and a shelf, shimmering. */
@Composable
private fun HomeSkeleton() {
    Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(Dimens.Section)) {
        LoadingPlaceholder(Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(Dimens.CardRadius + 2.dp)))
        LoadingPlaceholder(Modifier.width(150.dp).height(18.dp).clip(RoundedCornerShape(6.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Item)) {
            repeat(3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoadingPlaceholder(Modifier.size(118.dp, 109.dp).clip(RoundedCornerShape(16.dp)))
                    LoadingPlaceholder(Modifier.width(90.dp).height(12.dp).clip(RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}

@Composable
internal fun MetaChip(icon: ImageVector, text: String) {
    Row(Modifier.clip(CircleShape).background(Raised).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(game: GameEntry, viewModel: LibraryViewModel, actions: AppActions, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    // The ripple shape is clipped, so text sits inside a padding that the rounded corners can't cut.
    Column(
        modifier.width(118.dp).clip(RoundedCornerShape(16.dp)).pressScale(interaction, 0.96f)
            .combinedClickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { Haptics.tick(); actions.play(game) }, onLongClick = { Haptics.heavy(); actions.openGame(game) })
            .padding(bottom = 6.dp)
    ) {
        Box {
            GameArt(game, viewModel.store, Modifier.fillMaxWidth().aspectRatio(1.08f), corner = 16.dp)
            if (game.favorite) Icon(Icons.Rounded.Favorite, contentDescription = "Favourite", tint = Coral,
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(14.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(game.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp))
        Text(if (game.playTimeMs > 0) formatPlayTime(game.playTimeMs) else "Not played", style = MaterialTheme.typography.bodySmall, color = TextFaint, maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

internal fun formatPlayTime(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "Under a minute"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

// ---- First launch, empty states, adding games -----------------------------------------------

@Composable
fun Onboarding(actions: AppActions) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        AppMark(76.dp)
        Spacer(Modifier.height(16.dp))
        Text("Play your DS games", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Add the games you own to get started.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.height(22.dp))
        AddOptions(actions, Modifier.widthIn(max = 460.dp))
        Spacer(Modifier.height(22.dp))
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(Dimens.Item)) {
            FeatureRow(Icons.Rounded.Lock, "Only what you choose", null)
            FeatureRow(Icons.Rounded.Save, "Auto-save and backups", null)
            FeatureRow(Icons.Rounded.WifiOff, "Offline, no ads in game", null)
        }
        Spacer(Modifier.height(18.dp))
        Text("No games included.", style = MaterialTheme.typography.bodySmall, color = TextFaint, textAlign = TextAlign.Center)
    }
}

/** The two ways to add games, side by side with their trade-offs. */
@Composable
fun AddOptions(actions: AppActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionCard(
            icon = Icons.Rounded.CreateNewFolder, title = "Choose a folder", badge = "Best",
            body = "New games appear automatically",
            onClick = actions.addFolder
        )
        OptionCard(
            icon = Icons.AutoMirrored.Rounded.NoteAdd, title = "Pick game files", badge = null,
            body = ".nds, .zip or .7z, also from Download",
            onClick = actions.addFiles
        )
        Text("Tip: Download itself can't be a folder. Use a folder inside it.",
            style = MaterialTheme.typography.bodySmall, color = TextFaint, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun OptionCard(icon: ImageVector, title: String, badge: String?, body: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { Haptics.tick(); onClick() }, interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().pressScale(interaction, 0.98f),
        shape = RoundedCornerShape(18.dp), color = Surface, contentColor = TextPrimary,
        border = BorderStroke(1.dp, if (badge != null) Accent.copy(alpha = 0.45f) else Stroke)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    if (badge != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(badge.uppercase(), style = MaterialTheme.typography.labelSmall, color = Accent,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Accent.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text(body, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Dimens.Item))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGamesSheet(actions: AppActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Background) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
            Text("Add games", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            AddOptions(actions)
        }
    }
}

@Composable
internal fun ScanningPanel(count: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Finding your games…", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(if (count > 0) "$count found so far" else "Looking through what you added", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(Modifier.height(14.dp))
        ProgressTrack(null, Modifier.width(180.dp))
    }
}

@Composable
internal fun EmptyLibrary(actions: AppActions, onRescan: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = TextFaint, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(10.dp))
        Text("No DS games found yet", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Add .nds, .zip or .7z files, then rescan.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Rescan", onRescan, height = 42.dp)
            PrimaryButton("Add games", actions.chooseHowToAdd, height = 42.dp)
        }
    }
}

@Composable
internal fun PreparingDialog(game: GameEntry, progress: Float?, viewModel: LibraryViewModel, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(24.dp), color = Surface, contentColor = TextPrimary, border = BorderStroke(1.dp, Stroke)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameArt(game, viewModel.store, Modifier.size(52.dp), corner = 14.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (game.isArchive) "Unpacking game" else "Getting ready", style = MaterialTheme.typography.titleLarge)
                        Text(game.title, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                ProgressTrack(progress)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("First time only", style = MaterialTheme.typography.bodySmall, color = TextFaint, modifier = Modifier.weight(1f))
                    if (progress != null) Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Accent)
                }
                SecondaryButton("Cancel", onCancel, height = 42.dp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Suppress("unused")
private val keepColor = Color.Unspecified
