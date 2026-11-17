package com.ivandsky.puzzlebox.play

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.linkedin.LinkedInCardState
import java.time.LocalDate
import java.time.YearMonth

/**
 * The library for [state]: list and page side by side, or the phone's list → page push.
 * [page] is the selected game's calendar and [month] the one it asked for (null = today's);
 * on the phone a picked day scrolls the page up to the day card. The header's Enter code shows
 * only with an [onEnterCode]. [friends] heads the list as From friends, each card opening its
 * puzzle through [onOpenFriend] and removing it through [onRemoveFriend]. A [duels] slot turns
 * the list into the Dailies, Duels and Custom tabs (the tab is saved here, so it survives a
 * page push); the [custom] slot's list holds the friend puzzles and its pill counts the ones not played yet. With a
 * [communityTab] Custom is not a pill: the tabs are Dailies and Duels. A [request] acts once
 * ([ConsumeLibraryRequest]; a section or a challenge needs [duels]) and is reported taken ([onRequestTaken]); a challenge
 * also leaves its [DuelPrefill] pending (saved here) for the Duels body, until the body takes it. The game page
 * shows its rank as a summary row that calls [onOpenRanks].
 * A non-null [linkedIn] puts the LinkedIn lines on the page's day card. [onMakeOne] puts "Make one" on the page.
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
    custom: CustomSlot,
    request: LibraryRequest? = null,
    onRequestTaken: () -> Unit = {},
    linkedIn: LinkedInCardState? = null,
    onMakeOne: (GameId) -> Unit,
    communityTab: Boolean = false,
    paneCrossfade: Boolean = false,
) {
    val listScroll = rememberScrollState()
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
    val customTab: @Composable () -> Unit = { CustomTabBody(custom, customScroll, friends, onOpenFriend, onRemoveFriend, onEnterCode ?: {}) }
    @Composable
    fun GamesList(onRow: (GameId) -> Unit, modifier: Modifier = Modifier) = LibraryList(
        state, layout, listScroll, onRow, modifier, headerAction = enterCode, friends = fromFriends,
        duels = duels, tab = tab, onTab = { tab = it },
        duelsScroll = duelsScroll, customTab = customTab, customCount = friends.unplayed(), customScroll = customScroll,
        duelPrefill = duelPrefill, onDuelPrefillTaken = { duelPrefill = null }, community = communityTab,
    )
    if (layout == PlayLayout.TwoPane) {
        Row(modifier.fillMaxSize().padding(start = Spacing.xl, top = Spacing.lg, end = Spacing.xxl, bottom = Spacing.xl)) {
            CompositionLocalProvider(LocalEnterSettled provides paneCrossfade) { GamesList(onRow, Modifier.width(400.dp)) }
            Spacer(Modifier.width(Spacing.xl))
            // The Duels tab's right pane is a duel, never a game's page; going back to Dailies shows the selected game again.
            if (duels != null && tab.shownWith(communityTab) == GamesTab.Duels) {
                duels.pane(Modifier.fillMaxSize())
            } else if (selected != null) {
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
