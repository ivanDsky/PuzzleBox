package com.ivandsky.puzzlebox.play

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
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
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The Play tab's root (A25–A29, N2): the games library. [PlayLayout.TwoPane] shows the
 * list beside the selected game's page, a game always selected; [PlayLayout.SinglePane]
 * shows the list and pushes a game's page over it, back returning to the list. The day
 * card's action is [onPlay] with the shown day, null for today (the entry pushes the
 * game's key, so back from the board returns here, N3). The page's calendar picks the
 * day card's day and pages months ([LibraryViewModel.page]). A row whose today can't load also retries; each start prefetches
 * today's uncached puzzles ([LibraryViewModel.syncToday], A12). "Open the lesson" runs
 * the game's tutorial over the library. The header's Enter code opens the code sheet, whose
 * Open is [onOpenCode]. Enter code and From friends show only while [ReleaseFlag.FriendSharing] is
 * on (ADR-0022). A From friends card's Remove shows "Removed <code>" with Undo. A [codeDraft] opens
 * the sheet with that text already in the field, then is taken ([onCodeDraftTaken]).
 */
@Composable
internal fun LibraryRoute(
    layout: PlayLayout,
    onPlay: (GameId, LocalDate?) -> Unit,
    onOpenCode: (ShareCode) -> Unit,
    modifier: Modifier = Modifier,
    codeDraft: String? = null,
    onCodeDraftTaken: () -> Unit = {},
) {
    val viewModel: LibraryViewModel = koinViewModel()
    val registry: GamePlayRegistry = koinInject()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lesson by viewModel.lesson.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val month by viewModel.shownMonth.collectAsStateWithLifecycle()
    val flags by koinInject<ReleaseFlags>().enabled.collectAsStateWithLifecycle()
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
            onEnterCode = { enteringCode = true }.takeIf { ReleaseFlag.FriendSharing in flags },
            friends = friends.takeIf { ReleaseFlag.FriendSharing in flags }.orEmpty(),
            onOpenFriend = onOpenCode,
            onRemoveFriend = viewModel::removeFriend,
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

/** The calendar's callbacks: page a month, pick the day card's day, return to today. */
internal class CalendarActions(
    val onShowMonth: (YearMonth) -> Unit = {},
    val onPick: (LocalDate) -> Unit = {},
    val onBackToToday: () -> Unit = {},
)

/**
 * The library for [state]: list and page side by side, or the phone's list → page push.
 * [page] is the selected game's calendar and [month] the one it asked for (null = today's);
 * on the phone a picked day scrolls the page up to the day card. The header's Enter code shows
 * only with an [onEnterCode]. [friends] heads the list as From friends, each card opening its
 * puzzle through [onOpenFriend] and removing it through [onRemoveFriend].
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
    modifier: Modifier = Modifier,
    onEnterCode: (() -> Unit)? = null,
    friends: List<FriendCardModel> = emptyList(),
    onOpenFriend: (ShareCode) -> Unit = {},
    onRemoveFriend: (ShareCode) -> Unit = {},
) {
    val listScroll = rememberScrollState()
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
    fun Page(row: LibraryRowModel, onBack: (() -> Unit)?) {
        val game = row.card.game
        val shown = page?.takeIf { it.game == game }
        val past = shown?.picked
        // This game's source, not the library's: another game's failed today says nothing about it.
        val unreachable = row.card.status == TodayStatus.Offline
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val reduced = rememberReducedMotion()
        GamePage(
            row = row,
            layout = layout,
            dayCard = {
                ArchiveDayCard(
                    row = row,
                    today = row.progress?.range?.endInclusive,
                    past = past,
                    offline = unreachable,
                    onPlay = { onPlay(game, past?.date) },
                    onRetry = onRetry,
                    onBackToToday = calendar.onBackToToday,
                )
            },
            calendar = {
                val range = row.progress?.range
                if (range != null) {
                    val shownMonth = month ?: YearMonth.from(range.endInclusive)
                    ArchiveMonthCalendar(
                        range = range,
                        month = shownMonth,
                        // Until the month's days load, its discs draw as outside rather than the old month's.
                        days = shown?.takeIf { it.month == shownMonth }?.days.orEmpty(),
                        picked = past?.date,
                        offline = unreachable,
                        accent = libraryCardColors(row.card.brand).fill,
                        compact = layout == PlayLayout.SinglePane,
                        onMonth = calendar.onShowMonth,
                        onPick = { day ->
                            calendar.onPick(day)
                            if (layout == PlayLayout.SinglePane) {
                                scope.launch { if (reduced) scroll.scrollTo(0) else scroll.animateScrollTo(0) }
                            }
                        },
                        modifier = if (layout == PlayLayout.TwoPane) Modifier.fillMaxHeight() else Modifier,
                    )
                }
            },
            onBack = onBack,
            onOpenLesson = { onOpenLesson(game) },
            offline = state.offline,
            hasLesson = hasLesson(game),
            scroll = scroll,
        )
    }
    val selected = state.rows.firstOrNull { it.card.game == state.selected } ?: state.rows.firstOrNull()
    if (layout == PlayLayout.TwoPane) {
        Row(modifier.fillMaxSize().padding(start = Spacing.xl, top = Spacing.lg, end = Spacing.xxl, bottom = Spacing.xl)) {
            LibraryList(state, layout, listScroll, onRow, Modifier.width(400.dp), headerAction = enterCode, friends = fromFriends)
            Spacer(Modifier.width(Spacing.xl))
            if (selected != null) {
                key(selected.card.game) {
                    Box(Modifier.fillMaxWidth()) { Page(selected, onBack = null) }
                }
            }
        }
    } else {
        var pageOpen by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = pageOpen) { pageOpen = false }
        val reduced = rememberReducedMotion()
        AnimatedContent(
            targetState = pageOpen && selected != null,
            transitionSpec = { libraryPush(reduced) },
            label = "library-push",
            modifier = modifier.fillMaxSize(),
        ) { open ->
            if (open && selected != null) {
                Page(selected, onBack = { pageOpen = false })
            } else {
                LibraryList(
                    state = state,
                    layout = layout,
                    scroll = listScroll,
                    onRow = { game ->
                        onRow(game)
                        pageOpen = true
                    },
                    headerAction = enterCode,
                    friends = fromFriends,
                )
            }
        }
    }
}
