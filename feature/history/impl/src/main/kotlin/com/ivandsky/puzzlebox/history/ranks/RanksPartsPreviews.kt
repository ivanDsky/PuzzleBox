package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.history.HistoryReplays
import com.ivandsky.puzzlebox.history.ReplaySolve
import com.ivandsky.puzzlebox.model.GameId

// Previews of the hub's parts, the states the screen previews do not reach: each part on its own, light and dark.

@PuzzleBoxPreviews
@Composable
private fun RanksTopBarPreview() {
    PuzzleBoxTheme {
        Box(Modifier.padding(Spacing.lg)) {
            RanksTopBar(label = tuneLabel(RankScope.Friends, RanksSelection.Window.Week), onTune = {}) {
                Text("Zip", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun PageDotsMidScrubPreview() {
    PuzzleBoxTheme {
        // A finger a little over a third of the way from the second board to the third.
        val scope = rememberCoroutineScope()
        val scrub = remember { BoardScrub(position = 1, scope = scope).also { it.dragBy(delta = -0.4f, count = DOTS) } }
        Box(Modifier.padding(Spacing.lg)) { PageDots(count = DOTS, index = 1, scrub = scrub) }
    }
}

private const val DOTS = 5

@PuzzleBoxPreviews
@Composable
private fun RollingLineMidRollPreview() {
    PuzzleBoxTheme {
        val ink = MaterialTheme.colorScheme.onSurface
        Box(Modifier.padding(Spacing.lg)) {
            RollingLine(
                text = "1:35 · 2 hints",
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = remember(ink) { ColorProducer { ink } },
                was = "1:15 · 1 hint",
                progress = { 0.5f },
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun RanksBoardNotePreview() {
    PuzzleBoxTheme { Box(Modifier.padding(Spacing.lg)) { RanksBoardNote("2 of 5 boards") } }
}

@PuzzleBoxPreviews
@Composable
private fun RanksFriendsHintPreview() {
    PuzzleBoxTheme { Box(Modifier.padding(Spacing.lg)) { RanksFriendsHint() } }
}

@PuzzleBoxPreviews
@Composable
private fun RanksSignInPromptPreview() {
    PuzzleBoxTheme { Box(Modifier.padding(Spacing.lg)) { RanksSignInPrompt(RanksSignIn.Idle, onSignIn = {}) } }
}

// The menu draws in its own popup below the title, so the preview reserves the room it drops into. Its rows are the title
// menu's own ([BoardItem], the "How ranks work" row); the title keeps its open state to itself.
@PuzzleBoxPreviews
@Composable
private fun RanksTitleMenuOpenPreview() {
    PuzzleBoxTheme {
        Box(Modifier.padding(Spacing.lg).height(MENU_ROOM)) {
            Box {
                Text("Overall", style = MaterialTheme.typography.titleLarge)
                AppDropdownMenu(expanded = true, onDismissRequest = {}) { TitleMenuRows() }
            }
        }
    }
}

private val MENU_ROOM = Spacing.xxl * 8

private val MenuPlaces = listOf(
    BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(12)),
    BoardPlace(RanksBoard.Game(GameId("zip")), "Zip", Standing.Place(3)),
    BoardPlace(RanksBoard.Game(GameId("tango")), "Tango", Standing.Beyond),
    BoardPlace(RanksBoard.Game(GameId("queens")), "Queens", Standing.None),
    BoardPlace(RanksBoard.Game(GameId("strands")), "Strands", Standing.Pending),
)

@Composable
private fun ColumnScope.TitleMenuRows() {
    MenuPlaces.forEachIndexed { index, place -> BoardItem(place, selected = index == 0, index = index, reduced = false) {} }
    HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
    DropdownMenuItem(
        text = { Text("How ranks work", style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(PuzzleIcons.HowTo, contentDescription = null, modifier = Modifier.size(RanksMetrics.IconSize)) },
        onClick = {},
    )
}

@PuzzleBoxPreviews
@Composable
private fun YourSolveRowReplayPreview() {
    PuzzleBoxTheme {
        Box(Modifier.padding(Spacing.lg)) {
            YourSolveRow(
                PreviewYourSolve.copy(replays = HistoryReplays.One(PreviewTimeline, ReplaySolve.Record), playUi = PreviewPlayUi),
                onPlayAgain = { _, _ -> },
                onOpenReplay = { _, _ -> },
                onCompare = {},
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun SolveReplayButtonOnePreview() = ReplayButton(HistoryReplays.One(PreviewTimeline, ReplaySolve.Record))

@PuzzleBoxPreviews
@Composable
private fun SolveReplayButtonFirstAndLatestPreview() = ReplayButton(HistoryReplays.FirstAndLatest(PreviewTimeline, PreviewTimeline))

@PuzzleBoxPreviews
@Composable
private fun SolveReplayButtonHiddenPreview() = ReplayButton(HistoryReplays.Hidden)

// The button is all that shows; the first-and-latest menu is a popup that opens on a tap.
@Composable
private fun ReplayButton(replays: HistoryReplays) {
    PuzzleBoxTheme {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SolveReplayButton(replays, onOpen = {}, onCompare = {})
        }
    }
}

private const val MOVES = 12
private const val MOVE_MS = 1_000L
private val PreviewGame = GameId("zip")

private data class CountSession(val placed: Int) : GameSession

private object CountReplay : GameReplay<CountSession> {
    override fun step(session: CountSession, move: ReplayMove, atMs: Long) = CountSession(session.placed + 1)

    override fun factsOf(initial: CountSession): (CountSession) -> ReplayFacts =
        { ReplayFacts(hints = 0, correct = it.placed, total = MOVES, solved = it.placed == MOVES) }
}

// A replay of twelve moves, one a second, over a game that only counts them.
private val PreviewTimeline: Timeline by lazy {
    val log = MoveLog.fresh(PreviewGame)
    var session = CountSession(0)
    for (move in 1..MOVES) session = log.play(CountReplay, session, ReplayMove(0, IntArray(0)), move * MOVE_MS)
    val sealed = checkNotNull(log.seal(ReplayOutcome.Solved, endMs = MOVES * MOVE_MS))
    checkNotNull(Timeline.open(PreviewGame, CountReplay, CountSession(0), sealed))
}

// A game with a replay board, which is all the solve row reads from its play UI.
private object PreviewPlayUi : GamePlayUi {
    override val gameId: GameId = PreviewGame
    override val replayBoard: GameReplayBoard = object : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) = Unit
    }
    override val tutorial: List<TutorialStep> get() = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController = error("preview only")

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) = Unit

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}
