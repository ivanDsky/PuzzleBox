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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job

/** A done or upcoming step dot; the current one is wider, so it never relies on colour alone. */
private val DotSize = 8.dp
private val CurrentDotWidth = 24.dp

/** The calm success line the caption slot shows during the beat. */
private const val SUCCESS_LINE = "✓ Nice!"

/**
 * Plays a game's tutorial (spec A11): it draws [session] and nothing else decides here.
 * Each lesson shows its step dots, an always-visible Skip, the instruction line, the
 * game's own [GamePlayUi.Board] on a sandboxed controller ([TutorialSandbox]) and the
 * shared caption slot docked under the board, under the same precedence as the play
 * chrome. Solving a lesson holds its board with a calm "Nice!" for a short beat, then
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
    // The success beat: the solved board stays with "Nice!" for a pause, then the next
    // lesson. It is a pause, not an animation, so it holds under reduced motion too. A
    // rotation restarts the hold; next() only moves a solved lesson, so none is skipped.
    LaunchedEffect(session, lesson.index, lesson.solved) {
        if (lesson.solved) {
            delay(MotionTokens.SolveHoldMillis)
            session.next()
        }
    }
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
    // Board and caption are one group, centred in the space under the instruction. The
    // board gets the same room as before (the area less the caption and its gap), and the
    // slot always reserves its lines, so the board never moves when a caption appears.
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .testTag("tutorial.board_area"),
            contentAlignment = Alignment.Center,
        ) {
            ui.Board(controller, Modifier)
        }
        val caption = if (lesson.solved) SUCCESS_LINE else playCaption(controller as? PlayFeedback, controller as? PlayHints)
        Box(if (lesson.solved) Modifier.testTag("tutorial.success") else Modifier) {
            PlayCaptionSlot(caption = caption, textAlign = TextAlign.Center)
        }
    }
}

/**
 * One sandboxed controller per lesson, keyed on the lesson's index so the solved beat
 * keeps the same board; its scope ends when the lesson leaves the screen.
 */
@Composable
private fun rememberLessonController(
    ui: GamePlayUi,
    session: TutorialSession,
    lesson: TutorialState.Playing,
): GamePlayController {
    val parent = rememberCoroutineScope()
    val scope = remember(ui, session, lesson.index) {
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
