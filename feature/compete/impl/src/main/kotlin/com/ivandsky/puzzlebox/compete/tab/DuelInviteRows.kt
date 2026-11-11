package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.Instant

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
 * My open invite nobody has accepted: "Waiting for a friend" over "Code K7Q-2MX · Best of n · 28h 20m left", a dashed
 * "?" for an avatar. "Share again" sends the same invite text as the created sheet, without games (the row does not
 * carry them); [inviter] is the signed-in player's name. [now] is read here for the time left.
 */
@Composable
internal fun UnclaimedRow(row: DuelRow, inviter: String?, now: () -> Instant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val time = rememberCompeteTime()
    DuelRowShell(
        title = "Waiting for a friend",
        sub = "Code ${row.code.display} · Best of ${row.legs} · ${timeLeft(row.deadline, now())} left",
        onClick = onClick,
        modifier = modifier,
        subLines = 2,
        avatar = { UnclaimedAvatar() },
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

private val DashStroke = 2.dp
private val DashLength = 6.dp

/** The avatar of a seat nobody has taken: a dashed outline round a "?". */
@Composable
private fun UnclaimedAvatar() {
    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outline
    Box(
        modifier = Modifier.size(AvatarSize).drawBehind {
            val stroke = DashStroke.toPx()
            drawCircle(
                color = outline,
                radius = (size.minDimension - stroke) / 2,
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashLength.toPx(), DashLength.toPx()))),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Text("?", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
    }
}
