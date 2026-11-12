package com.ivandsky.puzzlebox.profile.accounts

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.designsystem.LinkedInBlue
import com.ivandsky.puzzlebox.designsystem.LinkedInConnectButton
import com.ivandsky.puzzlebox.designsystem.LinkedInResumeButton
import com.ivandsky.puzzlebox.designsystem.LocalWarning
import com.ivandsky.puzzlebox.designsystem.R
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInAccountView
import com.ivandsky.puzzlebox.gameui.PlayerAvatar

private val TileSize = 40.dp
private val PhotoSize = 44.dp
private val GlyphSize = 26.dp
private const val NOT_CONNECTED_SUB =
    "Import your own LinkedIn game results and see your connections' times on this phone."
private const val NOT_CONNECTED_SYNC_SUB =
    "Import your own LinkedIn game results and see your connections' times. " +
        "The boards you read are saved to your own PuzzleBox account, readable only by you, and kept after Disconnect."
private const val SIGN_IN_HINT = "Sign in with Google to keep these boards on every device"
private const val NO_NAME = "LinkedIn account"

/**
 * The LinkedIn account row. Stateless: it draws [state] and reports taps. Connect and Reconnect share [onConnect]
 * (the risk warning and login are the caller's); a paused link that needs a reconnect offers Reconnect, not
 * Resume, since resuming a rejected session would only pause again. [nowMs] only phrases the update time. With cloud
 * boards on ([boards] given) the copy says the boards are saved to the account, a connected row whose Google account
 * is signed out ([googleSignedIn] false) asks to sign in, and the overflow may offer Delete saved boards
 * ([onDeleteSavedBoards]); without it the row is exactly the plain one.
 */
@Composable
internal fun LinkedInRow(
    state: LinkedInRowState,
    nowMs: Long,
    onConnect: () -> Unit,
    onResume: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    boards: ProfileUiState.LinkedInBoards? = null,
    googleSignedIn: Boolean = true,
    onDeleteSavedBoards: () -> Unit = {},
) {
    val row = modifier.testTag("profile.linkedin")
    val overflow: @Composable () -> Unit = {
        LinkedInOverflow(
            onDisconnect = onDisconnect,
            onDeleteSavedBoards = if (boards?.canDelete == true) onDeleteSavedBoards else null,
            deleteEnabled = boards?.deleting != true,
        )
    }
    when (state) {
        LinkedInRowState.NotConnected -> AccountRow(
            lead = { Image(painterResource(R.drawable.ic_linkedin_dot), contentDescription = null, modifier = Modifier.size(TileSize)) },
            title = "LinkedIn",
            sub = if (boards?.savedToAccount == true) NOT_CONNECTED_SYNC_SUB else NOT_CONNECTED_SUB,
            modifier = row,
            extra = { LinkedInConnectButton("Connect LinkedIn", onConnect, Modifier.fillMaxWidth().testTag("profile.linkedin.connect")) },
        )
        is LinkedInRowState.Connected -> AccountRow(
            lead = { LinkedInLead(state.summary) },
            title = state.summary.name ?: NO_NAME,
            sub = connectedSub(state.summary),
            modifier = row,
            trailing = overflow,
            extra = { StatsAndBoards(state.summary, nowMs, boards, googleSignedIn) },
        )
        is LinkedInRowState.Paused -> AccountRow(
            lead = { LinkedInLead(state.summary) },
            title = state.summary.name ?: NO_NAME,
            sub = if (state.needsReconnect) "LinkedIn signed this phone out." else "Paused · ${state.reason}",
            modifier = row,
            subColor = LocalWarning.current,
            subWeight = FontWeight.SemiBold,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (!state.needsReconnect) {
                        LinkedInResumeButton("Resume", onResume, Modifier.testTag("profile.linkedin.resume"))
                    }
                    overflow()
                }
            },
            extra = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (state.needsReconnect) {
                        LinkedInConnectButton("Reconnect", onConnect, Modifier.fillMaxWidth().testTag("profile.linkedin.connect"))
                    } else {
                        StatsLine(state.summary, nowMs)
                    }
                    BoardsLines(boards, googleSignedIn)
                }
            },
        )
    }
}

@Composable
private fun StatsAndBoards(summary: LinkedInAccountView, nowMs: Long, boards: ProfileUiState.LinkedInBoards?, googleSignedIn: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StatsLine(summary, nowMs)
        BoardsLines(boards, googleSignedIn)
    }
}

/** Under the stats: the sign-in hint while boards are meant for the account but nobody is signed in, and why a Delete saved boards did not finish. */
@Composable
private fun BoardsLines(boards: ProfileUiState.LinkedInBoards?, googleSignedIn: Boolean) {
    if (boards == null) return
    if (boards.savedToAccount && !googleSignedIn) {
        Text(
            text = SIGN_IN_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("profile.linkedin.sign-in-hint"),
        )
    }
    boards.deleteMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("profile.linkedin.delete-boards-message"),
        )
    }
}

private fun connectedSub(summary: LinkedInAccountView): String =
    if (summary.name == null) {
        "Name and photo appear after the first update"
    } else {
        "${summary.connectionsSeen} connections seen · ${summary.daysThisWeek} days this week"
    }

@Composable
private fun StatsLine(summary: LinkedInAccountView, nowMs: Long) {
    val games = summary.games.joinToString(", ").ifEmpty { "no games yet" }
    Text(
        text = "${summary.daysTotal} days total · $games · ${linkedInUpdatedText(summary.updatedAtMs, nowMs)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The player's LinkedIn photo on a blue disc (the initial shows until it loads), or the mark while no name is stored. */
@Composable
private fun LinkedInLead(summary: LinkedInAccountView) {
    val name = summary.name
    if (name != null) {
        PlayerAvatar(name, photoUrl = summary.photoPath, size = PhotoSize, fill = LinkedInBlue, onFill = Color.White)
    } else {
        Box(Modifier.size(PhotoSize).background(LinkedInBlue, CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_linkedin_dot), contentDescription = null, modifier = Modifier.size(GlyphSize))
        }
    }
}
