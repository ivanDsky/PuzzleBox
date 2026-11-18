package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.DuelSnapshot
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * One puzzle of the duel as an invite lists it (spec A8, A19): a daily leg is its game, a custom leg its [title] in
 * quotes, or "<game> · <code>" while no title could be read. [code] is null for a daily leg.
 */
internal data class InviteLeg(val gameId: GameId, val game: String, val code: ShareCode?, val title: String? = null) {
    val label: String
        get() = when {
            code == null -> game
            title != null -> "\"$title\""
            else -> "$game · ${code.display}"
        }
}

/** [legs] in leg order, named by [nameOf], titled by [titles] where a custom leg's title is known. */
internal fun inviteLegs(legs: List<LegRef>, nameOf: (GameId) -> String, titles: Map<ShareCode, String> = emptyMap()): List<InviteLeg> =
    legs.map { leg ->
        val code = (leg as? LegRef.Custom)?.code
        InviteLeg(leg.gameId, nameOf(leg.gameId), code, code?.let(titles::get))
    }

internal fun List<InviteLeg>.titled(titles: Map<ShareCode, String>): List<InviteLeg> =
    map { leg -> leg.code?.let(titles::get)?.let { leg.copy(title = it) } ?: leg }

/** The codes of the custom legs: the ones a title may be read for. */
internal fun List<LegRef>.customCodes(): List<ShareCode> = filterIsInstance<LegRef.Custom>().map { it.code }

private fun DuelSnapshot.legRefs(): List<LegRef> = when (val state = match) {
    is MatchState.Loaded -> state.view.match.legs.map { it.ref }
    is MatchState.SignedOut -> state.match?.legs?.map { it.ref }.orEmpty()
    else -> emptyList()
}

/**
 * The titles of the custom legs of the duel this flow shows, read once per set of codes; none while [lineupOn] is
 * false. Starts with none, so the card draws the codes first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<DuelSnapshot>.legTitlesBy(titles: ObserveLegTitles, lineupOn: () -> Boolean): Flow<Map<ShareCode, String>> =
    map { duel -> if (lineupOn()) duel.legRefs().customCodes() else emptyList() }
        .distinctUntilChanged()
        .flatMapLatest { codes -> if (codes.isEmpty()) flowOf(emptyMap()) else titles(codes) }
        .onStart { emit(emptyMap()) }

private val NumberWidth = 20.dp

/** The invite's puzzles top to bottom in leg order, each a numbered game chip. */
@Composable
internal fun InviteLineup(legs: List<InviteLeg>, brands: GamePlayRegistry, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        legs.forEachIndexed { index, leg ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = NumberWidth),
                )
                GameChip(leg.label, brands.brandOf(leg.gameId), "duel.invite.leg.$index")
            }
        }
    }
}
