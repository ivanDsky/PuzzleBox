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
import androidx.compose.runtime.remember
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
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesViewModel
import org.koin.androidx.compose.koinViewModel

/** The shelves home's list pane on the tablet: the shelf rows need more than Games' 400dp list. */
internal val ShelvesListPaneWidth = 600.dp

private const val DETAIL_ENTER_MILLIS = 280
private val DETAIL_RISE = 14.dp

/**
 * The Community tab's shelves home (epic community-browse), behind the CommunityShelves flag. On the phone it is the
 * screen; a card opens its page through [onOpen]. On [PlayLayout.TwoPane] it is a [ShelvesListPaneWidth] list pane with
 * the card's page beside it (Play opens the board through [onPlay]), which rises and fades in. Enter code runs
 * [onEnterCode]; [onNewPuzzle] (null: the create button is off) is what a Make card runs. [clearance] is what the list
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
    if (layout == PlayLayout.SinglePane) {
        val actions = ShelvesActions(onEnterCode, onOpen, viewModel::retry, onMake = { onNewPuzzle?.invoke() })
        CommunityShelvesScreen(state, actions, phone = true, modifier.fillMaxSize(), bottomPadding = clearance)
    } else {
        CommunityPanes(
            default = { null },
            list = { size, picks ->
                picks.ProvideFor(CommunityTab.NewToday) {
                    val picker = LocalCommunityPicker.current
                    val open = { code: ShareCode -> picker?.puzzle(code.value) ?: onOpen(code) }
                    val actions = ShelvesActions(onEnterCode, open, viewModel::retry, onMake = { onNewPuzzle?.invoke() })
                    CommunityShelvesScreen(state, actions, phone = false, size, bottomPadding = clearance)
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
