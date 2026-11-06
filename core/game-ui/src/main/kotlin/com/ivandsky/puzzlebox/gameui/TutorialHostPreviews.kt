package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

// Previews of TutorialHost — a stand-in game whose board is a plain tile: a lesson, the
// solved beat and the closing "You're ready", each in light and dark.

private val PreviewBoardSide = 200.dp
private val PreviewScreenWidth = 360.dp
private val PreviewScreenHeight = 640.dp

private object PreviewRef : PuzzleRef {
    override val gameId = GameId("preview")
    override val key: PuzzleKey = PuzzleKey.Daily(LocalDate.of(2026, 11, 4), null)
}

private object PreviewController : GamePlayController {
    override val isComplete = false
    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt = Adopt.Adopted
    override fun adoptRestored(bytes: ByteArray): Restored? = null
    override fun replay() = Unit
}

private object PreviewGame : GamePlayUi {
    override val gameId = PreviewRef.gameId
    override val tutorial = listOf("Tap a square to fill it.", "Fill every square.", "Now try it on your own.").map {
        TutorialStep(it, Puzzle(gameId, VariantId("preview/v1"), 1, byteArrayOf()), PreviewRef)
    }

    override fun createController(env: PlayEnvironment): GamePlayController = PreviewController

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.size(PreviewBoardSide),
        ) {}
    }

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}

/** The preview game's tutorial with the first [passed] lessons behind it; [beat] has the current one just solved. */
private fun previewSession(passed: Int, beat: Boolean = false) = TutorialSession(PreviewGame.tutorial).apply {
    repeat(passed) {
        advance()
        next()
    }
    if (beat) advance()
}

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(width = PreviewScreenWidth, height = PreviewScreenHeight),
            content = content,
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun TutorialHostLessonPreview() {
    PreviewFrame {
        TutorialHost(ui = PreviewGame, session = remember { previewSession(passed = 1) }, onFinished = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun TutorialHostSolvedBeatPreview() {
    PreviewFrame {
        TutorialHost(ui = PreviewGame, session = remember { previewSession(passed = 1, beat = true) }, onFinished = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun TutorialHostReadyPreview() {
    PreviewFrame {
        TutorialHost(ui = PreviewGame, session = remember { previewSession(passed = 3) }, onFinished = {})
    }
}
