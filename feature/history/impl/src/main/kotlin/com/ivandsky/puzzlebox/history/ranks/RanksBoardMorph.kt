package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.MorphCell
import com.ivandsky.puzzlebox.designsystem.ScrubCell
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.board.BoardFace
import com.ivandsky.puzzlebox.gameui.board.BoardMorph
import com.ivandsky.puzzlebox.gameui.rankRuleText

/**
 * The board slot between two of the hub's boards: the slice it leaves, [from], the one it goes to, [to], and how far
 * along it is, [progress] (0 to 1, read only where things are laid out and drawn). [board] is the same for the kit.
 */
@Stable
internal class RanksBoardMorph(val from: RanksBoardSlice, val to: RanksBoardSlice, val progress: () -> Float) {
    val board = BoardMorph(from.face(), to.face(), progress)
}

private fun RanksBoardSlice.face() = BoardFace(title, order, state, caption ?: rankRuleText(order))

/** The board the hub is on its way to: the scrub's, or the one just picked at [position], for the one composition before the scrub hears of it. */
internal fun BoardScrub.target(position: Int): Int = if (to != from) to else position

/** The slice of the board at [index]: the one on screen as it is drawn (a held one stays held), any other as the hub holds it. */
internal fun RanksUiState.sliceAt(index: Int): RanksBoardSlice? = if (index == position) board else boards.getOrNull(index)

/**
 * The morph [scrub] is in the middle of, over [state]'s boards; null at rest, under reduced motion, and when the hub
 * does not hold both boards. It is the same morph for as long as the two boards are, so a swipe that commits (the hub
 * moves on to the board the scrub is still arriving at) changes nothing that is drawn.
 */
@Composable
internal fun rememberBoardMorph(state: RanksUiState, scrub: BoardScrub): RanksBoardMorph? {
    val reduced = rememberReducedMotion()
    val from = scrub.from
    val target = scrub.target(state.position)
    val leaving = state.sliceAt(from)
    val arriving = state.sliceAt(target)
    if (reduced || target == from || leaving == null || arriving == null) return null
    return remember(leaving, arriving, scrub) { RanksBoardMorph(leaving, arriving) { scrub.progress } }
}

/**
 * A piece of the hub's text that differs from board to board. At rest it is [resting], and a new value frosts over the
 * old one on its own ([MorphCell]). While the hub is between two boards it is the two boards' values ([at] a board's
 * index), frosted from the one to the other as far as [scrub] is ([ScrubCell]), so it follows the finger. Reduced
 * motion, no [scrub] and a board with no value leave it [resting]. The hub is on the board at [position].
 */
@Composable
internal fun <T : Any> BoardScrubCell(
    scrub: BoardScrub?,
    position: Int,
    resting: T,
    modifier: Modifier = Modifier,
    at: (Int) -> T?,
    content: @Composable (T) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val from = scrub?.from ?: position
    val target = scrub?.target(position) ?: position
    val leaving = at(from)
    val arriving = at(target)
    if (scrub == null || reduced || target == from || leaving == null || arriving == null) {
        FrostCell(null, resting, { 0f }, modifier, content)
    } else {
        FrostCell(leaving, arriving, { scrub.progress }, modifier, content)
    }
}

/** A piece of text that is [now] at rest, frosting over what it was; with a [was], it is [was] frosted into [now] by [progress]. */
@Composable
internal fun <T : Any> FrostCell(was: T?, now: T, progress: () -> Float, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    if (was == null) MorphCell(now, modifier, content = content) else ScrubCell(was, now, progress, modifier, content = content)
}
