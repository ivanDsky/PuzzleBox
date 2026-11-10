package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.compete.create.DuelSignIn
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.domain.SeatAction
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.designsystem.timePulse
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.compose.koinInject

/** What the card's buttons do: the route turns each into a seat call, a sign-in or a pop. */
internal data class InviteActions(
    val onAccept: () -> Unit,
    val onDecline: () -> Unit,
    val onCancel: () -> Unit,
    val onClose: () -> Unit,
    val onSignIn: () -> Unit,
)

/**
 * A duel a viewer holds no seat in: who invites, the games, how many puzzles and when it ends, then the tail for
 * [invite]'s kind. The blocks fade in with a rise, 40 ms apart; the Accept button pulses once after signing in.
 */
@Composable
internal fun DuelInviteCard(invite: DuelUiState.Invite, actions: InviteActions, modifier: Modifier = Modifier) {
    val details = invite.details
    // A viewer who arrived signed out gets one pulse on Accept once the account is in; rotation does not repeat it.
    var cameSignedOut by rememberSaveable { mutableStateOf(false) }
    var pulsed by rememberSaveable { mutableStateOf(false) }
    if (invite.kind == InviteKind.SignedOut) cameSignedOut = true
    val pulse = cameSignedOut && !pulsed && invite.kind == InviteKind.Open
    LaunchedEffect(pulse) { if (pulse) pulsed = true }
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        InviteHeader(invite, Modifier.resultBeat(ResultBeats.Glyph, 0))
        Headline(details, Modifier.resultBeat(ResultBeats.Glyph, 1))
        if (details != null) Facts(details, Modifier.resultBeat(ResultBeats.Glyph, 2))
        Column(Modifier.resultBeat(ResultBeats.Glyph, 3), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            when (invite.kind) {
                InviteKind.SignedOut -> SignInTail(invite, actions)
                InviteKind.Open -> OpenTail(invite, actions, pulse)
                InviteKind.Taken -> Resolved(
                    title = "This duel already has two players",
                    message = "Someone accepted before you. Start a duel of your own.",
                    onClose = actions.onClose,
                )
                InviteKind.Cancelled -> Resolved("Cancelled", cancelledMessage(details), actions.onClose)
                InviteKind.Waiting -> WaitingTail(invite, actions)
            }
        }
    }
}

@Composable
private fun InviteHeader(invite: DuelUiState.Invite, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = if (invite.details?.viewerIsChallenger == true) "Your duel" else "Duel · from a friend",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = invite.code.display,
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(percent = 50))
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .testTag("duel.invite.code"),
        )
    }
}

@Composable
private fun Headline(details: InviteDetails?, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (details != null) PlayerAvatar(details.challenger.name, details.challenger.photoUrl, tone = DuelTone.Them)
        Text(
            text = when {
                details == null -> "A friend challenges you"
                details.viewerIsChallenger -> "You challenged a friend"
                else -> "${details.challenger.name} challenges you"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("duel.invite.headline"),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(details: InviteDetails, modifier: Modifier) {
    val brands = koinInject<GamePlayRegistry>()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            details.games.forEach { game ->
                GameChip(game, brands.brandOf(game.id))
            }
        }
        Text(
            text = "Best of ${details.rounds}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(Spacing.md))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .testTag("duel.invite.rounds"),
        )
        Text(
            text = "Ends ${endsAt(details)} · better result wins each puzzle",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A game's name and glyph on its accent container, like the friend puzzle card's chip. */
@Composable
private fun GameChip(game: InviteGame, brand: GameBrand) {
    val accent = brand.accent.colors()
    Row(
        modifier = Modifier
            .background(accent.container, CircleShape)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .testTag("duel.invite.game.${game.id.value}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        GameGlyph(brand, GameGlyphSize.Inline)
        Text(
            text = game.name,
            style = MaterialTheme.typography.labelLarge,
            color = accent.onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val EndsFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault())

private fun endsAt(details: InviteDetails): String = EndsFormat.format(details.deadline.atZone(ZoneId.systemDefault()))

private fun cancelledMessage(details: InviteDetails?): String = when {
    details == null -> "This duel was called off before anyone accepted."
    !details.viewerIsChallenger -> "${details.challenger.name} called this duel off before anyone accepted."
    details.declined -> "Your friend turned this duel down."
    else -> "You called this duel off before anyone accepted."
}

@Composable
private fun SignInTail(invite: DuelUiState.Invite, actions: InviteActions) {
    val signingIn = invite.signIn == DuelSignIn.SigningIn
    Callout(
        title = "Sign in to accept",
        message = "Duels need a Google account so the result is yours. Playing a friend's puzzle never does.",
    )
    when (invite.signIn) {
        DuelSignIn.Offline -> Note("No connection, so sign-in didn't finish. Try again when you're online.")
        DuelSignIn.Failed -> Note("Sign-in didn't finish. Please try again.")
        DuelSignIn.Idle, DuelSignIn.SigningIn -> Unit
    }
    SecondaryButton(
        text = if (signingIn) "Signing in…" else "Sign in with Google",
        onClick = actions.onSignIn,
        enabled = !signingIn,
        modifier = Modifier.fillMaxWidth().testTag("duel.invite.signIn"),
    )
    PrimaryButton("Accept duel", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().testTag("duel.invite.accept"))
}

@Composable
private fun OpenTail(invite: DuelUiState.Invite, actions: InviteActions, pulse: Boolean) {
    val notice = invite.notice
    val retrying = notice?.action == SeatAction.Claim
    Text(
        text = "First to accept takes the second seat.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (invite.details?.solvedBefore == true) {
        Note("You've solved one of these before. It still counts in this duel.")
    }
    if (notice != null) Note(noticeText(notice))
    PrimaryButton(
        text = if (retrying) "Try again" else "Accept duel",
        onClick = actions.onAccept,
        loading = invite.working,
        modifier = Modifier.fillMaxWidth().timePulse(trigger = pulse).testTag("duel.invite.accept"),
    )
    if (invite.details?.canDecline == true) {
        SecondaryButton("Decline", onClick = actions.onDecline, enabled = !invite.working, modifier = Modifier.fillMaxWidth().testTag("duel.invite.decline"))
    } else {
        SecondaryButton("Close", onClick = actions.onClose, modifier = Modifier.fillMaxWidth().testTag("duel.invite.close"))
    }
}

@Composable
private fun WaitingTail(invite: DuelUiState.Invite, actions: InviteActions) {
    Callout("Waiting on a friend", "Send them the code. The duel starts when they accept.")
    invite.notice?.let { Note(noticeText(it)) }
    SecondaryButton("Cancel", onClick = actions.onCancel, enabled = !invite.working, modifier = Modifier.fillMaxWidth().testTag("duel.invite.cancel"))
}

@Composable
private fun Resolved(title: String, message: String, onClose: () -> Unit) {
    Callout(title, message)
    SecondaryButton("Close", onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("duel.invite.close"))
}

/** What a seat call that did not finish says, by the call it was. */
private fun noticeText(notice: InviteNotice): String = when (notice) {
    is InviteNotice.Offline -> when (notice.action) {
        SeatAction.Claim -> "No connection, so the seat wasn't taken. Try again when you're online."
        SeatAction.Cancel -> "No connection, so the duel wasn't called off. Try again when you're online."
        SeatAction.Decline -> "No connection, so the invite wasn't turned down. Try again when you're online."
    }
    is InviteNotice.Closed -> when (notice.action) {
        SeatAction.Claim -> "This duel has ended, so the seat can't be taken."
        SeatAction.Cancel, SeatAction.Decline -> "This duel has already ended."
    }
    is InviteNotice.Failed -> notice.message ?: "That didn't work. Please try again."
}

@Composable
private fun Callout(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(Spacing.md))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
