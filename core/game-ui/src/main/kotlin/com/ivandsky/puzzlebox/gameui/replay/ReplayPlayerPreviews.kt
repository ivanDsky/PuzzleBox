package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.model.GameId

// Previews of the replay player — internal, light + dark inside PuzzleBoxTheme, over a counting fake game.

private const val PREVIEW_MOVES = 12
private val PreviewGame = GameId("preview")

private data class CountSession(val placed: Int) : GameSession

private object CountReplay : GameReplay<CountSession> {
    override fun step(session: CountSession, move: ReplayMove, atMs: Long) = CountSession(session.placed + 1)

    override fun factsOf(initial: CountSession): (CountSession) -> ReplayFacts =
        { ReplayFacts(hints = 0, correct = it.placed, total = PREVIEW_MOVES, solved = it.placed == PREVIEW_MOVES) }
}

private val PreviewBoard = object : GameReplayBoard {
    override val pieces = "pieces"

    @Composable
    override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
        Text("${frame.facts.correct} of ${frame.facts.total} $pieces", modifier.padding(16.dp))
    }
}

private fun previewTimeline(): Timeline {
    val log = MoveLog.fresh(PreviewGame)
    var session = CountSession(0)
    for (index in 1..PREVIEW_MOVES) session = log.play(CountReplay, session, ReplayMove(0, IntArray(0)), index * 1_000L)
    val sealed = checkNotNull(log.seal(ReplayOutcome.Solved, endMs = PREVIEW_MOVES * 1_000L))
    return checkNotNull(Timeline.open(PreviewGame, CountReplay, CountSession(0), sealed))
}

@PuzzleBoxPreviews
@Composable
internal fun ReplayPlayerPreview() {
    PuzzleBoxTheme { ReplayPlayer(previewTimeline(), PreviewBoard) }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplayHintBadgePreview() {
    PuzzleBoxTheme { ReplayHintBadge() }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplayTransportPreview() {
    PuzzleBoxTheme {
        val playback = rememberReplayPlayback(previewTimeline(), theirs = null)
        val state by playback.state.collectAsState()
        ReplayTransport(state, playback)
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplaySeekBarPreview() {
    PuzzleBoxTheme {
        ReplaySeekBar(
            positionMs = 5_000,
            durationMs = 12_000,
            ticksMs = longArrayOf(2_000, 5_000, 9_000),
            tickKinds = arrayOf(MoveKind.Normal, MoveKind.Hint, MoveKind.Mistake),
            onSeek = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplayPagePreview() {
    PuzzleBoxTheme { ReplayPage(previewTimeline(), PreviewBoard, context = "Preview · #1 · Today", onBack = {}, showErrors = true, onShowErrorsChange = {}) }
}

@PuzzleBoxPreviews
@Composable
internal fun WatchReplayRowPreview() {
    PuzzleBoxTheme { WatchReplayRow(moves = PREVIEW_MOVES, durationMs = PREVIEW_MOVES * 1_000L, onClick = {}) }
}

@PuzzleBoxPreviews
@Composable
internal fun LabelledWatchReplayRowPreview() {
    PuzzleBoxTheme {
        WatchReplayRow(moves = PREVIEW_MOVES, durationMs = PREVIEW_MOVES * 1_000L, onClick = {}, label = "First solve")
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplayLeadLinePreview() {
    PuzzleBoxTheme {
        val mine = previewTimeline()
        val theirs = previewTimeline()
        val playback = rememberReplayPlayback(mine, theirs)
        val state by playback.state.collectAsState()
        ReplayLeadLine(state, mine, theirs, CompareNames(mine = "You", theirs = "Maya"), PreviewBoard.pieces)
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplayComparePreview() {
    PuzzleBoxTheme {
        ReplayCompare(
            mine = previewTimeline(),
            theirs = previewTimeline(),
            board = PreviewBoard,
            names = CompareNames(mine = "You", theirs = "Maya"),
            layout = CompareLayout.Twin,
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplayCompareMineOnlyPreview() {
    PuzzleBoxTheme {
        ReplayCompare(
            mine = previewTimeline(),
            theirs = null,
            board = PreviewBoard,
            names = CompareNames(mine = "You", theirs = "Maya"),
            layout = CompareLayout.Twin,
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ReplayComparePagePreview() {
    PuzzleBoxTheme {
        ReplayComparePage(
            mine = previewTimeline(),
            theirs = previewTimeline(),
            board = PreviewBoard,
            names = CompareNames(mine = "You", theirs = "Maya"),
            layout = CompareLayout.FlipPeek,
            context = "Preview · Duel",
            onBack = {},
            showErrors = true,
            onShowErrorsChange = {},
        )
    }
}
