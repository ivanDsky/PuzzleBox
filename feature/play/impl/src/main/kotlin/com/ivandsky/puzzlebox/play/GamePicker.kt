package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId

/** Phone margins; two-pane keeps the top tight and widens the sides (hub-card §Layout). */
private val PhonePadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xl)
private val TwoPanePadding = PaddingValues(start = Spacing.xxl, top = Spacing.lg, end = Spacing.xxl, bottom = Spacing.xl)

/**
 * The games hub (tag `route.hub`, spec A13/A14): the "n of N done" header (`hub.summary`),
 * then [HubLayout] drawing [HubUiState.plan] — the Up-next card or the no-next panel, Also
 * today and Done today — in one column, or side by side in [PlayLayout.TwoPane]. Below
 * them, "Older puzzles" (`hub.older`, [onOpenHistory]). Picking a card is [onPick].
 */
@Composable
internal fun GamePicker(
    state: HubUiState,
    layout: PlayLayout,
    onPick: (GameId) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val twoPane = layout == PlayLayout.TwoPane
    Column(
        modifier
            .fillMaxWidth()
            .testTag("route.hub")
            .verticalScroll(rememberScrollState())
            .padding(if (twoPane) TwoPanePadding else PhonePadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = hubSummary(state.doneCount, state.rows.size),
            style = MaterialTheme.typography.headlineMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("hub.summary"),
        )
        HubLayout(plan = state.plan, rows = state.rows, twoPane = twoPane, onPick = onPick)
        Box(Modifier.fillMaxWidth().padding(top = Spacing.sm), contentAlignment = Alignment.Center) {
            AppTextButton(
                text = "Older puzzles",
                onClick = onOpenHistory,
                modifier = Modifier.testTag("hub.older"),
            )
        }
    }
}

/** The hub's header: how many of today's puzzles are solved, out of every game. */
internal fun hubSummary(done: Int, total: Int): String = "$done of $total done"
