package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.first

/**
 * The Ready page: [sections] in their four groups, each under a header that sticks to the
 * top of the list as its group scrolls, i.e. right under the filter bar, which sits above the list. [game] null is the
 * all-games page. The blocks arrive with the page's calm motion ([StatsMotion]): each group body lifts in once as it
 * reaches the screen, its charts draw in and its headline numbers count up, and a Since or game change morphs them
 * in place.
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
    val motion = remember { StatsMotion() }
    val state = rememberLazyListState()
    LaunchedEffect(state, motion) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        motion.firstPassDone = true
    }
    CompositionLocalProvider(LocalStatsMotion provides motion) {
        LazyColumn(
            state = state,
            modifier = modifier.fillMaxSize().testTag("stats.sections"),
            contentPadding = PaddingValues(bottom = Spacing.xl),
        ) {
            blocks.forEachIndexed { index, block ->
                stickyHeader(key = "header.${block.group.name}") { StatsGroupHeader(block.group, divided = index > 0) }
                item(key = "body.${block.group.name}") {
                    StatsGroupFrame(
                        block,
                        gameName,
                        onOpenSolve,
                        singleGame = game != null,
                        modifier = Modifier
                            .statsArrival(index, key = "body.${block.group.name}", visible = {
                                state.layoutInfo.visibleItemsInfo.any { it.key == "body.${block.group.name}" }
                            })
                            .padding(bottom = Spacing.xl),
                    )
                }
            }
        }
    }
}
