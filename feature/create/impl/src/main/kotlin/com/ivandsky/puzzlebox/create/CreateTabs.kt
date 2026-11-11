package com.ivandsky.puzzlebox.create

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.SegmentedToggle
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.maxContentWidth

/**
 * The Create root's Drafts / Published switch (01 handoff): two segments carrying their
 * counts, "Drafts {n}" and "Published {m}"; a tap reports its tab through [onTab].
 */
@Composable
internal fun CreateTabs(state: CreateUiState, onTab: (CreateUiState.Tab) -> Unit, modifier: Modifier = Modifier) {
    SegmentedToggle(
        options = CreateUiState.Tab.entries,
        selected = state.tab,
        onSelect = onTab,
        label = { tab ->
            val count = when (tab) {
                CreateUiState.Tab.Drafts -> state.draftCount
                CreateUiState.Tab.Published -> state.published.size
            }
            "${tab.name} $count"
        },
        tag = { "create.tab.${it.name.lowercase()}" },
        modifier = modifier
            .maxContentWidth()
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    )
}
