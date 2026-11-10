package com.appkraft.turbonds.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appkraft.turbonds.LibraryTab
import com.appkraft.turbonds.LibraryViewModel
import com.appkraft.turbonds.SortOrder
import com.appkraft.turbonds.library.GameEntry

private val Gutter = Dimens.Edge

@Composable
fun LibraryScreen(viewModel: LibraryViewModel, actions: AppActions) {
    val state = viewModel.state
    val games = state.visibleGames
    var sortOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Library") { AddGamesButton(actions.chooseHowToAdd) }
        SearchField(state.query, viewModel::setQuery, Modifier.padding(horizontal = Gutter))
        Row(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter - 10.dp, top = Dimens.Item, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PlatformPill("All ${state.games.size}", state.tab == LibraryTab.ALL) { viewModel.setTab(LibraryTab.ALL) }
                PlatformPill("Recent", state.tab == LibraryTab.RECENT) { viewModel.setTab(LibraryTab.RECENT) }
                PlatformPill("Favourites", state.tab == LibraryTab.FAVORITES) { viewModel.setTab(LibraryTab.FAVORITES) }
            }
            Box {
                Box(Modifier.size(40.dp).clip(CircleShape).clickableTick { sortOpen = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort games", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }, containerColor = Surface) {
                    listOf(SortOrder.RECENT to "Recently played", SortOrder.NAME to "Name", SortOrder.PLAY_TIME to "Most played").forEach { (order, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = if (state.sort == order) Accent else TextPrimary, fontWeight = if (state.sort == order) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { viewModel.setSort(order); sortOpen = false }
                        )
                    }
                }
            }
        }
        if (state.scanning) Row(Modifier.padding(horizontal = Gutter, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressTrack(null, Modifier.width(56.dp))
            Spacer(Modifier.width(10.dp))
            Text("Checking for new games…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        when {
            state.games.isEmpty() && state.scanning -> LibrarySkeleton()
            state.games.isEmpty() -> EmptyLibrary(actions, onRescan = { viewModel.rescan() })
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(142.dp),
                contentPadding = PaddingValues(start = Gutter, end = Gutter, top = Dimens.Tight, bottom = Dimens.Section),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Item),
                verticalArrangement = Arrangement.spacedBy(Dimens.Edge),
                modifier = Modifier.fillMaxSize()
            ) {
                if (games.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "nothing") {
                    Text(
                        when {
                            state.query.isNotBlank() -> "No game matches “${state.query}”."
                            state.tab == LibraryTab.FAVORITES -> "No favourites yet"
                            else -> "Nothing played yet"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.padding(vertical = 28.dp)
                    )
                }
                games.forEachIndexed { index, game ->
                    item(key = game.id) { LibraryTile(game, viewModel, actions, Modifier.animateItem().riseIn(game.id, index)) }
                    if (index == 7) item(span = { GridItemSpan(maxLineSpan) }, key = "native-ad") { NativeAdCard(slot = 1) }
                }
            }
        }
    }
}

/** Shimmering tiles while the first scan runs. */
@Composable
private fun LibrarySkeleton() {
    Column(Modifier.padding(horizontal = Gutter, vertical = Dimens.Tight), verticalArrangement = Arrangement.spacedBy(Dimens.Edge)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Item)) {
                repeat(2) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoadingPlaceholder(Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(16.dp)))
                        LoadingPlaceholder(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(5.dp)))
                        LoadingPlaceholder(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(5.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(Surface).border(1.dp, Stroke.copy(alpha = 0.8f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search your games", color = TextFaint, style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                cursorBrush = SolidColor(Accent), modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = TextMuted,
            modifier = Modifier.size(20.dp).clip(CircleShape).clickableTick { onQuery("") })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTile(game: GameEntry, viewModel: LibraryViewModel, actions: AppActions, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).pressScale(interaction, 0.97f)
            .combinedClickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { Haptics.tick(); actions.play(game) }, onLongClick = { Haptics.heavy(); actions.openGame(game) })
            .padding(bottom = 6.dp)
    ) {
        Box {
            GameArt(game, viewModel.store, Modifier.fillMaxWidth().aspectRatio(1.15f), corner = 16.dp)
            if (game.favorite) Icon(Icons.Rounded.Favorite, contentDescription = "Favourite", tint = Coral,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(15.dp))
            // Details button: the long-press menu, discoverable.
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(26.dp).clip(CircleShape).background(Background.copy(alpha = 0.75f))
                .clickableTick { actions.openGame(game) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "Game options", tint = TextPrimary, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(game.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp))
        Text(tileInfo(game), style = MaterialTheme.typography.bodySmall, color = TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

private fun tileInfo(game: GameEntry): String = when {
    game.lastPlayed > 0 -> "${formatPlayTime(game.playTimeMs)} · ${DateUtils.getRelativeTimeSpanString(game.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}"
    game.subtitle.isNotBlank() -> game.subtitle
    else -> "Not played yet"
}

/** Everything about one game: details, stats and actions, coloured with the game's own colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSheet(game: GameEntry, viewModel: LibraryViewModel, onDismiss: () -> Unit, onPlay: () -> Unit) {
    val context = LocalContext.current
    val color = rememberGameColor(game, viewModel.store)
    val hasContinue = viewModel.saves.hasContinue(game.id)
    val hasSaves = viewModel.saves.hasAnySave(game.id)
    var confirmDelete by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    val backups = remember(game.id, restoring) { viewModel.saves.backups(game.id) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Background) {
        Column(Modifier.padding(start = Dimens.Edge, end = Dimens.Edge, bottom = Dimens.Section + 4.dp), verticalArrangement = Arrangement.spacedBy(Dimens.Edge)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameArt(game, viewModel.store, Modifier.size(84.dp), corner = 18.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (game.subtitle.isNotBlank()) Text(game.subtitle.substringBefore(" · "), style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(color))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Played", if (game.playTimeMs > 0) formatPlayTime(game.playTimeMs) else "—", Modifier.weight(1f))
                StatTile("Last played", if (game.lastPlayed > 0) DateUtils.getRelativeTimeSpanString(game.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() else "Never", Modifier.weight(1f))
                StatTile("Size", Formatter.formatShortFileSize(context, game.size), Modifier.weight(1f))
            }
            if (confirmDelete) {
                Text("Delete all saves of this game? This can't be undone.", style = MaterialTheme.typography.bodyMedium, color = Coral)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Keep saves", { confirmDelete = false }, height = 44.dp, modifier = Modifier.weight(1f))
                    PrimaryButton("Delete", { viewModel.deleteSaves(game); onDismiss() }, height = 44.dp, container = Coral, content = Color.White, modifier = Modifier.weight(1f))
                }
            } else if (restoring) {
                Text("Pick an earlier in-game save. Your current one is kept too.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Surface).border(1.dp, Stroke.copy(alpha = 0.6f), RoundedCornerShape(18.dp))) {
                    backups.forEachIndexed { index, file ->
                        val whenSaved = DateUtils.getRelativeDateTimeString(context, file.lastModified(), DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
                        SheetAction(Icons.Rounded.History, whenSaved, last = index == backups.lastIndex) {
                            viewModel.restoreSave(game, file)
                            onDismiss()
                        }
                    }
                }
                SecondaryButton("Cancel", { restoring = false }, height = 44.dp, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton(if (hasContinue) "Continue" else "Play", onPlay, icon = Icons.Rounded.PlayArrow, height = 48.dp, modifier = Modifier.fillMaxWidth())
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Surface).border(1.dp, Stroke.copy(alpha = 0.6f), RoundedCornerShape(18.dp))) {
                    SheetAction(if (game.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (game.favorite) "Remove from favourites" else "Add to favourites",
                        tint = if (game.favorite) Coral else TextPrimary) { viewModel.toggleFavorite(game) }
                    if (hasContinue) SheetAction(Icons.Rounded.RestartAlt, "Start from the game's own save") { viewModel.startFresh(game); onDismiss() }
                    if (backups.isNotEmpty()) SheetAction(Icons.Rounded.History, "Restore an older save") { restoring = true }
                    if (hasSaves) SheetAction(Icons.Rounded.DeleteOutline, "Delete all saves", tint = Coral) { confirmDelete = true }
                    SheetAction(Icons.Rounded.RemoveCircleOutline, "Remove from library", last = true) { viewModel.removeFromLibrary(game); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Surface).border(1.dp, Stroke.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextFaint, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, tint: Color = TextPrimary, last: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickableTick(onClick).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
    if (!last) Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(1.dp).background(Stroke.copy(alpha = 0.5f)))
}

/** Click with a light tick of haptics (used by rows, chips and icon buttons). */
internal fun Modifier.clickableTick(onClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickableTick(onClick))

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableTick(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = { Haptics.tick(); onClick() })
