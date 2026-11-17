package com.ivandsky.puzzlebox.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialHost
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.friend.EnterCodeSheet
import com.ivandsky.puzzlebox.play.linkedin.rememberLinkedInCard
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
 * A [custom] slot beside the [duels] one makes the tabs Dailies, Duels, Custom ([LibraryScreen]).
 * The game page shows its rank as a summary row that calls [onOpenRanks].
 * With [linkedIn] the day card shows the LinkedIn lines, and [onReconnectLinkedIn] opens its login.
 * A non-null [onMakeOne] puts "Make one" on each game page. With a [communityTab] Custom is not a Games pill: the
 * pills are Dailies and Duels, and the friend puzzles live on that tab ([CommunityRoute]). [underlineTabs]: see [LibraryList].
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
    custom: CustomSlot? = null,
    request: LibraryRequest? = null,
    onRequestTaken: () -> Unit = {},
    linkedIn: Boolean = false,
    onReconnectLinkedIn: (() -> Unit)? = null,
    onMakeOne: ((GameId) -> Unit)? = null,
    communityTab: Boolean = false, underlineTabs: Boolean = false,
) {
    val viewModel: LibraryViewModel = koinViewModel(parameters = { parametersOf(linkedIn) })
    val registry: GamePlayRegistry = koinInject()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lesson by viewModel.lesson.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val month by viewModel.shownMonth.collectAsStateWithLifecycle()
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
    LifecycleStartEffect(viewModel) {
        viewModel.syncToday()
        onStopOrDispose { }
    }
    Box(modifier.fillMaxSize().testTag("route.library")) {
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
            friends = friends,
            onOpenFriend = onOpenCode,
            onRemoveFriend = viewModel::removeFriend,
            duels = duels,
            custom = custom,
            request = request,
            onRequestTaken = onRequestTaken,
            linkedIn = linkedInCard,
            onOpenRanks = onOpenRanks,
            onMakeOne = onMakeOne,
            communityTab = communityTab, underlineTabs = underlineTabs,
            paneCrossfade = rememberPaneCrossfade(),
        )
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
        val open = lesson
        val ui = open?.let { registry[it.game] }
        if (open != null && ui != null) {
            BackHandler { viewModel.closeLesson() }
            PlayFeelHost {
                TutorialHost(
                    ui = ui,
                    session = open.session,
                    onFinished = viewModel::closeLesson,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                )
            }
        }
    }
}
