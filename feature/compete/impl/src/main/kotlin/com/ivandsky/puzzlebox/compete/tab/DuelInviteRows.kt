package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.duel.rememberCompeteTime
import com.ivandsky.puzzlebox.compete.share.UNKNOWN_INVITER
import com.ivandsky.puzzlebox.compete.share.inviteText
import com.ivandsky.puzzlebox.compete.share.shareInvite
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.gameui.PlayerAvatar

private val AvatarSize = 48.dp

/**
 * A duel invite for me: "{Name} challenged you" over "Best of n", or over [note] after a call that left the row in
 * place. Accept and Decline are disabled while [inFlight]; tapping the row opens the duel screen.
 */
@Composable
internal fun InvitedRow(
    row: DuelRow,
    inFlight: Boolean,
    note: InviteNote?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = row.opponent?.name ?: "A friend"
    DuelRowShell(
        title = "$name challenged you",
        sub = note?.text() ?: "Best of ${row.legs}",
        onClick = onClick,
        modifier = modifier.testTag("duel.invited"),
        avatar = { PlayerAvatar(name, row.opponent?.photoUrl, size = AvatarSize, tone = DuelTone.Them) },
        below = {
            Row(
                modifier = Modifier.padding(top = Spacing.sm).fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PrimaryButton(
                    text = "Accept",
                    onClick = onAccept,
                    enabled = !inFlight,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("duel.invited.accept"),
                )
                SecondaryButton(
                    text = "Decline",
                    onClick = onDecline,
                    enabled = !inFlight,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("duel.invited.decline"),
                )
            }
        },
    )
}

private fun InviteNote.text(): String = when (this) {
    InviteNote.AlreadyTaken -> "Already taken"
    InviteNote.Cancelled -> "Cancelled"
    InviteNote.Closed -> "Closed"
    InviteNote.AcceptFailed -> "Couldn't accept · try again"
    InviteNote.DeclineFailed -> "Couldn't decline · try again"
}

/**
 * My open invite nobody has accepted: "Waiting on a friend to accept" over "Code K7Q-2MX · Best of n". "Share again"
 * sends the same invite text as the created sheet, without games (the row does not carry them); [inviter] is the
 * signed-in player's name.
 */
@Composable
internal fun UnclaimedRow(row: DuelRow, inviter: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val time = rememberCompeteTime()
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
                            zone = time.zone,
                            locale = time.locale,
                            code = row.code,
                        ),
                    )
                },
                modifier = Modifier.testTag("duel.unclaimed.share"),
            )
        },
    )
}
