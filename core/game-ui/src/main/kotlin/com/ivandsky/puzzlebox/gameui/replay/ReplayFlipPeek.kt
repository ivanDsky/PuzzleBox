package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.game.replay.Timeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Test tags of the flip layout's side tabs. */
object ReplayFlipTags {
    const val TAB_MINE = "replay-flip-tab-mine"
    const val TAB_THEIRS = "replay-flip-tab-theirs"
}

private val PeekWidth = 36.dp
private val PageGap = Spacing.md
private val UnderlineHeight = 3.dp
private val TabsHeight = SideHeaderHeight + UnderlineHeight
private val ControlsGap = Spacing.lg
private val NudgeDistance = 28.dp

private enum class Side { Mine, Theirs }

/**
 * Phone compare: one board fills the width and the edge of the other peeks in beside it. Two text tabs name the
 * sides and flip the pager; so does a swipe. A flip never touches [state]'s position or playing. The first time
 * both sides show, the pager nudges toward the other board and settles back; a hint on the hidden side flashes
 * on its tab. Reduced motion: no nudge, no flash, flips jump. With one side the pager shows just that side and
 * the other tab says "No replay" and cannot be selected.
 */
@Composable
internal fun ReplayFlipPeek(
    state: ReplayPlaybackState,
    playback: ReplayPlayback,
    mine: Timeline?,
    theirs: Timeline?,
    board: GameReplayBoard,
    names: CompareNames,
    modifier: Modifier = Modifier,
) {
    val sides = listOfNotNull(Side.Mine.takeIf { mine != null }, Side.Theirs.takeIf { theirs != null })
    val pager = rememberPagerState { sides.size }
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val nudgePx = with(LocalDensity.current) { NudgeDistance.toPx() }
    val inspecting = LocalInspectionMode.current

    LaunchedEffect(sides.size, reduced) {
        if (sides.size > 1 && !reduced && !inspecting) pager.nudge(nudgePx)
    }

    fun flipTo(side: Side) {
        val page = sides.indexOf(side)
        if (page < 0) return
        scope.launch { if (reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val pageWidth = (maxWidth - PeekWidth - PageGap).coerceAtLeast(0.dp)
        val boardSize = minOf(pageWidth, maxHeight - TabsHeight - CompareTransportHeight - ControlsGap * 2).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ControlsGap)) {
            Row(Modifier.fillMaxWidth()) {
                Side.entries.forEach { side ->
                    val isMine = side == Side.Mine
                    val frame = if (isMine) state.mineFrame else state.theirsFrame
                    val page = sides.indexOf(side)
                    SideTab(
                        name = if (isMine) names.mine else names.theirs,
                        frame = frame,
                        pieces = board.pieces,
                        isMine = isMine,
                        selected = page >= 0 && pager.targetPage == page,
                        hidden = page >= 0 && pager.targetPage != page,
                        reduced = reduced,
                        onClick = { flipTo(side) },
                        modifier = Modifier.weight(1f).testTag(if (isMine) ReplayFlipTags.TAB_MINE else ReplayFlipTags.TAB_THEIRS),
                    )
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height(boardSize),
                pageSize = PageSize.Fixed(pageWidth),
                pageSpacing = PageGap,
                snapPosition = EdgeSnap,
                beyondViewportPageCount = 1,
            ) { page ->
                val frame = if (sides[page] == Side.Mine) state.mineFrame else state.theirsFrame
                Box(Modifier.width(pageWidth), contentAlignment = Alignment.TopCenter) {
                    ReplayBoardSlot(frame, board, boardSize)
                }
            }
            ReplayCompareTransport(state, playback, mine, theirs, names)
        }
    }
}

/** The first page sits at the start edge and the last at the end edge, so the other board peeks in on the inside. */
private val EdgeSnap = object : SnapPosition {
    override fun position(
        layoutSize: Int,
        itemSize: Int,
        beforeContentPadding: Int,
        afterContentPadding: Int,
        itemIndex: Int,
        itemCount: Int,
    ): Int =
        if (itemIndex == itemCount - 1 && itemCount > 1) layoutSize - afterContentPadding - itemSize else beforeContentPadding
}

private suspend fun PagerState.nudge(distancePx: Float) {
    animateScrollBy(distancePx, tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard))
    animateScrollBy(-distancePx, tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard))
}

@Composable
private fun SideTab(
    name: String,
    frame: ReplayFrame?,
    pieces: String,
    isMine: Boolean,
    selected: Boolean,
    hidden: Boolean,
    reduced: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (isMine) colors.primary else colors.tertiary
    Column(
        modifier.selectable(selected = selected, enabled = frame != null, role = Role.Tab, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(SideHeaderHeight)) {
            if (frame == null) {
                Text(
                    ReplayCopy.NO_REPLAY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            } else {
                ReplaySideHeader(
                    name = name,
                    facts = frame.facts,
                    pieces = pieces,
                    tint = tint,
                    onTint = if (isMine) colors.onPrimary else colors.onTertiary,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                HintFlash(frame.facts.hints, hidden && !reduced, Modifier.align(Alignment.TopEnd))
            }
        }
        Box(Modifier.fillMaxWidth().height(UnderlineHeight).background(if (selected) tint else colors.surface.copy(alpha = 0f)))
    }
}

/** "+1 hint" over a hidden side's tab for a moment after its hint count rises; nothing while [armed] is false. */
@Composable
private fun HintFlash(hints: Int, armed: Boolean, modifier: Modifier = Modifier) {
    var previous by remember { mutableIntStateOf(hints) }
    var gain by remember { mutableIntStateOf(0) }
    LaunchedEffect(hints) {
        val delta = hints - previous
        previous = hints
        if (delta > 0 && armed) {
            gain = delta
            delay(MotionTokens.FlashDuration.toLong())
        }
        gain = 0
    }
    var shown by remember { mutableIntStateOf(1) }
    if (gain > 0) shown = gain
    AnimatedVisibility(
        visible = gain > 0,
        modifier = modifier.padding(end = Spacing.xs),
        enter = fadeIn(tween(MotionTokens.DurationShort)),
        exit = fadeOut(tween(MotionTokens.DurationExit)),
    ) {
        Text(ReplayCopy.hintGain(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
