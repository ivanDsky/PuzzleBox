package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.resultui.PastYouAvatar
import java.time.Instant

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
 * One My duels row for a duel against Past you: the Past you avatar wearing [selfName]'s initial, its name over [selfDuelSub], and no deadline pill
 * (a self duel never expires), then a chevron (my move), a trophy and a chevron (results ready) or my score (finished).
 */
@Composable
internal fun SelfDuelRow(row: DuelRow, selfName: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    DuelRowShell(
        title = row.opponent?.name.orEmpty(),
        sub = selfDuelSub(row),
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        fill = if (row.kind == DuelRowKind.RevealReady) lerp(scheme.surface, scheme.primaryContainer, RESULTS_READY_BLEND) else Color.Unspecified,
        avatar = { PastYouAvatar(name = selfName, size = AvatarSize) },
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

private fun previewRow(kind: DuelRowKind, done: Int) = DuelRow(
    code = checkNotNull(ShareCode.parse("K7Q2MX")),
    opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null),
    kind = kind,
    legsDone = done,
    theirDone = 3,
    legs = 3,
    deadline = Instant.EPOCH,
    isSelf = true,
)

@PuzzleBoxPreviews
@Composable
internal fun SelfDuelRowPreview() = PuzzleBoxTheme {
    Column {
        SelfDuelRow(previewRow(DuelRowKind.YourMove, 1), "I", onClick = {})
        SelfDuelRow(previewRow(DuelRowKind.RevealReady, 3), "I", onClick = {})
        SelfDuelRow(previewRow(DuelRowKind.Finished(2, 1), 3), "I", onClick = {})
        SelfDuelRow(previewRow(DuelRowKind.Finished(1, 2), 3), "I", onClick = {})
    }
}
