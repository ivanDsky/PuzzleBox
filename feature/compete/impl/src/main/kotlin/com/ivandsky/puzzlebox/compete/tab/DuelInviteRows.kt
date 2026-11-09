package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.share.UNKNOWN_INVITER
import com.ivandsky.puzzlebox.compete.share.inviteText
import com.ivandsky.puzzlebox.compete.share.shareInvite
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import java.time.ZoneId
import java.util.Locale

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

/**
 * My open invite nobody has accepted: "Waiting on a friend to accept" over "Code K7Q-2MX · Best of n". "Share again"
 * sends the same invite text as the created sheet, without games (the row does not carry them); [inviter] is the
 * signed-in player's name.
 */
@Composable
internal fun UnclaimedRow(row: DuelRow, inviter: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DuelRowShell(
        title = "Waiting on a friend to accept",
        sub = "Code ${row.code.display} · Best of ${row.legs}",
        onClick = onClick,
        modifier = modifier,
        avatar = { PlayerAvatar(name = "?", photoUrl = null, size = AvatarSize, tone = DuelTone.Them) },
        trailing = {
            SecondaryButton(
                text = "Share again",
                onClick = {
                    context.shareInvite(
                        inviteText(
                            inviter = inviter ?: UNKNOWN_INVITER,
                            bestOf = row.legs,
                            games = null,
                            endsAt = row.deadline,
                            zone = ZoneId.systemDefault(),
                            locale = Locale.getDefault(),
                            code = row.code,
                        ),
                    )
                },
                modifier = Modifier.testTag("duel.unclaimed.share"),
            )
        },
    )
}
