package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.gameui.PlayerAvatar

private val AvatarSize = 48.dp

/** A link invite this device opened: "{Name} challenged you" over "Best of n"; tapping opens the duel screen. */
@Composable
internal fun InvitedRow(row: DuelRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = row.opponent?.name ?: "A friend"
    DuelRowShell(
        title = "$name challenged you",
        sub = "Best of ${row.legs}",
        onClick = onClick,
        modifier = modifier,
        avatar = { PlayerAvatar(name, row.opponent?.photoUrl, size = AvatarSize, tone = DuelTone.Them) },
    )
}

/** My open invite nobody has accepted: "Waiting on a friend to accept" over "Code K7Q-2MX · Best of n". */
@Composable
internal fun UnclaimedRow(row: DuelRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DuelRowShell(
        title = "Waiting on a friend to accept",
        sub = "Code ${row.code.display} · Best of ${row.legs}",
        onClick = onClick,
        modifier = modifier,
        avatar = { PlayerAvatar(name = "?", photoUrl = null, size = AvatarSize, tone = DuelTone.Them) },
    )
}
