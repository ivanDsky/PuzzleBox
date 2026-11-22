package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.LocalSelectedRowCode
import com.ivandsky.puzzlebox.designsystem.community.ShelfBadge
import com.ivandsky.puzzlebox.designsystem.community.ShelfCard
import com.ivandsky.puzzlebox.designsystem.community.SpotlightCard
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPlan.Card

/** [card]'s badges, NEW first. */
private fun Card.badges() = listOfNotNull(
    ShelfBadge.New.takeIf { isNew },
    when (origin) {
        Card.Origin.Friend -> ShelfBadge.Friend
        Card.Origin.You -> ShelfBadge.You
        Card.Origin.Community -> null
    },
)

/**
 * The plan's [card] drawn as a shelf card with its title, author and level read from [content]. A card whose puzzle or
 * game is not in [content] draws nothing. With no solve counter yet it draws no solves chip; the open puzzle of the
 * tablet's detail pane ([LocalSelectedRowCode]) is ringed.
 */
@Composable
internal fun PlanCard(card: Card, content: ShelvesUiState.Content, actions: ShelvesActions, modifier: Modifier = Modifier) {
    val puzzle = content.puzzles[card.code] ?: return
    val brand = (content.games[card.game] ?: return).brand
    PlanCardMenu(card, actions) { onLongClick ->
        ShelfCard(
            title = puzzle.title,
            author = puzzle.author,
            solves = card.solves,
            accent = brand.accent,
            glyph = brand.glyph,
            onClick = { actions.onOpen(card.code) },
            modifier = modifier.testTag("shelves.card.${card.code.value}"),
            difficulty = puzzle.difficulty?.name,
            badges = card.badges(),
            selected = LocalSelectedRowCode.current == card.code.value,
            onLongClick = onLongClick,
            onLongClickLabel = CARD_ACTIONS_LABEL,
        )
    }
}

/** The plan's most played [card] as the hero card, in the same colours and with the same open ring as [PlanCard]. */
@Composable
internal fun PlanSpotlight(card: Card, content: ShelvesUiState.Content, actions: ShelvesActions, modifier: Modifier = Modifier) {
    val puzzle = content.puzzles[card.code] ?: return
    val game = content.games[card.game] ?: return
    PlanCardMenu(card, actions) { onLongClick ->
        SpotlightCard(
            title = puzzle.title,
            gameName = game.name,
            author = puzzle.author,
            solves = card.solves ?: 0,
            accent = game.brand.accent,
            glyph = game.brand.glyph,
            onClick = { actions.onOpen(card.code) },
            modifier = modifier.testTag("shelves.card.${card.code.value}"),
            difficulty = puzzle.difficulty?.name,
            selected = LocalSelectedRowCode.current == card.code.value,
            onLongClick = onLongClick,
            onLongClickLabel = CARD_ACTIONS_LABEL,
        )
    }
}
