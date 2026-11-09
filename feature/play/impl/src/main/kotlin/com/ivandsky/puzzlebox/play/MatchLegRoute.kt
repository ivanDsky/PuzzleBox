package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.FinishResult
import com.ivandsky.puzzlebox.gameui.LocalRouteCaption
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * One duel puzzle's board (prototype 02): the store's live play of the key's leg under the
 * normal play chrome, without a tutorial gate or How to play. The game id rides the key, so the
 * board opens, after a kill too, without the match.
 *
 * A taken puzzle never reopens (A22): before the play opens, my row in the outbox for this
 * puzzle (the single truth for "taken") sends the route to [onTaken] with that row's result.
 * The check waits up to [IDENTITY_WAIT_MS] for the signed-in player (restored after a kill
 * only some moments in), runs as signed out past that, and runs again when the player changes.
 *
 * Once the match is known the caption reads "Duel vs {opponent} · puzzle {n} of {N}"
 * ([LocalRouteCaption], so a game's own caption still wins for its moment). Signed in, the
 * bar's menu adds Give up, confirmed in a dialog: confirming queues the loss and calls
 * [onGaveUp]; Keep playing, the scrim and Back keep playing. Signed out there is no Give up,
 * as nothing could record it.
 */
@Composable
internal fun GameRoute(
    key: MatchLegKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    onGaveUp: () -> Unit,
    onTaken: (MatchLegResultKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = koinInject<PlaySessionStore>()
    val outbox = koinInject<MatchResultOutbox>()
    val matches = koinInject<MatchRepository>()
    val identity = koinInject<IdentityService>()
    val code = remember(key) { ShareCode.parse(key.code) }
    val uid = identity.current.collectAsStateWithLifecycle().value?.uid
    val taken: suspend (String?) -> MatchLegResultKey? = { who ->
        code?.let { takenResultKey(outbox.observe(it).first(), key, who) }
    }

    var checked by remember(key) { mutableStateOf(false) }
    val currentOnTaken by rememberUpdatedState(onTaken)
    LaunchedEffect(key, uid) {
        // Signed out so far: give a restoring player a moment; its arrival restarts this check.
        if (uid == null) delay(IDENTITY_WAIT_MS)
        val result = taken(uid)
        if (result != null) currentOnTaken(result) else checked = true
    }
    if (!checked) {
        GameLoading(onBack)
        return
    }

    val play = remember(key) { store.open(key) }
    val fetch by remember(code) { code?.let(matches::observe) ?: flowOf(null) }
        .collectAsStateWithLifecycle(initialValue = null)
    var confirming by rememberSaveable { mutableStateOf(false) }
    var givingUp by remember(key) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val giveUp = remember(key) { listOf(PlayOverflowItem(GIVE_UP, checked = null) { confirming = true }) }

    CompositionLocalProvider(LocalRouteCaption provides legPosition(fetch, key, uid)?.caption()) {
        GameBoard(
            play = play,
            pastDay = false,
            tutorial = TutorialGate.Closed,
            onHowToPlay = null,
            onTutorialFinished = {},
            layout = layout,
            onBack = onBack,
            onSolved = { _, timeMs, hintsUsed -> onSolved(timeMs, hintsUsed) },
            overflow = if (uid != null) giveUp else emptyList(),
            modifier = modifier,
        )
    }
    if (confirming && uid != null) {
        GiveUpDialog(
            onConfirm = {
                confirming = false
                if (!givingUp) {
                    givingUp = true
                    scope.launch {
                        when (store.giveUp(key)) {
                            FinishResult.Queued -> onGaveUp()
                            // A finish landed first (a solve at the same moment): its row is the result.
                            FinishResult.AlreadyRecorded -> taken(uid)?.let(onTaken) ?: onGaveUp()
                            FinishResult.SignedOut, null -> givingUp = false
                        }
                    }
                }
            },
            onCancel = { confirming = false },
        )
    }
}

/** Where a duel puzzle sits: the [opponent]'s name (null while the seat is open), its 1-based [number] of [count]. */
internal data class LegPosition(val opponent: String?, val number: Int, val count: Int)

/** [key]'s place in a found match as [uid] sees it; null until the match is found or when the index is past its puzzles. */
internal fun legPosition(fetch: MatchFetch?, key: MatchLegKey, uid: String?): LegPosition? {
    val match = (fetch as? MatchFetch.Found)?.match ?: return null
    if (key.legIndex !in match.legs.indices) return null
    return LegPosition(uid?.let(match::opponentOf)?.name, key.legIndex + 1, match.legs.size)
}

/** The result page of [key] when [rows] hold [uid]'s own result for it (A22: taken), else null (the board opens). */
internal fun takenResultKey(rows: List<OutboxResult>, key: MatchLegKey, uid: String?): MatchLegResultKey? {
    uid ?: return null
    val mine = rows.firstOrNull { it.result.uid == uid && it.result.legIndex == key.legIndex }?.result ?: return null
    return MatchLegResultKey(key.code, key.legIndex, mine.timeMs, mine.hints, mine.gaveUp)
}

private fun LegPosition.caption(): String = "Duel${opponent?.let { " vs $it" }.orEmpty()} · puzzle $number of $count"

/** The Give up confirm: a loss that can't be restarted (A22). Keep playing, the scrim and Back cancel. */
@Composable
private fun GiveUpDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AppDialog(
        title = "Give up this puzzle?",
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("leg.give_up.dialog"),
        content = {
            Text(
                text = "It counts as a loss for you, and you can't start it again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(text = "Keep playing", onClick = onCancel, modifier = Modifier.testTag("leg.give_up.cancel"))
                PrimaryButton(
                    text = GIVE_UP,
                    onClick = onConfirm,
                    destructive = true,
                    modifier = Modifier.testTag("leg.give_up.confirm"),
                )
            }
        },
    )
}

private const val GIVE_UP = "Give up"

/** How long a duel puzzle waits for the restored player before checking "taken" as signed out. */
private const val IDENTITY_WAIT_MS = 2_000L
