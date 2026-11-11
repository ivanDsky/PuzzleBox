package com.ivandsky.puzzlebox.profile.accounts

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
private const val NO_NAME = "LinkedIn account"

/**
 * The LinkedIn account row. Stateless: it draws [state] and reports taps. Connect and Reconnect share [onConnect]
 * (the risk warning and login are the caller's); a paused link that needs a reconnect offers Reconnect, not
 * Resume, since resuming a rejected session would only pause again. [nowMs] only phrases the update time.
 */
@Composable
internal fun LinkedInRow(
    state: LinkedInRowState,
    nowMs: Long,
    onConnect: () -> Unit,
    onResume: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val row = modifier.testTag("profile.linkedin")
    when (state) {
        LinkedInRowState.NotConnected -> AccountRow(
            lead = { Image(painterResource(R.drawable.ic_linkedin_dot), contentDescription = null, modifier = Modifier.size(TileSize)) },
            title = "LinkedIn",
            sub = NOT_CONNECTED_SUB,
            modifier = row,
            extra = { LinkedInConnectButton("Connect LinkedIn", onConnect, Modifier.fillMaxWidth().testTag("profile.linkedin.connect")) },
        )
        is LinkedInRowState.Connected -> AccountRow(
            lead = { LinkedInLead(state.summary) },
            title = state.summary.name ?: NO_NAME,
            sub = connectedSub(state.summary),
            modifier = row,
            trailing = { LinkedInOverflow(onDisconnect) },
            extra = { StatsLine(state.summary, nowMs) },
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
                    LinkedInOverflow(onDisconnect)
                }
            },
            extra = if (state.needsReconnect) {
                { LinkedInConnectButton("Reconnect", onConnect, Modifier.fillMaxWidth().testTag("profile.linkedin.connect")) }
            } else {
                { StatsLine(state.summary, nowMs) }
            },
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
