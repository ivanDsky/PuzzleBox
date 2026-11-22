package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.SingleFilterSheet
import com.ivandsky.puzzlebox.designsystem.community.TextDropdownFilter
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.GameChoice
import com.ivandsky.puzzlebox.resultui.gameFilterOption

/** "Any game ▾", or the picked game's name over an underline; a tap opens the game sheet. */
@Composable
internal fun SeeAllGameFilter(game: GameId?, games: List<GameChoice>, onSelect: (GameId?) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextDropdownFilter(
        label = "Any game",
        value = games.firstOrNull { it.id == game }?.name,
        open = open,
        onClick = { open = true },
        modifier = modifier.testTag("seeall.filter.game"),
    )
    if (open) {
        val options = remember(games) { games.map { gameFilterOption(it.id, it.name, it.brand, tag = "seeall.filter.game.${it.id.value}") } }
        SingleFilterSheet(
            title = "Game",
            allLabel = "Any game",
            options = options,
            selected = game,
            onSelect = onSelect,
            onDismiss = { open = false },
            tag = "seeall.filter.game.sheet",
            allTag = "seeall.filter.game.any",
        )
    }
}
