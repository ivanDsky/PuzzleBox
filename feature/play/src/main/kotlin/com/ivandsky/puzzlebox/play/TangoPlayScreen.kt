package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.BoardError
import com.ivandsky.puzzlebox.gameui.BoardLoading
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.games.tangoui.TangoBoardRenderer

/**
 * The Tango play screen (tango-05): the Zip play shell (ticket 13/23 semantics) with
 * 04's board renderer — tap placements land through [TangoBoardRenderer]'s placement
 * callback, the timer/hint/banner chrome is the shared [PlaySidePanel]. Design §6:
 * the win moment is the same route handoff; this screen never renders win content.
 */
@Composable
fun TangoPlayScreen(
    state: TangoPlayState,
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayClockEffects(state)

    val ready = state.screen as? TangoScreen.Ready
    val model = remember(state.screen, state.session, state.hintCell, state.rejection) {
        state.rendererModel()
    }

    // The win moment: the one-shot route handoff (ticket-23 pattern) — the frozen facts
    // go to the result route while the record write keeps running in the flow.
    val completed = state.session?.takeIf { it.isComplete }
    LaunchedEffect(completed) {
        if (completed != null) onSolved(completed.elapsedMs, completed.hintsUsed)
    }

    Box(modifier.fillMaxSize()) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = Modifier.fillMaxSize(),
            board = { TangoBoardArea(state, ready, model) },
            side = {
                PlaySidePanel(
                    title = "Tango",
                    subtitle = state.subtitle,
                    banner = ready?.degraded?.let(::degradeBannerMessage),
                    elapsedMs = state.displayedElapsedMs(),
                    hintsUsed = state.session?.hintsUsed ?: 0,
                    hintEnabled = ready != null && state.session?.isComplete != true,
                    onHint = state::hint,
                )
            },
        )
    }
}

@Composable
private fun BoxScope.TangoBoardArea(
    state: TangoPlayState,
    ready: TangoScreen.Ready?,
    model: TangoRendererModel?,
) {
    Box(
        Modifier
            .fillMaxSize()
            .testTag("play.board_area")
            // A4: standard pane margins; the cap below is a max, not a fixed width.
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        val screenState = state.screen
        when {
            ready != null && model != null -> {
                // The renderer owns the tap policy + A/B placement variants (04); the
                // play shell only wires placements into the state holder. The outline
                // rides the board modifier (§2 — the Zip outline pattern).
                val renderer = remember(state) { TangoBoardRenderer(onPlacement = state::onPlacement) }
                renderer.Content(
                    model = model,
                    modifier = Modifier
                        .widthIn(max = PlayBoardMaxWidth)
                        .heightIn(max = PlayBoardMaxWidth)
                        .zipBoardOutline(model.n)
                        .testTag("play.board"),
                )
                HintCaption(state)
            }

            screenState is TangoScreen.Unavailable -> BoardError(
                message = screenState.message,
                onRetry = state::load,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl)
                    .testTag("play.error"),
            )

            else -> BoardLoading(modifier = Modifier.testTag("play.loading"))
        }
    }
}

/** §6 hint caption: names the deduction class while the hint's placement stands. */
@Composable
private fun BoxScope.HintCaption(state: TangoPlayState) {
    val why = state.hintWhy ?: return
    Text(
        text = hintPhrase(why),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = Spacing.md)
            .testTag("play.hint_caption"),
    )
}

/** The hint's WHY as a sentence (§6 hint copy: the reveal names the rule + evidence). */
internal fun hintPhrase(why: TangoHint.Why): String = when (why) {
    is TangoHint.Why.MarkerBinding -> when (why.kind) {
        TangoMarkerKind.SAME -> "= links this cell to its partner — it must match."
        TangoMarkerKind.OPPOSITE -> "× links this cell to its partner — it must be the opposite."
    }

    is TangoHint.Why.ThreeInARow ->
        "This cell would make three of the same in a line — it must be the other value."

    is TangoHint.Why.LineFull -> {
        val other = if (why.fullOf == TangoCell.SUN) "moons" else "suns"
        val row = why.row
        when {
            row != null -> "Row ${row + 1} already holds all its " +
                valueWord(why.fullOf) + " — this cell is one of the $other."

            else -> "Column ${(why.col ?: 0) + 1} already holds all its " +
                valueWord(why.fullOf) + " — this cell is one of the $other."
        }
    }
}

private fun valueWord(value: TangoCell): String = if (value == TangoCell.SUN) "suns" else "moons"
