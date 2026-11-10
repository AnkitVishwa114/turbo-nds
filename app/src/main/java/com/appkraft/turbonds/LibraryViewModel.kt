package com.appkraft.turbonds

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appkraft.turbonds.ads.Ads
import com.appkraft.turbonds.data.Engagement
import com.appkraft.turbonds.data.PlayStore
import com.appkraft.turbonds.library.GameEntry
import com.appkraft.turbonds.library.GameFileProblem
import com.appkraft.turbonds.library.GameStore
import com.appkraft.turbonds.library.SaveStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class Screen { HOME, LIBRARY, SETTINGS, ABOUT, PRIVACY }
enum class LibraryTab { ALL, RECENT, FAVORITES }
enum class SortOrder { RECENT, NAME, PLAY_TIME }

data class LibraryState(
    val screen: Screen = Screen.HOME,
    val games: List<GameEntry> = emptyList(),
    val folders: List<Uri> = emptyList(),
    /** Game files added one by one. */
    val files: List<Uri> = emptyList(),
    val scanning: Boolean = false,
    val scanCount: Int = 0,
    val query: String = "",
    val tab: LibraryTab = LibraryTab.ALL,
    val sort: SortOrder = SortOrder.RECENT,
    /** Game being copied/extracted before it starts, with progress (null = indeterminate). */
    val preparing: GameEntry? = null,
    val prepareProgress: Float? = null,
    /** Ready to open in the player from the visible screen. */
    val pendingLaunch: GameEntry? = null,
    val notice: String? = null,
    val askReview: Boolean = false,
    val adBreak: Boolean = false,
    val updateVersion: Int? = null
) {
    /** The game to resume from the home screen: last played with a resume point. */
    fun continueGame(saves: SaveStore): GameEntry? =
        games.filter { it.lastPlayed > 0 && saves.hasContinue(it.id) }.maxByOrNull { it.lastPlayed }

    /** True once the user gave the app at least one folder or file. */
    val hasSources: Boolean get() = folders.isNotEmpty() || files.isNotEmpty()

    val totalPlayTimeMs: Long get() = games.sumOf { it.playTimeMs }

    val recentlyPlayed: List<GameEntry> get() = games.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }
    val favorites: List<GameEntry> get() = games.filter { it.favorite }.sortedBy { it.title.lowercase() }
    val recentlyAdded: List<GameEntry> get() = games.sortedByDescending { it.addedAt }

    val visibleGames: List<GameEntry>
        get() {
            val base = when (tab) {
                LibraryTab.ALL -> games
                LibraryTab.RECENT -> games.filter { it.lastPlayed > 0 }
                LibraryTab.FAVORITES -> games.filter { it.favorite }
            }
            val filtered = if (query.isBlank()) base else base.filter {
                it.title.contains(query.trim(), ignoreCase = true) || it.fileName.contains(query.trim(), ignoreCase = true)
            }
            return when (sort) {
                SortOrder.RECENT -> filtered.sortedWith(compareByDescending<GameEntry> { it.lastPlayed }.thenByDescending { it.addedAt }.thenBy { it.title.lowercase() })
                SortOrder.NAME -> filtered.sortedBy { it.title.lowercase() }
                SortOrder.PLAY_TIME -> filtered.sortedByDescending { it.playTimeMs }
            }
        }
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    val store = GameStore(application)
    val saves = SaveStore(application)
    private var scanJob: Job? = null
    private var prepareJob: Job? = null
    private var updateCheck: Job? = null

    var state by mutableStateOf(LibraryState(games = store.load(), folders = store.folders(), files = store.files()))
        private set

    private val updatePrefs = application.getSharedPreferences("updates", android.content.Context.MODE_PRIVATE)

    init {
        // A newer version seen earlier stays required, even offline, until it is installed.
        updatePrefs.getInt("required", 0).takeIf { it > BuildConfig.VERSION_CODE }?.let { state = state.copy(updateVersion = it) }
        if (state.hasSources) rescan(quiet = true)
    }

    // ---- Navigation and filters ---------------------------------------------------------------

    fun navigate(screen: Screen) { state = state.copy(screen = screen) }
    fun setQuery(value: String) { state = state.copy(query = value.take(60)) }
    fun setTab(tab: LibraryTab) { state = state.copy(tab = tab) }
    fun setSort(sort: SortOrder) { state = state.copy(sort = sort) }
    fun showNotice(message: String) { state = state.copy(notice = message) }
    fun clearNotice() { state = state.copy(notice = null) }

    // ---- Folders and scanning --------------------------------------------------------------

    fun addFolder(tree: Uri) {
        runCatching { store.addFolder(tree) }
            .onFailure { showNotice("That folder can't be used. Try another one.") }
            .onSuccess {
                state = state.copy(folders = store.folders())
                rescan(quiet = false)
            }
    }

    /** Single files picked with the system file picker (works in Download, where folders can't be picked). */
    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val added = store.addFiles(uris)
        state = state.copy(files = store.files())
        if (added == 0) showNotice("Pick .nds, .zip or .7z files.") else rescan(quiet = false)
    }

    fun removeFolder(tree: Uri) {
        store.removeFolder(tree)
        state = state.copy(folders = store.folders())
        rescan(quiet = true)
    }

    fun rescan(quiet: Boolean = false) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            state = state.copy(scanning = true, scanCount = 0)
            val before = state.games.size
            runCatching { store.scan { count -> state = state.copy(scanCount = count) } }
                .onSuccess { games ->
                    state = state.copy(games = games, scanning = false)
                    if (!quiet) {
                        val added = games.size - before
                        showNotice(when {
                            games.isEmpty() -> "No DS games found there"
                            added > 0 -> "$added new game${if (added == 1) "" else "s"} added"
                            else -> "Library is up to date"
                        })
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    state = state.copy(scanning = false)
                    showNotice("Scan stopped. Try again.")
                }
        }
    }

    // ---- Games -------------------------------------------------------------------------------

    fun toggleFavorite(game: GameEntry) {
        state = state.copy(games = store.update(game.id) { it.copy(favorite = !it.favorite) })
    }

    fun removeFromLibrary(game: GameEntry) {
        state = state.copy(games = store.remove(game.id), files = store.files())
        showNotice("Removed. File and saves kept.")
    }

    fun startFresh(game: GameEntry) {
        saves.clearContinue(game.id)
        showNotice("Next start uses the in-game save")
    }

    fun restoreSave(game: GameEntry, backup: java.io.File) {
        runCatching { saves.restore(game.id, backup) }
            .onSuccess { showNotice("Older save restored") }
            .onFailure { showNotice(it.message ?: "The save could not be restored") }
    }

    fun deleteSaves(game: GameEntry) {
        saves.deleteAll(game.id)
        showNotice("All saves of ${game.title} deleted")
    }

    /** Copies the game into private storage if needed, then asks the screen to open the player. */
    fun play(game: GameEntry) {
        if (prepareJob?.isActive == true) return
        prepareJob = viewModelScope.launch {
            state = state.copy(preparing = game, prepareProgress = null)
            try {
                store.prepare(game) { progress -> state = state.copy(prepareProgress = progress) }
                state = state.copy(preparing = null, pendingLaunch = game)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (problem: GameFileProblem) {
                showNotice(problem.message ?: "The game could not be opened.")
            } catch (problem: Exception) {
                showNotice("The game could not be opened. Please try again.")
            } finally {
                state = state.copy(preparing = null, prepareProgress = null)
            }
        }
    }

    fun cancelPrepare() { prepareJob?.cancel() }

    fun consumeLaunch() { state = state.copy(pendingLaunch = null) }

    /** Called when the player returns: records play time and recency. */
    fun recordSession(id: String, playedMs: Long) {
        if (playedMs <= 0) return
        state = state.copy(games = store.update(id) { it.copy(lastPlayed = System.currentTimeMillis(), playTimeMs = it.playTimeMs + playedMs) })
    }

    // ---- Review, ad breaks, updates (same rules as the other AppKraft apps) -------------------

    fun reviewMoment() {
        if (!state.askReview && Engagement.reviewDue()) state = state.copy(askReview = true)
    }

    fun reviewHandled(launched: Boolean) {
        if (launched) {
            Engagement.reviewAsked()
            Ads.quietSession = true
        }
        state = state.copy(askReview = false)
    }

    fun adBreak() { if (!state.askReview) state = state.copy(adBreak = true) }
    fun adBreakHandled() { state = state.copy(adBreak = false) }

    fun checkForUpdate() {
        if (state.updateVersion != null || updateCheck?.isActive == true) return
        updateCheck = viewModelScope.launch {
            val version = PlayStore.availableUpdate(getApplication())?.takeIf { it > BuildConfig.VERSION_CODE } ?: return@launch
            updatePrefs.edit().putInt("required", version).apply()
            state = state.copy(updateVersion = version)
        }
    }

    fun openUpdate() { PlayStore.openListing(getApplication()) }
}
