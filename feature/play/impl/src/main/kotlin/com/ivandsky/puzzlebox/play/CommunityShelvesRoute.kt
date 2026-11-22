package com.ivandsky.puzzlebox.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.community.shelves.CommunityShelvesScreen
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesActions
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesPage
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesPageContent
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesPageHost
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesPageSaver
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesReportHost
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesViewModel
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllScope
import org.koin.androidx.compose.koinViewModel

/** The shelves home's list pane on the tablet: the shelf rows need more than Games' 400dp list. */
internal val ShelvesListPaneWidth = 600.dp

private const val DETAIL_ENTER_MILLIS = 280
private val DETAIL_RISE = 14.dp

/**
 * The Community tab's shelves home (epic community-browse), behind the CommunityShelves flag. On the phone it is the
 * screen; a card opens its page through [onOpen]. On [PlayLayout.TwoPane] it is a [ShelvesListPaneWidth] list pane with
 * the card's page beside it (Play opens the board through [onPlay]), which rises and fades in. Enter code runs
 * [onEnterCode]; the entry tiles, Search and a shelf's See all open a page over the home (in the list pane on the tablet) that back
 * closes, and a row picked on it opens like one on the home.
 *
 * What the old tabs did stays: a card's long press reports it (a friend's card can also be removed, [onRemoveFriend]; [removedFriends] are the ones in their Undo window, which the home and From friends leave out),
 * a share waiting in the store ([sharePending]) opens Your puzzles where the import takes it, the home and each page keep
 * their scroll, and reselecting the tab closes an open page first and then scrolls the home to the top.
 *
 * [onNewPuzzle] (null: the create button is off) is what a Make card and the create button run (null: the New puzzle
 * sheet; a game: a draft of it); the button floats over the home and every page. [clearance] is what the lists leave
 * free for it.
 */
@Composable
internal fun CommunityShelvesRoute(
    layout: PlayLayout,
    custom: CustomSlot,
    onOpen: (ShareCode) -> Unit,
    onPlay: (CustomGameKey) -> Unit,
    onEnterCode: () -> Unit,
    onNewPuzzle: ((GameId?) -> Unit)?,
    clearance: Dp,
    modifier: Modifier = Modifier,
    onRemoveFriend: (ShareCode) -> Unit = {},
    sharePending: Boolean = false,
    removedFriends: Set<ShareCode> = emptySet(),
) {
    val viewModel: ShelvesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val report by viewModel.reportState.collectAsStateWithLifecycle()
    // The page over the home: a See all page or Your puzzles, behind the entry tiles, Search and a shelf's See all.
    var page by rememberSaveable(stateSaver = ShelvesPageSaver) { mutableStateOf<ShelvesPage?>(null) }
    // Counts the opens: a page opened from the home starts clean even when it is the same one as before, while a rotation or a
    // return from a row (no open) keeps its filters. Counted on open, not close, so a page sliding out is not reset under the eye.
    var visit by rememberSaveable { mutableIntStateOf(0) }
    val openPage = { next: ShelvesPage -> page = next; visit++ }
    val homeList = rememberLazyListState()
    // A shared text waits for the import on Your puzzles, on the first look and when one arrives. It opens the page once: a
    // rotation, or a return to the home while the share still waits, does not open it again; the next share does.
    var shareOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sharePending) {
        if (!sharePending) {
            shareOpened = false
        } else if (!shareOpened) {
            shareOpened = true
            openPage(ShelvesPage.Yours)
        }
    }
    // The friends in their Undo window stop being friends' puzzles on the home and on From friends, until Undo.
    LaunchedEffect(removedFriends) { viewModel.friendsRemoved(removedFriends) }
    // The page takes the reselect first: it closes; the next one scrolls the home to the top.
    OnTabReselect { if (page != null) page = null else homeList.animateScrollToItem(0) }
    fun actionsFor(open: (ShareCode) -> Unit) = ShelvesActions(
        onEnterCode = onEnterCode,
        onOpen = open,
        onRetry = viewModel::retry,
        onSearch = { openPage(ShelvesPage.SeeAll(SeeAllScope.Search)) },
        onFriends = { openPage(ShelvesPage.SeeAll(SeeAllScope.Friends)) },
        onYours = { openPage(ShelvesPage.Yours) },
        onSeeAll = { openPage(ShelvesPage.SeeAll(it)) },
        onMake = { onNewPuzzle?.invoke(it) },
        onReport = viewModel::report,
        onRemoveFriend = onRemoveFriend,
    )
    val pages: @Composable (Boolean, (ShareCode) -> Unit, Modifier) -> Unit = { phone, open, size ->
        Box(size) {
            ShelvesPageHost(
                page = page,
                onBack = { page = null },
                modifier = Modifier.fillMaxSize(),
                home = { CommunityShelvesScreen(state, actionsFor(open), phone, Modifier.fillMaxSize(), clearance, homeList) },
                content = { ShelvesPageContent(it, custom, onBack = { page = null }, onOpen = open, visit = visit, bottomPadding = clearance, removedFriends = removedFriends) },
            )
            ShelvesReportHost(report, viewModel::noticeShown, viewModel::signIn, viewModel::dismissSignIn, Modifier.fillMaxSize(), clearance)
        }
    }
    if (layout == PlayLayout.SinglePane) {
        Box(modifier.fillMaxSize()) {
            pages(true, onOpen, Modifier.fillMaxSize())
            onNewPuzzle?.let { make -> CommunityCreateFab({ make(null) }, Modifier.align(Alignment.BottomEnd)) }
        }
    } else {
        CommunityPanes(
            default = { null },
            list = { size, picks ->
                picks.ProvideFor(CommunityTab.NewToday) {
                    val picker = LocalCommunityPicker.current
                    pages(false, { code: ShareCode -> picker?.puzzle(code.value) ?: onOpen(code) }, size)
                }
            },
            empty = {
                Text(
                    "Pick a puzzle to see it here",
                    Modifier.testTag("community.shelves.detailEmpty"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            detail = { pick, clear -> ShelvesDetail(pick) { CommunityPage(pick, clear, custom, onPlay) } },
            modifier = modifier,
            listOverlay = onNewPuzzle?.let { make -> { CommunityCreateFab({ make(null) }, Modifier.align(Alignment.BottomEnd)) } },
            listPaneWidth = ShelvesListPaneWidth,
        )
    }
}

/** The right pane's page for [pick]: it rises [DETAIL_RISE] and fades in over [DETAIL_ENTER_MILLIS] ms (still under reduced motion). */
@Composable
private fun ShelvesDetail(pick: CommunityPick, content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    val enter = remember(pick) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(pick, reduced) {
        if (reduced) enter.snapTo(1f) else enter.animateTo(1f, tween(DETAIL_ENTER_MILLIS, easing = MotionTokens.EasingStandard))
    }
    val rise = with(LocalDensity.current) { DETAIL_RISE.toPx() }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = enter.value
            translationY = (1f - enter.value) * rise
        },
        contentAlignment = Alignment.TopStart,
    ) { content() }
}
