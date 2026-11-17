package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.ivandsky.puzzlebox.compete.common.PastYouAvatar
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind

/** The avatar's letter: Past you is the player's own earlier self, so it wears no friend's initial. */
private const val PAST_INITIAL = "I"

/** "You 1/3 · Best of 3" while playing or ready; "You won 2–1 · Best of 3" once finished and scored; else "Best of 3". */
internal fun selfDuelSub(row: DuelRow): String {
    val best = "Best of ${row.legs}"
    val kind = row.kind
    return when {
        kind is DuelRowKind.Finished -> kind.outcome()?.let { "You ${it.verb()} ${kind.myScore}–${kind.theirScore} · $best" } ?: best
        else -> "You ${row.legsDone}/${row.legs} · $best"
    }
}

private fun DuelOutcome.verb(): String = when (this) {
    DuelOutcome.Won -> "won"
    DuelOutcome.Lost -> "lost"
    DuelOutcome.Drawn -> "drew"
}

/**
 * One My duels row for a duel against Past you: the Past you avatar, its name over [selfDuelSub], and no deadline pill
 * (a self duel never expires), then a chevron (my move), a trophy and a chevron (results ready) or my score (finished).
 */
@Composable
internal fun SelfDuelRow(row: DuelRow, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    DuelRowShell(
        title = row.opponent?.name.orEmpty(),
        sub = selfDuelSub(row),
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        fill = if (row.kind == DuelRowKind.RevealReady) lerp(scheme.surface, scheme.primaryContainer, RESULTS_READY_BLEND) else Color.Unspecified,
        avatar = { PastYouAvatar(initial = PAST_INITIAL, size = AvatarSize) },
        trailing = {
            when (val kind = row.kind) {
                DuelRowKind.RevealReady -> {
                    TrophyDisc()
                    DuelChevron()
                }
                is DuelRowKind.Finished -> ScoreChip(kind)
                else -> DuelChevron()
            }
        },
    )
}
