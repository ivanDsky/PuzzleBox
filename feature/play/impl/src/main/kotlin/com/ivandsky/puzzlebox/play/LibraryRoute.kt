package com.ivandsky.puzzlebox.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.play.domain.random.RandomPick
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialHost
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.friend.EnterCodeSheet
import com.ivandsky.puzzlebox.play.linkedin.rememberLinkedInCard
import com.ivandsky.puzzlebox.play.random.RandomPuzzleSheet
import com.ivandsky.puzzlebox.play.random.archiveGames
import com.ivandsky.puzzlebox.play.random.communityGamesOf
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * The Play tab's root: the games library. [PlayLayout.TwoPane] shows the
 * list beside the selected game's page, a game always selected; [PlayLayout.SinglePane]
 * shows the list and pushes a game's page over it, back returning to the list. The day
 * card's action is [onPlay] with the shown day, null for today (the entry pushes the
 * game's key, so back from the board returns here). The page's calendar picks the
 * day card's day and pages months ([LibraryViewModel.page]). A row whose today can't load also retries; each start prefetches
 * today's uncached puzzles ([LibraryViewModel.syncToday]). "Open the lesson" runs
 * the game's tutorial over the library. The header's Enter code opens the code sheet, whose
 * Open is [onOpenCode]. A From friends card's Remove shows "Removed <code>" with Undo. A
 * [codeDraft] opens the sheet with that text already in the field, then is taken
 * ([onCodeDraftTaken]); a [request] opens a game's page or a tab, then is taken ([onRequestTaken]).
 * The game page shows its rank as a summary row that calls [onOpenRanks].
 * With [linkedIn] the day card shows the LinkedIn lines, and [onReconnectLinkedIn] opens its login.
 * [onMakeOne] puts "Make one" on each game page. The friend puzzles and Custom's list are the app's Community
 * tab ([CommunityRoute]); Games shows Dailies and Duels.
 * Each game page's calendar has Random: it opens the random sheet for that game, and its
 * pick plays a past day through [onPlay], as a fresh play again when it is already solved, or opens a New today puzzle's card through [onOpenCode], the sheet closing first.
 */
@Composable
internal fun LibraryRoute(
    layout: PlayLayout,
    onPlay: (GameId, LocalDate?) -> Unit,
    onOpenCode: (ShareCode) -> Unit,
    onOpenRanks: (GameId, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    codeDraft: String? = null,
    onCodeDraftTaken: () -> Unit = {},
    duels: DuelsSlot? = null,
    request: LibraryRequest? = null,
    onRequestTaken: () -> Unit = {},
    linkedIn: Boolean = false,
    onReconnectLinkedIn: (() -> Unit)? = null,
    onMakeOne: (GameId) -> Unit,
) {
    val viewModel: LibraryViewModel = koinViewModel(parameters = { parametersOf(linkedIn) })
    val registry: GamePlayRegistry = koinInject()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lesson by viewModel.lesson.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val month by viewModel.shownMonth.collectAsStateWithLifecycle()
    val monthSwipe by viewModel.monthSwipe.collectAsStateWithLifecycle()
    val makeOwnLink by viewModel.makeOwnLink.collectAsStateWithLifecycle()
    // The card collects the view itself, so the route never starts a LinkedIn read.
    val linkedInCard = rememberLinkedInCard(viewModel, onReconnectLinkedIn)
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val removedFriend by viewModel.removedFriend.collectAsStateWithLifecycle()
    var enteringCode by rememberSaveable { mutableStateOf(false) }
    var codePrefill by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(codeDraft) {
        if (codeDraft != null) {
            codePrefill = codeDraft
            enteringCode = true
            onCodeDraftTaken()
        }
    }
    // A closed sheet forgets the draft: the next Enter code opens empty.
    if (!enteringCode && codePrefill.isNotEmpty()) LaunchedEffect(Unit) { codePrefill = "" }
    // The game whose random sheet is open, null while it is closed.
    var randomGame by rememberSaveable { mutableStateOf<String?>(null) }
    LifecycleStartEffect(viewModel) {
        viewModel.syncToday()
        onStopOrDispose { }
    }
    Box(modifier.fillMaxSize().testTag("route.library")) {
        CompositionLocalProvider(LocalArchiveMonthSwipe provides monthSwipe, LocalMakeOwnLink provides makeOwnLink) {
            LibraryScreen(
                state = state,
                page = page,
                month = month,
                layout = layout,
                hasLesson = { registry[it]?.tutorial?.isNotEmpty() == true },
                onSelect = viewModel::select,
                onPlay = onPlay,
                onRetry = viewModel::syncToday,
                onOpenLesson = viewModel::openLesson,
                calendar = CalendarActions(
                    onShowMonth = viewModel::showMonth,
                    onPick = viewModel::pick,
                    onBackToToday = viewModel::backToToday,
                ),
                onEnterCode = { enteringCode = true },
                onRandom = { game -> randomGame = game.value },
                friends = friends,
                onOpenFriend = onOpenCode,
                onRemoveFriend = viewModel::removeFriend,
                duels = duels,
                request = request,
                onRequestTaken = onRequestTaken,
                linkedIn = linkedInCard,
                onOpenRanks = onOpenRanks,
                onMakeOne = onMakeOne,
                largeBar = rememberMainTabsLargeBar(),
            )
        }
        FriendRemovedSnackbar(removedFriend, onUndo = viewModel::undoRemoveFriend, modifier = Modifier.align(Alignment.BottomCenter))
        if (enteringCode) {
            EnterCodeSheet(
                onOpen = { code ->
                    enteringCode = false
                    onOpenCode(code)
                },
                onDismiss = { enteringCode = false },
                initialText = codePrefill,
                onOpenDuel = duels?.onOpenDuel?.let { openDuel ->
                    { key ->
                        enteringCode = false
                        openDuel(key)
                    }
                },
            )
        }
        randomGame?.let { randomOf ->
            val games: GameRegistry = koinInject()
            val store: PlaySessionStore = koinInject()
            RandomPuzzleSheet(
                game = GameId(randomOf),
                games = state.rows.archiveGames(),
                communityGames = remember(games, registry) { communityGamesOf(games, registry) },
                layout = layout,
                onOpen = { pick ->
                    randomGame = null
                    when (pick) {
                        // A solved day would open its result; Random's pick is always a board to play.
                        is RandomPick.Day -> {
                            store.playAgain(pick.game, pick.day)
                            onPlay(pick.game, pick.day)
                        }
                        is RandomPick.Shared -> onOpenCode(pick.code)
                    }
                },
                onDismiss = { randomGame = null },
            )
        }
        val open = lesson
        val ui = open?.let { registry[it.game] }
        if (open != null && ui != null) {
            BackHandler { viewModel.closeLesson() }
            TutorialHost(
                ui = ui,
                session = open.session,
                onFinished = viewModel::closeLesson,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            )
        }
    }
}
