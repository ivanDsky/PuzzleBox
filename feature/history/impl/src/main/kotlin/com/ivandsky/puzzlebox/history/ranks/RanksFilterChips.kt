package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.FilterChipRow
import com.ivandsky.puzzlebox.designsystem.FilterOption
import com.ivandsky.puzzlebox.designsystem.SingleFilterChip
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window

// The rows carry the tags today's tune segments do; the first row of each (Global, Day) is the chip's "all" row.
private val ScopeOptions = listOf(FilterOption(RankScope.Friends, "Friends", tag = "ranks.tune.scope.Friends"))
private val WindowOptions = listOf(Window.Week, Window.Month).map { FilterOption(it, it.name, tag = "ranks.tune.window.${it.name}") }

/** Who and When as single-pick chips. [onOpenChange] hears each sheet open and close so the screen can hold other sheets back. */
@Composable
internal fun RanksFilterChips(state: RanksUiState, actions: RanksActions, onOpenChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FilterChipRow(modifier.padding(top = Spacing.sm)) {
        SingleFilterChip(
            name = "Who",
            allLabel = "Global",
            options = ScopeOptions,
            selected = state.scope.takeIf { it != RankScope.Global },
            onSelect = { actions.onScope(it ?: RankScope.Global) },
            onOpenChange = onOpenChange,
            tag = "ranks.filter.who",
            allTag = "ranks.tune.scope.Global",
        )
        SingleFilterChip(
            name = "When",
            allLabel = "Day",
            options = WindowOptions,
            selected = state.window.takeIf { it != Window.Day },
            onSelect = { actions.onWindow(it ?: Window.Day) },
            onOpenChange = onOpenChange,
            tag = "ranks.filter.when",
            allTag = "ranks.tune.window.Day",
        )
    }
}
