package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.FilterOption
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SingleFilterSheet
import com.ivandsky.puzzlebox.designsystem.community.TextDropdownFilter
import com.ivandsky.puzzlebox.insights.recap.LocalGameBrands
import com.ivandsky.puzzlebox.model.GameId

/**
 * The stats filter bar's "Any game" dropdown: the picked game's name over a secondary underline, or "Any game" while
 * every game counts. A tap opens the Game sheet: a starred "Any game" row, then a glyph row per game in [games]. A pick
 * is reported through [onSelect], null for "Any game".
 */
@Composable
internal fun StatsGameFilter(
    game: GameId?,
    games: List<GameId>,
    gameName: (GameId) -> String,
    onSelect: (GameId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextDropdownFilter(
        label = ANY_GAME,
        value = game?.let(gameName),
        open = open,
        onClick = { open = true },
        modifier = modifier.testTag(GAME_TAG),
    )
    if (open) {
        val brands = LocalGameBrands.current
        val options = buildList {
            add(FilterOption<GameId?>(null, ANY_GAME, icon = { tint -> Icon(PuzzleIcons.Star, null, Modifier.size(GLYPH_SIZE), tint) }, tag = "$GAME_TAG.any"))
            games.forEach { id ->
                add(FilterOption<GameId?>(id, gameName(id), icon = { tint -> Icon(brands(id).glyph, null, Modifier.size(GLYPH_SIZE), tint) }, tag = "$GAME_TAG.${id.value}"))
            }
        }
        SingleFilterSheet(
            title = "Game",
            allLabel = null,
            options = options,
            selected = game,
            onSelect = onSelect,
            onDismiss = { open = false },
            tag = GAME_TAG,
        )
    }
}

internal const val ANY_GAME = "Any game"
internal const val GAME_TAG = "stats.filter.game"
private val GLYPH_SIZE = 24.dp
