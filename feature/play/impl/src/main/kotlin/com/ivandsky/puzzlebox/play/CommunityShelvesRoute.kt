package com.ivandsky.puzzlebox.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.community.shelves.CommunityShelvesScreen
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesActions
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesPage
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesPageContent
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesPageHost
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesPageSaver
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
 * closes, and a row picked on it opens like one on the home. [onNewPuzzle] (null: the create button is off) is what a Make card runs. [clearance] is what the list
 * leaves free for the create button that floats over it.
 */
@Composable
internal fun CommunityShelvesRoute(
    layout: PlayLayout,
    custom: CustomSlot,
    onOpen: (ShareCode) -> Unit,
    onPlay: (CustomGameKey) -> Unit,
    onEnterCode: () -> Unit,
    onNewPuzzle: (() -> Unit)?,
    clearance: Dp,
    modifier: Modifier = Modifier,
) {
    val viewModel: ShelvesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The page over the home: a See all page or Your puzzles, behind the entry tiles, Search and a shelf's See all.
    var page by rememberSaveable(stateSaver = ShelvesPageSaver) { mutableStateOf<ShelvesPage?>(null) }
    // Counts the opens: a page opened from the home starts clean even when it is the same one as before, while a rotation or a
    // return from a row (no open) keeps its filters. Counted on open, not close, so a page sliding out is not reset under the eye.
    var visit by rememberSaveable { mutableIntStateOf(0) }
    val openPage = { next: ShelvesPage -> page = next; visit++ }
    fun actionsFor(open: (ShareCode) -> Unit) = ShelvesActions(
        onEnterCode = onEnterCode,
        onOpen = open,
        onRetry = viewModel::retry,
        onSearch = { openPage(ShelvesPage.SeeAll(SeeAllScope.Search)) },
        onFriends = { openPage(ShelvesPage.SeeAll(SeeAllScope.Friends)) },
        onYours = { openPage(ShelvesPage.Yours) },
        onSeeAll = { openPage(ShelvesPage.SeeAll(it)) },
        onMake = { onNewPuzzle?.invoke() },
    )
    if (layout == PlayLayout.SinglePane) {
        ShelvesPageHost(
            page = page,
            onBack = { page = null },
            modifier = modifier.fillMaxSize(),
            home = { CommunityShelvesScreen(state, actionsFor(onOpen), phone = true, Modifier.fillMaxSize(), bottomPadding = clearance) },
            content = { ShelvesPageContent(it, custom, onBack = { page = null }, onOpen = onOpen, visit = visit, bottomPadding = clearance) },
        )
    } else {
        CommunityPanes(
            default = { null },
            list = { size, picks ->
                picks.ProvideFor(CommunityTab.NewToday) {
                    val picker = LocalCommunityPicker.current
                    val open = { code: ShareCode -> picker?.puzzle(code.value) ?: onOpen(code) }
                    ShelvesPageHost(
                        page = page,
                        onBack = { page = null },
                        modifier = size,
                        home = { CommunityShelvesScreen(state, actionsFor(open), phone = false, Modifier.fillMaxSize(), bottomPadding = clearance) },
                        content = { ShelvesPageContent(it, custom, onBack = { page = null }, onOpen = open, visit = visit, bottomPadding = clearance) },
                    )
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
            listOverlay = onNewPuzzle?.let { open -> { CommunityCreateFab(open, Modifier.align(Alignment.BottomEnd)) } },
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
