package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.compete.create.NewDuelSheet
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind
import java.time.Instant
import org.koin.androidx.compose.koinViewModel

/**
 * The body under the Games screen's Duels pill: the challenge card, then my duels, then a one-line note when
 * there are none. Owns its own scrolling; the Games host owns the side inset. [onOpenDuel] opens the duel
 * screen and [onOpenReveal] the reveal of a duel whose reveal is ready.
 */
@Composable
fun DuelsTab(
    onOpenDuel: (DuelKey) -> Unit,
    onOpenReveal: (DuelRevealKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DuelsTabViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle()
    var newDuelOpen by rememberSaveable { mutableStateOf(false) }
    DuelsTabContent(
        rows = state.value.rows,
        signedIn = state.value.signedIn,
        // Read only inside each row's time-left text, so the minute tick redraws that text and nothing else.
        now = { state.value.now },
        onNewDuel = { newDuelOpen = true },
        onOpenDuel = onOpenDuel,
        onOpenReveal = onOpenReveal,
        modifier = modifier,
    )
    // The created duel's code is dropped here for now: the share sheet (ticket 39) takes it from this callback.
    if (newDuelOpen) NewDuelSheet(onDismiss = { newDuelOpen = false }, onCreated = { newDuelOpen = false })
}

/** The stateless tab: [rows] and [signedIn] in, taps out; [now] is read only by the time-left text. */
@Composable
internal fun DuelsTabContent(
    rows: List<DuelRow>,
    signedIn: Boolean,
    now: () -> Instant,
    onNewDuel: () -> Unit,
    onOpenDuel: (DuelKey) -> Unit,
    onOpenReveal: (DuelRevealKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("duels.tab"),
        contentPadding = PaddingValues(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "challenge") { DuelChallengeCard(signedIn = signedIn, onClick = onNewDuel) }
        items(rows, key = { it.code.value }) { row ->
            val open = { onOpenDuel(DuelKey(row.code.value)) }
            when (row.kind) {
                DuelRowKind.Invited -> InvitedRow(row, onClick = open)
                DuelRowKind.Unclaimed -> UnclaimedRow(row, onClick = open)
                DuelRowKind.RevealReady ->
                    DuelCard(row, now, onClick = { onOpenReveal(DuelRevealKey(row.code.value)) })
                DuelRowKind.YourMove, DuelRowKind.Waiting -> DuelCard(row, now, onClick = open)
            }
        }
        if (rows.isEmpty()) item(key = "empty") { DuelsEmptyNote() }
    }
}
