package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import java.time.LocalDate

/** A done or upcoming step dot; the current one is wider, so it never relies on colour alone. */
private val DotSize = 8.dp
private val CurrentDotWidth = 24.dp

/**
 * Plays a game's tutorial (spec A11): it draws [session] and nothing else decides here.
 * Each lesson shows its step dots, an always-visible Skip, the instruction line, the
 * game's own [GamePlayUi.Board] on a sandboxed controller ([TutorialSandbox]) and the
 * shared caption slot under the same precedence as the play chrome. Solving a lesson
 * moves on; after the last one a calm "You're ready" hands back through [onFinished].
 * Skip ends the session and calls [onFinished] at once. No clock runs here.
 */
@Composable
fun TutorialHost(
    ui: GamePlayUi,
    session: TutorialSession,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by session.state.collectAsState()
    Box(modifier.fillMaxSize().testTag("tutorial.host"), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = PlayBoardMaxWidth)
                .fillMaxSize()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val current = state) {
                is TutorialState.Playing -> LessonPane(
                    ui = ui,
                    session = session,
                    lesson = current,
                    onSkip = {
                        session.skip()
                        onFinished()
                    },
                )
                TutorialState.Ready -> ReadyPane(onFinished)
                TutorialState.Skipped -> Unit
            }
        }
    }
}

@Composable
private fun ColumnScope.LessonPane(
    ui: GamePlayUi,
    session: TutorialSession,
    lesson: TutorialState.Playing,
    onSkip: () -> Unit,
) {
    val controller = rememberLessonController(ui, session, lesson)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepDots(index = lesson.index, count = lesson.count, modifier = Modifier.weight(1f))
        AppTextButton(text = "Skip", onClick = onSkip, modifier = Modifier.testTag("tutorial.skip"))
    }
    Text(
        text = lesson.step.instruction,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tutorial.instruction")
            .semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
    )
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .testTag("tutorial.board_area"),
        contentAlignment = Alignment.Center,
    ) {
        ui.Board(controller, Modifier)
    }
    PlayCaptionSlot(caption = playCaption(controller as? PlayFeedback, controller as? PlayHints))
}

/** One sandboxed controller per lesson; its scope ends when the lesson leaves the screen. */
@Composable
private fun rememberLessonController(
    ui: GamePlayUi,
    session: TutorialSession,
    lesson: TutorialState.Playing,
): GamePlayController {
    val parent = rememberCoroutineScope()
    val scope = remember(ui, session, lesson) {
        CoroutineScope(parent.coroutineContext + Job(parent.coroutineContext.job))
    }
    DisposableEffect(scope) { onDispose { scope.cancel() } }
    return remember(scope) { TutorialSandbox(ui, session).controllerFor(lesson, scope) }
}

@Composable
private fun StepDots(index: Int, count: Int, modifier: Modifier = Modifier) {
    val reached = LocalPrimaryText.current
    val upcoming = MaterialTheme.colorScheme.outline
    Row(
        modifier
            .testTag("tutorial.dots")
            .clearAndSetSemantics { contentDescription = "Step ${index + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { dot ->
            Box(
                Modifier
                    .size(width = if (dot == index) CurrentDotWidth else DotSize, height = DotSize)
                    .background(if (dot <= index) reached else upcoming, CircleShape),
            )
        }
    }
}

@Composable
private fun ColumnScope.ReadyPane(onFinished: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Text(
        text = "You're ready!",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .testTag("tutorial.ready")
            .semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
    )
    Text(
        text = "You know how to play. Have fun!",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    PrimaryButton(
        text = "Let's play",
        onClick = onFinished,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tutorial.finish"),
    )
    Spacer(Modifier.weight(1f))
}

// Previews — a stand-in game whose board is a plain tile: one lesson in light and dark,
// and the closing "You're ready".

private val PreviewBoardSide = 200.dp

private object PreviewRef : PuzzleRef {
    override val gameId = GameId("preview")
    override val date: LocalDate = LocalDate.of(2026, 11, 4)
    override val puzzleNumber: Int? = null
}

private object PreviewController : GamePlayController {
    override val isComplete = false
    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt = Adopt.Adopted
    override fun adoptRestored(bytes: ByteArray): Restored? = null
    override fun replay() = Unit
}

private object PreviewGame : GamePlayUi {
    override val gameId = PreviewRef.gameId
    override val tutorial = listOf("Tap a cell to fill it.", "Fill every cell.", "Now try it on your own.").map {
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

/** The preview game's tutorial with the first [solved] lessons already won. */
private fun previewSession(solved: Int) = TutorialSession(PreviewGame.tutorial).apply { repeat(solved) { advance() } }

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun TutorialHostLessonPreview() {
    PuzzleBoxTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            TutorialHost(ui = PreviewGame, session = remember { previewSession(solved = 1) }, onFinished = {})
        }
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun TutorialHostLessonDarkPreview() {
    PuzzleBoxTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            TutorialHost(ui = PreviewGame, session = remember { previewSession(solved = 1) }, onFinished = {})
        }
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun TutorialHostReadyPreview() {
    PuzzleBoxTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            TutorialHost(ui = PreviewGame, session = remember { previewSession(solved = 3) }, onFinished = {})
        }
    }
}
