package com.ivandsky.puzzlebox.create

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.maxContentWidth

/**
 * The Create root's Drafts / Published switch (01 handoff): two segments carrying their
 * counts, "Drafts {n}" and "Published {m}"; a tap reports its tab through [onTab].
 */
@Composable
internal fun CreateTabs(state: CreateUiState, onTab: (CreateUiState.Tab) -> Unit, modifier: Modifier = Modifier) {
    val tabs = CreateUiState.Tab.entries
    SingleChoiceSegmentedButtonRow(
        modifier
            .maxContentWidth()
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        tabs.forEachIndexed { index, tab ->
            val count = when (tab) {
                CreateUiState.Tab.Drafts -> state.draftCount
                CreateUiState.Tab.Published -> state.published.size
            }
            SegmentedButton(
                selected = state.tab == tab,
                onClick = { onTab(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                modifier = Modifier.testTag("create.tab.${tab.name.lowercase()}"),
            ) { Text("${tab.name} $count", maxLines = 1) }
        }
    }
}
