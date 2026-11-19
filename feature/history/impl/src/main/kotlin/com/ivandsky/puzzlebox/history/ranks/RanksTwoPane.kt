package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LargeTopBarTitle
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.bleedSides
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.resultui.board.boardRowValueText

private const val LIST_WEIGHT = 0.4f
private const val BOARD_WEIGHT = 0.6f

/**
 * The Ranks hub on a tablet: the left pane holds the full month with the lifetime stats under it and the game list
 * (Overall first, the viewer's place on each); the right pane holds the top bar (the board's name and the tune icon),
 * the placement hero card, Your solve for a past day and the board. A pick in the list re-ranks the board in place, and
 * so does a swipe anywhere under the top bar, down to the bottom of the pane ([RanksActions.onSwipe], as on the phone:
 * the same [RanksBoardStack]), so there is no title menu, week strip or dots here. The
 * calendar is always the month: the shared calendar's fold-to-week is turned back into a month at the door
 * ([RanksActions.onShowCalendar] only ever hears `expanded = true`). The hero card wears the shown board's accent; it,
 * the board's title and the board itself go to the next board's with the [scrub]. With [RanksUiState.filterSheets] the Who and When
 * chips sit under the top bar, and [onFilterOpenChange] hears their sheets open and close; a null [onTune] leaves the tune icon out.
 * With [largeBar] the board's name, the scope and window and the tune icon are Today's large top bar over the left pane
 * ([RanksLargeBar]), and the right pane starts with the hero card.
 */
@Composable
internal fun RanksTwoPane(
    state: RanksUiState,
    actions: RanksActions,
    scrub: BoardScrub,
    leftScroll: ScrollState,
    rightScroll: ScrollState,
    onRowClick: ((DailyBoardRowModel) -> Unit)?,
    refreshing: Boolean,
    onTune: (() -> Unit)?,
    onFilterOpenChange: (Boolean) -> Unit,
    onHowRanksWork: () -> Unit,
    modifier: Modifier = Modifier,
    largeBar: Boolean = false,
) {
    val current = state.places.getOrNull(state.position)
    val accents = state.places.map { it.brand.accent.colors() }
    val containers = accents.map { it.container }
    val inks = accents.map { it.onContainer }
    val plain = MaterialTheme.colorScheme
    val target = scrub.target(state.position)
    val between = target != scrub.from && !rememberReducedMotion()
    val heroFace = { board: Int ->
        val place = state.places.getOrNull(board)
        HeroFace(place?.name ?: "Ranks", place?.standing ?: Standing.None, state.sliceAt(board)?.selfScore())
    }
    Row(
        // No bottom padding on the row: each pane keeps its own, so the left list scrolls to the pane's edge.
        modifier.fillMaxSize().testTag("route.ranks").padding(start = Spacing.lg, top = if (largeBar) 0.dp else Spacing.lg, end = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Column(Modifier.weight(LIST_WEIGHT).fillMaxHeight().verticalScroll(leftScroll).padding(bottom = Spacing.lg).testTag("ranks.leftpane")) {
            if (largeBar) {
                // The bar's side padding sits in the gutters, so its title lines up with Today's and its tune with the list's end.
                // The board's name, as the phone's title menu shows it; the sub line already says the scope and window.
                RanksLargeBar(state, onTune, Modifier.bleedSides(Spacing.lg)) {
                    BoardScrubCell(scrub, state.position, current?.name ?: "Ranks", at = { state.places.getOrNull(it)?.name }) { title ->
                        LargeTopBarTitle(title, Modifier.testTag("ranks.boardtitle"))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RanksCalendar(state.calendar.copy(expanded = true), state.stats, { day, _ -> actions.onShowCalendar(day, true) }, actions.onSelectDay, foldable = false, gapBelow = 0.dp)
                GameList(state.places, current?.board, actions.onPickBoard, onHowRanksWork = onHowRanksWork)
            }
        }
        val paneTop = if (largeBar) Spacing.lg else 0.dp
        RanksPullToRefresh(refreshing, actions.onRefresh, Modifier.weight(BOARD_WEIGHT).fillMaxHeight().padding(top = paneTop, bottom = Spacing.lg)) {
            FillRemainingColumn(Modifier.fillMaxSize().verticalScroll(rightScroll).testTag("ranks.rightpane")) {
                if (!largeBar) {
                    RanksTopBar(state.topBarLabel(), onTune = onTune) {
                        BoardScrubCell(scrub, state.position, state.board.title, Modifier.padding(start = Spacing.sm), at = { state.sliceAt(it)?.title }) { title ->
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("ranks.boardtitle"),
                            )
                        }
                    }
                }
                if (state.filterSheets) RanksFilterChips(state, actions, onFilterOpenChange)
                RanksBoardStack(state, scrub, actions, onRowClick) { jump ->
                    // The pane's one gap between its parts: Your solve brings its own under it, and is nothing when there is none.
                    RanksHeroCard(
                        face = heroFace(if (between) target else state.position),
                        container = { scrub.blend(containers, plain.surfaceContainerHigh) },
                        ink = { scrub.blend(inks, plain.onSurface) },
                        modifier = Modifier.padding(vertical = Spacing.md),
                        was = heroFace(scrub.from).takeIf { between },
                        progress = { scrub.progress },
                        onClick = jump,
                    )
                }
            }
        }
    }
}

/** The viewer's own official row of the shown board as its score column says it; null when they have none there. */
private fun RanksBoardSlice.selfScore(): String? =
    (state as? DailyBoardState.Ranked)?.rows?.firstOrNull { it.isSelf && !it.unofficial }
        ?.let { boardRowValueText(it.scoreValue, order) }

/** Every board with the viewer's place on it, the shown one marked; a pick reports [onPick]. */
@Composable
private fun GameList(places: List<BoardPlace>, selected: RanksBoard?, onPick: (RanksBoard) -> Unit, onHowRanksWork: () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth().testTag("ranks.list"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            places.forEachIndexed { index, place ->
                GameItem(place, place.board == selected, Modifier.testTag("ranks.list.item.$index")) { onPick(place.board) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = RanksMetrics.ListItemHeight)
                    .selectable(selected = false, role = Role.Button, onClick = onHowRanksWork)
                    .padding(horizontal = Spacing.md)
                    .testTag("ranks.list.how"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(PuzzleIcons.HowTo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(RanksMetrics.IconSize))
                Text("How ranks work", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GameItem(place: BoardPlace, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = RanksMetrics.ListItemHeight),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                place.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else null,
                modifier = Modifier.weight(1f),
            )
            Text(place.standing.label(), style = MaterialTheme.typography.labelLarge, color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
        }
    }
}
