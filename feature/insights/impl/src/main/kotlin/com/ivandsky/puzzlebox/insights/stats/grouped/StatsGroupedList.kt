package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.model.GameId

/**
 * The Ready page with the StatsFilters flag on: [sections] in their four groups, each under a header that sticks to the
 * top of the list as its group scrolls, i.e. right under the filter bar, which sits above the list. [game] null is the
 * all-games page.
 */
@Composable
internal fun StatsGroupedList(
    sections: List<StatsSection>,
    game: GameId?,
    gameName: (GameId) -> String,
    onOpenSolve: (SolveRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = groupStatsSections(sections, game)
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("stats.sections"),
        contentPadding = PaddingValues(bottom = Spacing.xl),
    ) {
        blocks.forEachIndexed { index, block ->
            stickyHeader(key = "header.${block.group.name}") { StatsGroupHeader(block.group, divided = index > 0) }
            item(key = "body.${block.group.name}") {
                StatsGroupFrame(block, gameName, onOpenSolve, singleGame = game != null, modifier = Modifier.padding(bottom = Spacing.xl))
            }
        }
    }
}
