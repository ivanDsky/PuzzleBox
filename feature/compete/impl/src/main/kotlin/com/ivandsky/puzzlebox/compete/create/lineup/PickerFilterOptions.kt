package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.designsystem.Calendar
import com.ivandsky.puzzlebox.designsystem.FilterOption
import com.ivandsky.puzzlebox.designsystem.Globe
import com.ivandsky.puzzlebox.designsystem.Hidden
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Sparkle
import com.ivandsky.puzzlebox.domain.lineup.Freshness
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.gameFilterOption
import org.koin.compose.koinInject

private val ICON_SIZE = 24.dp

private fun <T> option(value: T, label: String, tag: String, icon: @Composable () -> ImageVector): FilterOption<T> =
    FilterOption(value, label, icon = { tint -> Icon(icon(), contentDescription = null, modifier = Modifier.size(ICON_SIZE), tint = tint) }, tag = "duel.lineup.chip.$tag")

/** The Freshness rows after "Any freshness". The chip and its page both read this one list. */
internal fun freshnessOptions(): List<FilterOption<Freshness>> = listOf(
    option(Freshness.New, "New", "new") { PuzzleIcons.Sparkle },
    option(Freshness.NotPlayed, "Not played", "notplayed") { PuzzleIcons.Hidden },
)

/** The Source rows after "All sources". Past you has only Past days and From friends: every puzzle in it is one the player solved. */
internal fun sourceOptions(pastYou: Boolean): List<FilterOption<PuzzleSource>> {
    val pastDays = option(PuzzleSource.PastDay, "Past days", "pastdays") { PuzzleIcons.Calendar }
    val friends = option(PuzzleSource.Friend, "From friends", "friends") { PuzzleIcons.Community }
    return if (pastYou) {
        listOf(pastDays, friends)
    } else {
        listOf(option(PuzzleSource.Community, "Community", "community") { PuzzleIcons.Globe }, friends, pastDays, option(PuzzleSource.Mine, "Mine", "mine") { PuzzleIcons.Profile })
    }
}

/** One Games row per game, with the game's own glyph. */
@Composable
internal fun gameOptions(games: List<DuelGame>): List<FilterOption<GameId>> {
    val registry = koinInject<GamePlayRegistry>()
    return remember(games, registry) {
        games.map { gameFilterOption(it.id, it.name, registry.brandOf(it.id), tag = "duel.lineup.chip.game.${it.id.value}") }
    }
}
