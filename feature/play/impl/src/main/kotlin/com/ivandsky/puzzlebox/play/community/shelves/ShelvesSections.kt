package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.community.MakePuzzleCard
import com.ivandsky.puzzlebox.designsystem.community.ShelfCardDefaults
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllScope
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPlan.Section

/** The sub line of a game shelf, from what it holds. */
private fun Section.GameShelf.sub() = when (val summary = summary) {
    is Section.GameShelf.Summary.More -> "${summary.count} more · ${summary.inAll} in all"
    is Section.GameShelf.Summary.AllAbove -> "all ${summary.inAll} are above"
    Section.GameShelf.Summary.None -> "none yet · be the first"
}

/** The loaded plan's sections, top to bottom, each a lazy item. Make cards open the New puzzle sheet through [ShelvesActions.onMake]. */
internal fun LazyListScope.shelvesSections(content: ShelvesUiState.Content, actions: ShelvesActions) {
    content.plan.sections.forEach { section ->
        when (section) {
            is Section.Spotlight -> item(key = "spotlight", contentType = "spotlight") {
                PlanSpotlight(section.card, content, actions, Modifier.padding(horizontal = Spacing.lg))
            }
            is Section.MostPlayed -> item(key = "most", contentType = "shelf") {
                ShelfRow("Most played", "all time", onSeeAll = { actions.onSeeAll(SeeAllScope.MostPlayed) }, seeAllTag = "shelves.seeall.most") { items(section.cards, key = { it.code.value }) { PlanCard(it, content, actions) } }
            }
            is Section.Newest -> item(key = "newest", contentType = "shelf") {
                ShelfRow(
                    "Newest",
                    "latest first",
                    Modifier.testTag("shelves.shelf.newest"),
                    onSeeAll = { actions.onSeeAll(SeeAllScope.Newest) },
                    seeAllTag = "shelves.seeall.newest",
                ) { items(section.cards, key = { it.code.value }) { PlanCard(it, content, actions) } }
            }
            is Section.GameShelf -> item(key = "game:${section.game.value}", contentType = "shelf") {
                val game = content.games[section.game] ?: return@item
                ShelfRow(
                    game.name,
                    section.sub(),
                    Modifier.testTag("shelves.shelf.${game.id.value}"),
                    disc = game.brand,
                    // A shelf with nothing on it has nothing to see all of.
                    onSeeAll = if (section.summary == Section.GameShelf.Summary.None) null else ({ actions.onSeeAll(SeeAllScope.Game(game.id)) }),
                    seeAllTag = "shelves.seeall.${game.id.value}",
                ) {
                    items(section.cards, key = { it.code.value }) { PlanCard(it, content, actions) }
                    item(key = "make", contentType = "make") { MakeCard(game.id, game.name, section.cards.isEmpty(), actions) }
                }
            }
            is Section.MakeOne -> item(key = "make-one", contentType = "shelf") {
                ShelfRow("Make one", "pick a game") {
                    items(section.games, key = { it.value }) { id ->
                        val game = content.games[id]
                        if (game != null) MakeCard(id, game.name, wide = true, actions)
                    }
                }
            }
        }
    }
}

/** The "Make a [name] puzzle" card for [game]; [wide] when it is the shelf's only card or in the make row. */
@Composable
private fun MakeCard(game: GameId, name: String, wide: Boolean, actions: ShelvesActions) {
    MakePuzzleCard(
        gameName = name,
        onClick = { actions.onMake(game) },
        modifier = (if (wide) Modifier.width(ShelfCardDefaults.MakeRowCardWidth) else Modifier).testTag("shelves.make.${game.value}"),
    )
}
