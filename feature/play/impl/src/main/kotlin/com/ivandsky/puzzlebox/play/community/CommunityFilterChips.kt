package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId

private val TouchTarget = 48.dp

/**
 * The game row: All, then a chip per game in [games], [game] the one picked (null is All). Each chip is the same
 * [Chip] as the difficulty row's, so every chip in both rows has one structure and a 48dp touch target. The row
 * scrolls sideways when the chips outgrow the width.
 */
@Composable
internal fun GameChips(games: List<GameChoice>, game: GameId?, onGame: (GameId?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.selectableGroup().testTag("community.games"),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "all") { Chip("All", selected = game == null, brand = null, onClick = { onGame(null) }, tag = "community.game.all") }
        items(games, key = { it.id.value }) { choice ->
            Chip(choice.name, selected = game == choice.id, brand = choice.brand, onClick = { onGame(choice.id) }, tag = "community.game.${choice.id.value}")
        }
    }
}

/** The difficulty row: Easy, Medium, Hard (which also asks for Expert), [difficulty] the one picked; a picked chip tapped again is dropped. */
@Composable
internal fun DifficultyChips(difficulty: DifficultyChoice?, onDifficulty: (DifficultyChoice) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.selectableGroup().testTag("community.difficulty"),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(DifficultyChoice.entries, key = { it.name }) { choice ->
            Chip(choice.label, selected = difficulty == choice, brand = null, onClick = { onDifficulty(choice) }, tag = "community.difficulty.${choice.name.lowercase()}")
        }
    }
}

/** One flat chip, at least 48dp tall: [label], led by the game's glyph when it has a [brand]. */
@Composable
private fun Chip(label: String, selected: Boolean, brand: GameBrand?, onClick: () -> Unit, tag: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = brand?.let { { GameGlyph(it, GameGlyphSize.Inline) } },
        modifier = Modifier.heightIn(min = TouchTarget).testTag(tag),
    )
}
