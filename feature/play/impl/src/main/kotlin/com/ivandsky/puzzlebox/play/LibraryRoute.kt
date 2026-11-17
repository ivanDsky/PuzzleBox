package com.ivandsky.puzzlebox.play

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialHost
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.friend.EnterCodeSheet
import com.ivandsky.puzzlebox.play.linkedin.LinkedInCardState
import com.ivandsky.puzzlebox.play.linkedin.rememberLinkedInCard
import java.time.LocalDate
import java.time.YearMonth
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

/**
 * The library for [state]: list and page side by side, or the phone's list → page push.
 * [page] is the selected game's calendar and [month] the one it asked for (null = today's);
 * on the phone a picked day scrolls the page up to the day card. The header's Enter code shows
 * only with an [onEnterCode]. [friends] heads the list as From friends, each card opening its
 * puzzle through [onOpenFriend] and removing it through [onRemoveFriend]. A [duels] slot turns
 * the list into the Dailies, Friends and Duels tabs (the tab is saved here, so it survives a
 * page push); with a [custom] slot too they are Dailies, Duels and Custom, whose list holds the
 * friend puzzles and whose pill counts the ones not played yet. A [request] acts once
 * ([ConsumeLibraryRequest]; a section or a challenge needs [duels]) and is reported taken ([onRequestTaken]); a challenge
 * also leaves its [DuelPrefill] pending (saved here) for the Duels body, until the body takes it. The game page
 * shows its rank as a summary row that calls [onOpenRanks].
 * A non-null [linkedIn] puts the LinkedIn lines on the page's day card. A non-null [onMakeOne] puts "Make one" on the page.
 * With [paneCrossfade] the two-pane right pane fades to the picked game's page instead of switching in one frame ([LibraryPane]).
 */
@Composable
internal fun LibraryScreen(
    state: LibraryUiState,
    page: ArchivePageState?,
    month: YearMonth?,
    layout: PlayLayout,
    hasLesson: (GameId) -> Boolean,
    onSelect: (GameId) -> Unit,
    onPlay: (GameId, LocalDate?) -> Unit,
    onRetry: () -> Unit,
    onOpenLesson: (GameId) -> Unit,
    calendar: CalendarActions,
    onOpenRanks: (GameId, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    onEnterCode: (() -> Unit)? = null,
    friends: List<FriendCardModel> = emptyList(),
    onOpenFriend: (ShareCode) -> Unit = {},
    onRemoveFriend: (ShareCode) -> Unit = {},
    duels: DuelsSlot? = null,
    custom: CustomSlot? = null,
    request: LibraryRequest? = null,
    onRequestTaken: () -> Unit = {},
    linkedIn: LinkedInCardState? = null,
    onMakeOne: ((GameId) -> Unit)? = null,
    communityTab: Boolean = false, underlineTabs: Boolean = false,
    paneCrossfade: Boolean = false,
) {
    val listScroll = rememberScrollState()
    val friendsScroll = rememberScrollState()
    val duelsScroll = rememberLazyListState()
    val customScroll = rememberLazyListState()
    val enterCode: @Composable () -> Unit = {
        onEnterCode?.let { SecondaryButton(text = "Enter code", onClick = it, modifier = Modifier.testTag("library.enterCode")) }
    }
    val fromFriends: @Composable () -> Unit = { FromFriendsRow(friends, onOpenFriend, onRemoveFriend) }
    val onRow: (GameId) -> Unit = { game ->
        onSelect(game)
        if (state.rows.firstOrNull { it.card.game == game }?.card?.status == TodayStatus.Offline) {
            onRetry()
        }
    }
    @Composable
    fun Page(row: LibraryRowModel, onBack: (() -> Unit)?, shownPage: ArchivePageState? = page, shownMonth: YearMonth? = month) = LibraryGamePage(
        row, shownPage, shownMonth, layout, calendar, onPlay, onRetry, onOpenLesson, onBack,
        offline = state.offline, hasLesson = hasLesson(row.card.game),
        onOpenRanks = onOpenRanks, linkedIn = linkedIn, onMakeOne = onMakeOne,
    )
    val selected = state.rows.firstOrNull { it.card.game == state.selected } ?: state.rows.firstOrNull()
    var tab by rememberSaveable { mutableStateOf(GamesTab.Dailies) }
    var pageOpen by rememberSaveable { mutableStateOf(false) }
    var duelPrefill by rememberSaveable(stateSaver = DuelPrefillSaver) { mutableStateOf<DuelPrefill?>(null) }
    val showSection: suspend (GamesTab) -> Unit = { next ->
        if (duels != null) {
            tab = next
            pageOpen = false
            listScroll.scrollTo(0)
            duelsScroll.requestScrollToItem(0)
            customScroll.requestScrollToItem(0)
        }
    }
    ConsumeLibraryRequest(
        request = request,
        onSection = showSection,
        onChallenge = { challenge ->
            if (duels != null) {
                showSection(GamesTab.Duels)
                duelPrefill = challenge
            }
        },
        onTaken = onRequestTaken,
    )
    val friendsTab: @Composable () -> Unit = { FriendsTabBody(friends, onOpenFriend, onRemoveFriend, onEnterCode ?: {}) }
    val customTab: (@Composable () -> Unit)? = custom?.let {
        { CustomTabBody(it, customScroll, friends, onOpenFriend, onRemoveFriend, onEnterCode ?: {}) }
    }
    @Composable
    fun GamesList(onRow: (GameId) -> Unit, modifier: Modifier = Modifier) = LibraryList(
        state, layout, listScroll, onRow, modifier, headerAction = enterCode, friends = fromFriends,
        duels = duels, tab = tab, onTab = { tab = it }, friendsTab = friendsTab, friendCount = friends.size,
        duelsScroll = duelsScroll, customTab = customTab, customCount = friends.unplayed(), customScroll = customScroll,
        duelPrefill = duelPrefill, onDuelPrefillTaken = { duelPrefill = null }, community = communityTab,
        underlineTabs = underlineTabs, friendsScroll = friendsScroll,
    )
    if (layout == PlayLayout.TwoPane) {
        Row(modifier.fillMaxSize().padding(start = Spacing.xl, top = Spacing.lg, end = Spacing.xxl, bottom = Spacing.xl)) {
            GamesList(onRow, Modifier.width(400.dp))
            Spacer(Modifier.width(Spacing.xl))
            if (selected != null) {
                LibraryPane(LibraryPaneFrame(selected, page, month), paneCrossfade) { shown ->
                    Page(shown.row, onBack = null, shown.page, shown.month)
                }
            }
        }
    } else {
        BackHandler(enabled = pageOpen) { pageOpen = false }
        val pageShown = pageOpen && selected != null
        // The list isn't composed under the page, so the page takes the reselect: it closes first.
        if (pageShown) OnTabReselect { pageOpen = false }
        val reduced = rememberReducedMotion()
        AnimatedContent(
            targetState = pageShown,
            transitionSpec = { libraryPush(reduced) },
            label = "library-push",
            modifier = modifier.fillMaxSize(),
        ) { open ->
            if (open && selected != null) {
                Page(selected, onBack = { pageOpen = false })
            } else {
                GamesList(onRow = { game ->
                    onRow(game)
                    pageOpen = true
                })
            }
        }
    }
}
