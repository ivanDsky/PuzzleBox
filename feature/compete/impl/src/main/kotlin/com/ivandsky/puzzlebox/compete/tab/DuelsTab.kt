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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.compete.api.NewDuelKey
import com.ivandsky.puzzlebox.compete.create.DuelChallenge
import com.ivandsky.puzzlebox.compete.create.DuelChallengeSaver
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
 * whose reveal is ready or already seen. A pending [challenge] opens New duel filled in, once: [onChallengeTaken]
 * runs as it takes the challenge, and closing the sheet drops it. [onNewDuel] opens New duel as a screen (the phone, with
 * the flag on); without it, or in the pane, New duel is the sheet. With [inPane] (the tablet, whose right pane is [DuelsPane])
 * a row, an accepted invite and a reveal row select that duel for the pane instead of calling [onOpenDuel] and
 * [onOpenReveal], and the selected row is highlighted.
 */
@Composable
fun DuelsTab(
    onOpenDuel: (DuelKey) -> Unit,
    onOpenReveal: (DuelRevealKey) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    challenge: DuelChallenge? = null,
    onChallengeTaken: () -> Unit = {},
    inPane: Boolean = false,
    onNewDuel: ((NewDuelKey) -> Unit)? = null,
) {
    val viewModel: DuelsTabViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle()
    val select: (String) -> Unit = { code -> ShareCode.parse(code)?.let(viewModel::select) }
    val open: (DuelKey) -> Unit = if (inPane) ({ select(it.code) }) else onOpenDuel
    val openReveal: (DuelRevealKey) -> Unit = if (inPane) ({ select(it.code) }) else onOpenReveal
    val currentOnOpenDuel by rememberUpdatedState(open)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.openDuel.collect { currentOnOpenDuel(DuelKey(it.value)) }
        }
    }
    var newDuelOpen by rememberSaveable { mutableStateOf(false) }
    // The challenge the open sheet was started with; the host's copy is gone once it has been taken.
    var sheetChallenge by rememberSaveable(stateSaver = DuelChallengeSaver) { mutableStateOf<DuelChallenge?>(null) }
    // A screen where there is one to push (the phone, flag on); else the sheet, over this tab.
    val newDuelScreen = onNewDuel.takeIf { viewModel.newDuelAsScreen && !inPane }
    val startNewDuel: (DuelChallenge?) -> Unit = { start ->
        if (newDuelScreen != null) {
            newDuelScreen(NewDuelKey(start?.friendUid.orEmpty(), start?.friendName.orEmpty(), start?.game?.value.orEmpty()))
        } else {
            sheetChallenge = start
            newDuelOpen = true
        }
    }
    TakeDuelChallenge(challenge, onTaken = onChallengeTaken, open = startNewDuel)
    DuelsTabContent(
        rows = state.value.rows,
        signedIn = state.value.signedIn,
        selfName = state.value.selfName,
        inviter = state.value.inviter,
        // Read only inside each row's time-left text, so the minute tick redraws that text and nothing else.
        now = { state.value.now },
        onNewDuel = { startNewDuel(null) },
        onOpenDuel = open,
        onOpenReveal = openReveal,
        modifier = modifier,
        listState = listState,
        selected = state.value.selected.takeIf { inPane },
        inFlight = state.value.inFlight,
        notes = state.value.notes,
        onAccept = viewModel::accept,
        onDecline = viewModel::decline,
    )
    if (newDuelOpen) {
        NewDuelSheet(
            inviter = state.value.inviter,
            onDismiss = {
                newDuelOpen = false
                sheetChallenge = null
            },
            challenge = sheetChallenge,
            onOpenDuel = { code -> open(DuelKey(code.value)) },
        )
    }
}

/**
 * The stateless tab: [rows], [signedIn], and the Invited rows' [inFlight] and [notes] in, taps out; [now] is read only by
 * the time-left text. The rows sit in their sections ([sectionRows]), one column on [listState]; the row of [selected]
 * is highlighted.
 */
@Composable
internal fun DuelsTabContent(
    rows: List<DuelRow>,
    signedIn: Boolean,
    selfName: String = "You",
    inviter: String?,
    now: () -> Instant,
    onNewDuel: () -> Unit,
    onOpenDuel: (DuelKey) -> Unit,
    onOpenReveal: (DuelRevealKey) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    selected: ShareCode? = null,
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
            val isSelected = row.code == selected
            // Only a list that has a selection says which row it is; the phone's rows stay as they were.
            val marked = if (selected == null) Modifier else Modifier.semantics { this.selected = isSelected }
            Box(Modifier.testTag("duel.row.${row.code.value}").then(marked)) {
                when (row.kind) {
                    DuelRowKind.Invited -> InvitedRow(
                        row = row,
                        inFlight = row.code in inFlight,
                        note = notes[row.code],
                        onAccept = { onAccept(row.code) },
                        onDecline = { onDecline(row.code) },
                        onClick = open,
                        selected = isSelected,
                    )
                    DuelRowKind.Unclaimed -> UnclaimedRow(row, inviter, now, onClick = open, selected = isSelected)
                    DuelRowKind.RevealReady, is DuelRowKind.Finished -> {
                        val reveal = { onOpenReveal(DuelRevealKey(row.code.value)) }
                        if (row.isSelf) SelfDuelRow(row, selfName, onClick = reveal, selected = isSelected)
                        else DuelCard(row, now, onClick = reveal, selected = isSelected)
                    }
                    DuelRowKind.YourMove, DuelRowKind.Waiting ->
                        if (row.isSelf) SelfDuelRow(row, selfName, onClick = open, selected = isSelected)
                        else DuelCard(row, now, onClick = open, selected = isSelected)
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

