package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.compete.create.NewDuelSheet
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import org.koin.androidx.compose.koinViewModel

/**
 * The body under the Games screen's Duels pill: the challenge card, then my duels in their sections, then a one-line
 * note when there are none. It scrolls as one list on [listState], which the Games host holds so it can send the list
 * to its top; the host owns the side inset. [onOpenDuel] opens the duel screen and [onOpenReveal] the reveal of a duel
 * whose reveal is ready or already seen.
 */
@Composable
fun DuelsTab(
    onOpenDuel: (DuelKey) -> Unit,
    onOpenReveal: (DuelRevealKey) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val viewModel: DuelsTabViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenDuel by rememberUpdatedState(onOpenDuel)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.openDuel.collect { currentOnOpenDuel(DuelKey(it.value)) }
        }
    }
    var newDuelOpen by rememberSaveable { mutableStateOf(false) }
    DuelsTabContent(
        rows = state.value.rows,
        signedIn = state.value.signedIn,
        inviter = state.value.inviter,
        // Read only inside each row's time-left text, so the minute tick redraws that text and nothing else.
        now = { state.value.now },
        onNewDuel = { newDuelOpen = true },
        onOpenDuel = onOpenDuel,
        onOpenReveal = onOpenReveal,
        modifier = modifier,
        listState = listState,
        inFlight = state.value.inFlight,
        notes = state.value.notes,
        onAccept = viewModel::accept,
        onDecline = viewModel::decline,
    )
    if (newDuelOpen) NewDuelSheet(inviter = state.value.inviter, onDismiss = { newDuelOpen = false })
}

/**
 * The stateless tab: [rows], [signedIn], and the Invited rows' [inFlight] and [notes] in, taps out; [now] is read only by
 * the time-left text. The rows sit in their sections ([sectionRows]), one column on [listState].
 */
@Composable
internal fun DuelsTabContent(
    rows: List<DuelRow>,
    signedIn: Boolean,
    inviter: String?,
    now: () -> Instant,
    onNewDuel: () -> Unit,
    onOpenDuel: (DuelKey) -> Unit,
    onOpenReveal: (DuelRevealKey) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    inFlight: Set<ShareCode> = emptySet(),
    notes: Map<ShareCode, InviteNote> = emptyMap(),
    onAccept: (ShareCode) -> Unit = {},
    onDecline: (ShareCode) -> Unit = {},
) {
    val sections = remember(rows) { sectionRows(rows) }
    val section: @Composable (SectionRows) -> Unit = { group ->
        DuelSectionCard(group) { row ->
            val open = { onOpenDuel(DuelKey(row.code.value)) }
            // One tag per row of any kind: the kinds carry their own tags (an invite's, say), a second would clash.
            Box(Modifier.testTag("duel.row.${row.code.value}")) {
                when (row.kind) {
                    DuelRowKind.Invited -> InvitedRow(
                        row = row,
                        inFlight = row.code in inFlight,
                        note = notes[row.code],
                        onAccept = { onAccept(row.code) },
                        onDecline = { onDecline(row.code) },
                        onClick = open,
                    )
                    DuelRowKind.Unclaimed -> UnclaimedRow(row, inviter, now, onClick = open)
                    DuelRowKind.RevealReady, is DuelRowKind.Finished ->
                        DuelCard(row, now, onClick = { onOpenReveal(DuelRevealKey(row.code.value)) })
                    DuelRowKind.YourMove, DuelRowKind.Waiting -> DuelCard(row, now, onClick = open)
                }
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("duels.tab"),
        contentPadding = PaddingValues(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item(key = "challenge") { DuelChallengeCard(signedIn = signedIn, onClick = onNewDuel) }
        items(sections, key = { it.section.tag }) { section(it) }
        if (rows.isEmpty()) item(key = "empty") { DuelsEmptyNote() }
    }
}

