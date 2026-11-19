package com.ivandsky.puzzlebox.resultui.replay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
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
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.gameui.replay.ReplayPlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Test tags of the flip layout's side tabs. */
object ReplayFlipTags {
    const val TAB_MINE = "replay-flip-tab-mine"
    const val TAB_THEIRS = "replay-flip-tab-theirs"
}

private val PageGap = Spacing.md
private val UnderlineHeight = 3.dp
private val TabHeight = 48.dp
private val TabsHeight = TabHeight + UnderlineHeight
private val ControlsGap = Spacing.lg
private val NudgeDistance = 28.dp

private enum class Side { Mine, Theirs }

/**
 * Phone compare: one board at a time, centred on the screen, with the other a swipe away. Two text tabs name the
 * sides and flip the pager; so does a swipe. A flip never touches [state]'s position or playing. The first time
 * both sides show, the pager nudges toward the other board and settles back, so the swipe is discoverable; a
 * hint on the hidden side flashes on its tab. Reduced motion: no nudge, no flash, flips jump. With one side the
 * pager shows just that side and the other tab says "No replay" and cannot be selected.
 */
@Composable
internal fun ReplayFlipPeek(
    state: ReplayPlaybackState,
    playback: ReplayPlayback,
    mine: Timeline?,
    theirs: Timeline?,
    board: GameReplayBoard,
    options: ReplayBoardOptions,
    names: CompareNames,
    modifier: Modifier = Modifier,
) {
    val sides = listOfNotNull(Side.Mine.takeIf { mine != null }, Side.Theirs.takeIf { theirs != null })
    val pager = rememberPagerState { sides.size }
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val nudgePx = with(LocalDensity.current) { NudgeDistance.toPx() }
    val inspecting = LocalInspectionMode.current

    var nudged by remember { mutableStateOf(false) }
    LaunchedEffect(sides.size, reduced) {
        if (!nudged && sides.size > 1 && !reduced && !inspecting) {
            nudged = true
            pager.nudge(nudgePx)
        }
    }

    fun flipTo(side: Side) {
        val page = sides.indexOf(side)
        if (page < 0) return
        scope.launch { if (reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val boardSize = minOf(maxWidth, maxHeight - TabsHeight - CompareTransportHeight - ControlsGap * 2).coerceAtLeast(0.dp)
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
                        pastYouName = names.mine.takeIf { !isMine && names.theirsPast },
                        selected = page >= 0 && pager.targetPage == page,
                        hidden = page >= 0 && pager.targetPage != page,
                        reduced = reduced,
                        playing = state.playing,
                        onClick = { flipTo(side) },
                        modifier = Modifier.weight(1f).testTag(if (isMine) ReplayFlipTags.TAB_MINE else ReplayFlipTags.TAB_THEIRS),
                    )
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height(boardSize),
                pageSpacing = PageGap,
                beyondViewportPageCount = 1,
            ) { page ->
                val frame = if (sides[page] == Side.Mine) state.mineFrame else state.theirsFrame
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    ReplayBoardSlot(frame, board, options, boardSize)
                }
            }
            ReplayCompareTransport(state, playback, mine, theirs, names)
        }
    }
}

/** Out and back in one scroll, so a drag or a tab tap anywhere in it cancels both legs and the pager stays where the user left it. */
private suspend fun PagerState.nudge(distancePx: Float) {
    scroll {
        var last = 0f
        suspend fun travel(from: Float, to: Float) {
            animate(from, to, animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard)) { value, _ ->
                scrollBy(value - last)
                last = value
            }
        }
        travel(0f, distancePx)
        travel(distancePx, 0f)
    }
}

@Composable
private fun SideTab(
    name: String,
    frame: ReplayFrame?,
    pieces: String,
    isMine: Boolean,
    pastYouName: String?,
    selected: Boolean,
    hidden: Boolean,
    reduced: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (isMine) colors.primary else colors.tertiary
    Column(
        modifier.selectable(selected = selected, enabled = frame != null, role = Role.Tab, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(TabHeight)) {
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
                    pastYouName = pastYouName,
                )
                HintFlash(frame.facts.hints, hidden && playing && !reduced, Modifier.align(Alignment.TopEnd))
            }
        }
        Box(Modifier.fillMaxWidth().height(UnderlineHeight).background(if (selected) tint else colors.surface.copy(alpha = 0f)))
    }
}

/**
 * "+1 hint" over a hidden side's tab for a moment after its hint count rises while the replay plays. [armed] false
 * (a seek, a drag, reduced motion, the side showing) updates the count silently.
 */
@Composable
private fun HintFlash(hints: Int, armed: Boolean, modifier: Modifier = Modifier) {
    var previous by remember { mutableIntStateOf(hints) }
    var gain by remember { mutableIntStateOf(0) }
    var shown by remember { mutableIntStateOf(1) }
    LaunchedEffect(hints) {
        val delta = hints - previous
        previous = hints
        if (delta > 0 && armed) {
            shown = delta
            gain = delta
            delay(MotionTokens.FlashDuration.toLong())
        }
        gain = 0
    }
    AnimatedVisibility(
        visible = gain > 0,
        modifier = modifier.padding(end = Spacing.xs),
        enter = fadeIn(tween(MotionTokens.DurationShort)),
        exit = fadeOut(tween(MotionTokens.DurationExit)),
    ) {
        Text(ReplayCopy.hintGain(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
