package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * The tablet's right column: every fact of the story as a list, the one on the story's page outlined. A tap on a fact
 * calls [onSelect] with its position so the story jumps to its page.
 */
@Composable
internal fun RecapFactList(
    cards: List<RecapCard>,
    current: Int,
    gameName: (GameId) -> String,
    onSelect: (Int) -> Unit,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("recap.facts"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        cards.forEachIndexed { index, card ->
            val selected = index == current
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier.fillMaxWidth().semantics { this.selected = selected }.testTag("recap.fact.$index"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    width = if (selected) SelectedBorder else NoBorder,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                RecapFact(card, gameName, onWatchReplay, Modifier.padding(Spacing.md))
            }
        }
    }
}

/** One card as a list fact: the one place a [RecapCard] becomes a list entry, exhaustive like [RecapCardPage]. */
@Composable
private fun RecapFact(
    card: RecapCard,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (card) {
        is RecapCard.Solved -> SolvedCardFact(card, gameName, modifier)
        is RecapCard.Fastest -> FastestCardFact(card, gameName, onWatchReplay, modifier)
        is RecapCard.Streak -> StreakCardFact(card, modifier)
        is RecapCard.HintFree -> HintFreeCardFact(card, modifier)
        is RecapCard.Moments -> MomentsCardFact(card, modifier)
        is RecapCard.TotalTime -> TotalTimeCardFact(card, modifier)
        is RecapCard.Friends -> FriendsCardFact(card, gameName, modifier)
    }
}

private val SelectedBorder = Spacing.xs / 2
private val NoBorder = Spacing.xs / 4
