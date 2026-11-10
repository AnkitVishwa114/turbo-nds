package com.appkraft.turbonds.ui

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import com.appkraft.turbonds.LibraryViewModel
import com.appkraft.turbonds.Screen
import com.appkraft.turbonds.ads.AdPolicy
import com.appkraft.turbonds.ads.Ads
import com.appkraft.turbonds.ads.NativeAds
import com.appkraft.turbonds.data.Engagement
import com.appkraft.turbonds.data.PlayStore
import com.appkraft.turbonds.data.ReviewPolicy
import com.appkraft.turbonds.library.GameEntry
import com.appkraft.turbonds.player.PlayerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Actions every screen can trigger: adding games and opening a game's sheet. */
class AppActions(
    val addFolder: () -> Unit,
    val addFiles: () -> Unit,
    val chooseHowToAdd: () -> Unit,
    val openGame: (GameEntry) -> Unit,
    val play: (GameEntry) -> Unit
)

@Composable
fun TurboApp(viewModel: LibraryViewModel) {
    val state = viewModel.state
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var sheetGame by remember { mutableStateOf<GameEntry?>(null) }
    var addSheet by remember { mutableStateOf(false) }
    var playOffer by remember { mutableStateOf<GameEntry?>(null) }
    var playingId by rememberSaveable { mutableStateOf<String?>(null) }

    // Folders: open the picker in Download, where most people keep their games. Android does not
    // allow Download itself (it says the folder is "not safe"), but any folder inside it works.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) viewModel.addFolder(uri)
    }
    // Single files work everywhere, Download included, so this is the always-works option.
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        viewModel.addFiles(uris)
    }
    val downloads = remember { runCatching { DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download") }.getOrNull() }
    val actions = AppActions(
        addFolder = { addSheet = false; pickFolder.launch(downloads) },
        addFiles = { addSheet = false; pickFiles.launch(arrayOf("*/*")) },
        chooseHowToAdd = { addSheet = true },
        openGame = { sheetGame = it },
        play = { viewModel.play(it) }
    )

    BackHandler(enabled = state.screen != Screen.HOME) {
        viewModel.navigate(if (state.screen == Screen.ABOUT || state.screen == Screen.PRIVACY) Screen.SETTINGS else Screen.HOME)
    }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.clearNotice()
        }
    }
    LaunchedEffect(state.scanning) { if (!state.scanning) clearArtCache() }

    // Back from a game: record play time; a minute of play may earn a rating ask, longer an ad break.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val played = Engagement.playerClosed()
            playingId?.let { id -> viewModel.recordSession(id, played) }
            playingId = null
            if (played >= ReviewPolicy.MIN_GAME_SESSION_MS) viewModel.reviewMoment()
            if (played >= AdPolicy.MIN_GAME_SESSION_MS) viewModel.adBreak()
            viewModel.checkForUpdate()
            NativeAds.ensure(context)
        }
    }

    fun startGame(game: GameEntry) {
        scope.launch {
            lifecycleOwner.withResumed {
                Engagement.playerOpened()
                playingId = game.id
                PlayerActivity.launch(context, game.id, game.title, viewModel.store.preparedFile(game.id).absolutePath)
            }
        }
    }

    // Now and then the ad-free hour is offered first; whatever the choice, the game then starts.
    LaunchedEffect(state.pendingLaunch, lifecycleOwner) {
        val game = state.pendingLaunch ?: return@LaunchedEffect
        lifecycleOwner.withResumed { }
        if (Ads.canOfferAdFreeBeforePlay(context)) playOffer = game else startGame(game)
        viewModel.consumeLaunch()
    }

    LaunchedEffect(state.askReview, state.preparing == null) {
        if (!state.askReview || state.preparing != null || state.updateVersion != null) return@LaunchedEffect
        delay(900)
        val launched = activity != null && runCatching { lifecycleOwner.withResumed { }; PlayStore.requestReview(activity) }.isSuccess
        viewModel.reviewHandled(launched)
    }

    // Interstitial only at a natural break: after leaving a game, never in the player.
    LaunchedEffect(state.adBreak, state.preparing == null) {
        if (!state.adBreak || state.preparing != null) return@LaunchedEffect
        delay(700)
        activity?.let { host ->
            lifecycleOwner.withResumed { }
            if (!state.askReview) Ads.showInterstitialIfDue(host)
        }
        viewModel.adBreakHandled()
    }

    LaunchedEffect(Ads.adFreeUntil) {
        val left = Ads.adFreeUntil - System.currentTimeMillis()
        if (left <= 0) return@LaunchedEffect
        delay(left + 1_000)
        lifecycleOwner.withResumed { }
        Ads.requestAdFreeOffer(context, renewal = true)
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        if (!state.hasSources) {
            Onboarding(actions)
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = state.screen,
                        transitionSpec = { (fadeIn(tween(240, delayMillis = 40)) + scaleIn(tween(280, easing = FastOutSlowInEasing), initialScale = 0.985f)) togetherWith fadeOut(tween(120)) },
                        label = "screen"
                    ) { screen ->
                        when (screen) {
                            Screen.HOME -> HomeScreen(viewModel, actions)
                            Screen.LIBRARY -> LibraryScreen(viewModel, actions)
                            Screen.SETTINGS -> SettingsScreen(viewModel, actions)
                            Screen.ABOUT -> AboutScreen(onBack = { viewModel.navigate(Screen.SETTINGS) })
                            Screen.PRIVACY -> PrivacyScreen(onBack = { viewModel.navigate(Screen.SETTINGS) })
                        }
                    }
                }
                if (state.screen != Screen.ABOUT && state.screen != Screen.PRIVACY) BottomBar(state.screen) { viewModel.navigate(it) }
            }
        }

        val bottomCards = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = if (state.hasSources) 76.dp else 12.dp)
        AnimatedVisibility(
            visible = Ads.offerAdFree && activity != null,
            enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
            modifier = bottomCards
        ) {
            AdFreeOffer(
                renewal = Ads.offerIsRenewal,
                onShown = { activity?.let(Ads::adFreeOfferShown) },
                onWatch = {
                    Ads.dismissAdFreeOffer()
                    activity?.let { host -> Ads.showAdFreeReward(host) { earned -> viewModel.showNotice(rewardNotice(host, earned)) } }
                },
                onClose = Ads::dismissAdFreeOffer
            )
        }

        SnackbarHost(snackbar, bottomCards) { data ->
            val light = AppColors.current.isLight
            Snackbar(
                modifier = Modifier.border(1.dp, if (light) Color.Transparent else Stroke.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                containerColor = if (light) Color(0xFF1B2230) else Raised,
                contentColor = if (light) Color(0xFFF4F7FA) else TextPrimary
            ) { Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)) }
        }
    }

    // Required update: drawn over everything, nothing else can be used until it is installed.
    if (state.updateVersion != null) {
        UpdateGate(onUpdate = viewModel::openUpdate)
        return
    }

    state.preparing?.let { game -> PreparingDialog(game, state.prepareProgress, viewModel, onCancel = viewModel::cancelPrepare) }

    if (addSheet) AddGamesSheet(actions, onDismiss = { addSheet = false })

    sheetGame?.let { game ->
        // Always show the freshest copy (favourite or play time may change while open).
        val current = state.games.firstOrNull { it.id == game.id } ?: game
        GameSheet(current, viewModel, onDismiss = { sheetGame = null }, onPlay = { sheetGame = null; viewModel.play(current) })
    }

    playOffer?.let { game ->
        PlayAdFreeDialog(
            gameTitle = game.title,
            onShown = { Ads.playOfferShown(context) },
            onWatch = {
                playOffer = null
                val host = activity
                if (host == null) startGame(game)
                else Ads.showAdFreeReward(host) { earned ->
                    if (earned) viewModel.showNotice(rewardNotice(host, true))
                    startGame(game)
                }
            },
            onPlay = {
                playOffer = null
                startGame(game)
            }
        )
    }
}

/** The message after a rewarded video: until when the user is ad-free, or why nothing played. */
internal fun rewardNotice(context: android.content.Context, earned: Boolean): String =
    if (earned) "Reward unlocked. No ads until ${android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(Ads.adFreeUntil))}"
    else "No video is available right now. Try again later."

// ---- Shared chrome -------------------------------------------------------------------------

/** Compact bottom bar: an accent pill marks the current tab, labels stay visible. */
@Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val tabs = listOf(Triple(Screen.HOME, "Home", Icons.Rounded.Home), Triple(Screen.LIBRARY, "Library", Icons.Rounded.GridView), Triple(Screen.SETTINGS, "Settings", Icons.Rounded.Settings))
    Box(Modifier.fillMaxWidth().background(Ink)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Stroke.copy(alpha = 0.6f)))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (screen, label, icon) -> NavItem(label, icon, current == screen) { if (current != screen) { Haptics.tick(); onSelect(screen) } } }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pill by animateColorAsState(if (selected) Accent.copy(alpha = 0.16f) else Color.Transparent, tween(200), label = "navPill")
    val tint by animateColorAsState(if (selected) Accent else TextFaint, tween(200), label = "navTint")
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).pressScale(interaction, 0.94f)
            .clickableNoRipple(interaction, onClick).padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.width(52.dp).height(28.dp).clip(CircleShape).background(pill), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) TextPrimary else TextFaint)
    }
}

/** Top bar used by the main tabs: title (or wordmark) on the left, actions on the right. */
@Composable
fun TopBar(title: String?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().height(Dimens.TopBarHeight).padding(horizontal = Dimens.Edge), verticalAlignment = Alignment.CenterVertically) {
        if (title == null) Wordmark(26.dp, Modifier.weight(1f))
        else Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, modifier = Modifier.weight(1f))
        actions()
    }
}

/** The "+" action that opens the add-games choice. */
@Composable
fun AddGamesButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { Haptics.tick(); onClick() }, interactionSource = interaction, shape = CircleShape,
        color = Accent.copy(alpha = 0.14f), contentColor = Accent, modifier = Modifier.height(36.dp).pressScale(interaction, 0.94f)
    ) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add games", style = MaterialTheme.typography.labelLarge)
        }
    }
}

internal fun Modifier.clickableNoRipple(interaction: MutableInteractionSource, onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
