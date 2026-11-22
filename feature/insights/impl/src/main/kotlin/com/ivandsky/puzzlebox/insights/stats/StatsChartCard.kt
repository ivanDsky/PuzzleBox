package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The card a stats section's chart or number sits on (no card on the grouped page, see [LocalStatsFlat]). */
@Composable
internal fun StatsChartCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .statsCard()
            .padding(statsCardInset()),
        content = content,
    )
}
