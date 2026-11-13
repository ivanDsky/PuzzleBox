package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.strands.StrandsReplay
import com.ivandsky.puzzlebox.games.strands.StrandsReplayMove
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.chrome.WordSlotLine

/** Scripted solves of the real 2026-11-06 day, stepped through the adapter, as the replay timeline would build its frames. */
internal object StrandsReplayScript {
    private val initial: StrandsSession = run {
        val fetched = RealDay.fetched
        val puzzle = (StrandsCodec.decode(fetched.puzzle.payload) as DecodeResult.Ok).value
        StrandsSession(ref = fetched.ref as StrandsPuzzleRef, state = StrandsState(puzzle))
    }
    private val cols = initial.state.puzzle.cols

    /** The first theme word (ACORN), the spangram, and a non-theme word with one spelling on the board (ARTS). */
    val theme = StrandsReplayMove.Submit(RealDay.themeWords[0])
    val spangram = StrandsReplayMove.Submit(RealDay.spangram)
    val nonTheme = StrandsReplayMove.Submit(RealDay.firstHintWords[0])

    fun sessionAfter(moves: List<StrandsReplayMove>): StrandsSession {
        var session = initial
        moves.forEachIndexed { i, move ->
            session = checkNotNull(StrandsReplay.step(session, move.toReplayMove(cols), i * 1_000L + 1_000L)) { "move $i" }
        }
        return session
    }

    private fun frameOf(session: StrandsSession, before: StrandsSession?, atMs: Long) = ReplayFrame(
        session = session,
        before = before,
        hinted = emptySet(),
        facts = StrandsReplay.factsOf(initial)(session),
        atMs = atMs,
        sinceMoveMs = 0L,
        ended = false,
    )

    /** The frame after [moves], with the board before the last of them behind it. */
    fun frameAfter(moves: List<StrandsReplayMove>, atMs: Long = moves.size * 1_000L): ReplayFrame =
        frameOf(sessionAfter(moves), if (moves.isEmpty()) null else sessionAfter(moves.dropLast(1)), atMs)

    /** The board [frameAfter] shows, reached with no step behind it: nothing found, set aside or settling. */
    fun bareFrameAfter(moves: List<StrandsReplayMove>, atMs: Long = moves.size * 1_000L): ReplayFrame =
        frameOf(sessionAfter(moves), null, atMs)
}

/**
 * One replay board under a test clock that is paused and stepped one frame at a time, sampled by its rendered pixels.
 * [reference] (optional) draws live's own board beside it, with [referenceTrace] selected, at the same size.
 */
internal class StrandsReplayRig(private val rule: ComposeContentTestRule) {
    private var frame by mutableStateOf(StrandsReplayScript.frameAfter(emptyList()))
    private var hasReference = false
    private var options by mutableStateOf(ReplayBoardOptions(animate = true, showErrors = true))

    /** Shows [shown]; the clock stays paused afterwards. [referenceTrace] adds the reference board. */
    fun show(shown: ReplayFrame, animate: Boolean, referenceTrace: List<Coord>? = null) {
        frame = shown
        hasReference = referenceTrace != null
        options = ReplayBoardOptions(animate = animate, showErrors = true)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Row {
                    Box(Modifier.testTag(REPLAY)) { StrandsReplayBoard.Board(frame, options, Modifier.size(BOARD_W, BOARD_H)) }
                    if (referenceTrace != null) {
                        // The same column as the replay board, so the two boards are the same size.
                        Column(Modifier.testTag(REFERENCE).size(BOARD_W, BOARD_H)) {
                            WordSlotLine(WordSlot.Empty, found = 0, total = 1, reduceMotion = true)
                            StrandsBoardContent(
                                model = StrandsRendererModel.of((frame.session as StrandsSession).state),
                                found = null,
                                reduceMotion = true,
                                trace = { Trace(referenceTrace) },
                                onTrace = {},
                                floor = 0.dp,
                                readOnly = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    /** Moves to [next] (or just to [animate]) and lets the board draw it. */
    fun update(next: ReplayFrame = frame, animate: Boolean = options.animate) {
        frame = next
        options = ReplayBoardOptions(animate = animate, showErrors = true)
        // One frame recomposes the board, the next recomposes it for what it rests on, the third draws it.
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        // A capture shows the board as it was before the change; the next one shows it.
        board()
        if (hasReference) reference()
    }

    private fun node(tag: String): SemanticsNodeInteraction = rule.onNode(hasTestTag("strands.board") and hasAnyAncestor(hasTestTag(tag)))

    // The first capture after a change shows the board as it was before it; the second is the board now.
    private fun capture(tag: String): ImageBitmap {
        rule.mainClock.advanceTimeByFrame()
        node(tag).captureToImage()
        return node(tag).captureToImage()
    }

    /** The replay board's pixels one frame on. */
    fun board(): ImageBitmap = capture(REPLAY)

    /** The reference board's pixels one frame on. */
    fun reference(): ImageBitmap = capture(REFERENCE)

    /** The replay board on each of the next [count] frames. */
    fun frames(count: Int = 16): List<ImageBitmap> = (1..count).map { board() }

    /** The replay board after [ms] more of the clock. */
    fun boardAfter(ms: Long): ImageBitmap {
        rule.mainClock.advanceTimeBy(ms)
        return board()
    }

    companion object {
        private val BOARD_W = 160.dp
        private val BOARD_H = 280.dp
        const val REPLAY = "strands.replay"
        const val REFERENCE = "strands.reference"
    }
}

internal fun ImageBitmap.sameAs(other: ImageBitmap): Boolean = toPixelMap().buffer.contentEquals(other.toPixelMap().buffer)
