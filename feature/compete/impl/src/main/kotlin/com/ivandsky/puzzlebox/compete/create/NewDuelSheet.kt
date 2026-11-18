package com.ivandsky.puzzlebox.compete.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor
import com.ivandsky.puzzlebox.compete.duel.rememberCompeteTime
import com.ivandsky.puzzlebox.compete.share.DuelCreatedSheet
import com.ivandsky.puzzlebox.compete.share.createdInviteSaver
import com.ivandsky.puzzlebox.compete.share.createdInviteText
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.getKoin

/** What the sheet's panels can do; the host binds each to the view model. */
internal class NewDuelActions(
    val onQuick: () -> Unit,
    val onDaily: () -> Unit,
    val onCount: (Int) -> Unit,
    val onToggle: (DuelGame) -> Unit,
    val onDeadline: (Int) -> Unit,
    val onFriends: (Boolean) -> Unit,
    val onOpponent: (Opponent) -> Unit,
    val onCreate: () -> Unit,
    val onGoogle: () -> Unit,
    val onCancel: () -> Unit,
    /** Customize was opened; with the lineup on, it becomes the chosen preset. */
    val onCustomize: () -> Unit = {},
    /** Applies an edit to the lineup the Customize panel shows. */
    val onEditLineup: ((LineupEditor) -> LineupEditor) -> Unit = {},
)

/**
 * New duel: a Quick duel, today's puzzles, or Customize, then Create duel; with Past you offered, "Play against" picks
 * a friend or Past you (Quick or Customize only, no account). A signed-out player meets the sign-in
 * panel first for a friend duel. Once created, the sheet becomes "Duel created"; [onDismiss] runs on a swipe, the scrim, Cancel or Done,
 * and the sheet starts from the Quick duel again. [inviter] names the sender in the invite text. A [challenge] opens it
 * filled in: its friend is the invitee and the duel is one puzzle of its game. A duel against Past you has no code to
 * share: [onOpenDuel] opens it by its code and the sheet closes.
 * Motion: the sheet's **Slide in / Slide out** and the scrim's **Fade** are ModalBottomSheet's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewDuelSheet(
    inviter: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    challenge: DuelChallenge? = null,
    onOpenDuel: (ShareCode) -> Unit = {},
    viewModel: NewDuelViewModel = koinViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val koin = getKoin()
    val who by rememberUpdatedState(inviter)
    val time by rememberUpdatedState(rememberCompeteTime())
    var shared by rememberSaveable(stateSaver = createdInviteSaver) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.created.collect { created ->
            val games = created.inviteGames(koin.getOrNull())
            shared = createdInviteText(who, created.puzzles.size, games, created.deadlineHours, created.code, time) to created.code.matchLink
        }
    }
    LaunchedEffect(viewModel) { viewModel.refreshSolved() }
    LaunchedEffect(viewModel, challenge) { challenge?.let(viewModel::prefill) }
    val dismiss = {
        viewModel.reset()
        onDismiss()
    }
    val openDuel by rememberUpdatedState(onOpenDuel)
    val close by rememberUpdatedState(onDismiss)
    LaunchedEffect(viewModel) {
        viewModel.opened.collect { code ->
            openDuel(code)
            viewModel.reset()
            close()
        }
    }
    val actions = NewDuelActions(
        onQuick = viewModel::chooseQuick,
        onDaily = viewModel::chooseDaily,
        onCount = viewModel::setCount,
        onToggle = viewModel::toggle,
        onDeadline = viewModel::setDeadline,
        onFriends = viewModel::setFriends,
        onOpponent = viewModel::chooseOpponent,
        onCreate = viewModel::create,
        onGoogle = { scope.launch { viewModel.signIn(context) } },
        onCancel = dismiss,
        onCustomize = viewModel::chooseCustom,
        onEditLineup = viewModel::editLineup,
    )
    val created = shared
    if (created == null) {
        ModalBottomSheet(onDismissRequest = dismiss, modifier = modifier.testTag("duel.new.sheet"), sheetState = sheetState) {
            NewDuelContent(state, actions)
        }
    } else {
        DuelCreatedSheet(text = created.first, link = created.second, onDone = dismiss)
    }
}

/** The sheet's body alone: the create panel, or the sign-in panel while there is no account to create a friend duel under. */
@Composable
internal fun NewDuelContent(state: NewDuelState, actions: NewDuelActions) {
    when (state.step) {
        DuelStep.Create -> CreatePanel(state, actions)
        DuelStep.SignIn -> SignInPanel(state, actions)
    }
}

/** The title, the one-line intro and the "Play against" row, then the friend panel or Past you's; the column scrolls when taller than the window. */
@Composable
private fun CreatePanel(state: NewDuelState, actions: NewDuelActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("New duel", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = if (state.opponent == Opponent.PastYou) {
                "Race your first solve of each puzzle. It stays on this device."
            } else {
                "Same puzzles for you both, played in your own time. Opponent times stay sealed until you finish."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.selfOffered) PlayAgainstRow(state.opponent, state.selfName, actions.onOpponent)
        when (state.opponent) {
            Opponent.Friend -> FriendCreatePanel(state, actions)
            Opponent.PastYou -> SelfCreatePanel(state, actions)
        }
    }
}

/** Reveals the Customize panel's [content] when [open]; it grows in, and goes at once when closed. */
@Composable
internal fun CustomizeBody(open: Boolean, content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = open,
        enter = if (reduced) {
            fadeIn(tween(MotionTokens.ReducedFadeDuration))
        } else {
            expandVertically(tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)) +
                fadeIn(tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
        },
        exit = ExitTransition.None,
    ) { content() }
}

/** One choice: its [title] and [subtitle], marked when [selected], with [content] drawn under them. */
@Composable
internal fun PresetCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, outline),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
internal fun Chevron(expanded: Boolean) {
    val reduced = rememberReducedMotion()
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduced) snap() else tween(MotionTokens.EnterDuration),
        label = "customize chevron",
    )
    Icon(
        imageVector = PuzzleIcons.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp).rotate(turn),
    )
}

/** Why Create did not close the sheet, each in its own words; the choices above stay as they were. */
@Composable
internal fun Notice(notice: DuelNotice) {
    val text = when (notice) {
        DuelNotice.Offline -> "You're offline. Connect and try again."
        DuelNotice.Busy -> "Duels are busy today. Try again later."
        DuelNotice.NoPuzzles -> "There are no puzzles to draw for these games. Pick more games."
        is DuelNotice.EntryUndrawable -> "No unplayed ${notice.game} puzzle left for you both. Change puzzle ${notice.position}."
        is DuelNotice.EntryUnavailable -> "Puzzle ${notice.position} is no longer available. Change or remove it."
        is DuelNotice.Failed -> notice.message ?: "Couldn't create the duel. Please try again."
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("duel.notice"))
}
