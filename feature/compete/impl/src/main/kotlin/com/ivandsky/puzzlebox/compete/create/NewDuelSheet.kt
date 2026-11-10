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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.ivandsky.puzzlebox.compete.share.DuelCreatedSheet
import com.ivandsky.puzzlebox.compete.share.createdInviteSaver
import com.ivandsky.puzzlebox.compete.share.createdInviteText
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** What the sheet's panels can do; the host binds each to the view model. */
internal class NewDuelActions(
    val onQuick: () -> Unit,
    val onDaily: () -> Unit,
    val onCount: (Int) -> Unit,
    val onToggle: (DuelGame) -> Unit,
    val onDeadline: (Int) -> Unit,
    val onFriends: (Boolean) -> Unit,
    val onCreate: () -> Unit,
    val onGoogle: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * New duel: a Quick duel, today's puzzles, or Customize, then Create duel. A signed-out player meets the sign-in
 * panel first. Once created, the sheet becomes "Duel created"; [onDismiss] runs on a swipe, the scrim, Cancel or Done,
 * and the sheet starts from the Quick duel again. [inviter] names the sender in the invite text.
 * Motion: the sheet's **Slide in / Slide out** and the scrim's **Fade** are ModalBottomSheet's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewDuelSheet(
    inviter: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewDuelViewModel = koinViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val who by rememberUpdatedState(inviter)
    var shared by rememberSaveable(stateSaver = createdInviteSaver) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.created.collect { (code, games) ->
            val s = viewModel.state.value
            shared = createdInviteText(who, s.draft.count, games, s.draft.deadlineHours, code) to code.matchLink
        }
    }
    val dismiss = {
        viewModel.reset()
        onDismiss()
    }
    val actions = NewDuelActions(
        onQuick = viewModel::chooseQuick,
        onDaily = viewModel::chooseDaily,
        onCount = viewModel::setCount,
        onToggle = viewModel::toggle,
        onDeadline = viewModel::setDeadline,
        onFriends = viewModel::setFriends,
        onCreate = viewModel::create,
        onGoogle = { scope.launch { viewModel.signIn(context) } },
        onCancel = dismiss,
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

/** The sheet's body alone: the create panel, or the sign-in panel while there is no account to create under. */
@Composable
internal fun NewDuelContent(state: NewDuelState, actions: NewDuelActions) {
    when (state.step) {
        DuelStep.Create -> CreatePanel(state, actions)
        DuelStep.SignIn -> SignInPanel(state.signIn, actions)
    }
}

@Composable
private fun CreatePanel(state: NewDuelState, actions: NewDuelActions) {
    var customizing by rememberSaveable { mutableStateOf(false) }
    val draft = state.draft
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("New duel", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "Same puzzles for you both, played in your own time. Opponent times stay sealed until you finish.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PresetCard("Quick duel", "Best of 3 · mixed past dailies · 24 h", draft.preset == DuelPreset.Quick, actions.onQuick, "duel.preset.quick")
        PresetCard("Daily duel", "Today's puzzles · 24 h", draft.preset == DuelPreset.Daily, actions.onDaily, "duel.preset.daily") {
            if (draft.preset == DuelPreset.Daily) DailyGames(state.games, draft, actions.onToggle)
        }
        PresetCard(
            title = "Customize",
            subtitle = "Rounds, games, deadline, friends' puzzles",
            selected = draft.preset == DuelPreset.Custom,
            onClick = { customizing = !customizing },
            tag = "duel.preset.custom",
            trailing = { Chevron(expanded = customizing) },
        ) { CustomizeBody(customizing, state, actions) }
        state.notice?.let { Notice(it) }
        Text(
            text = state.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("duel.summary"),
        )
        PrimaryButton(
            text = "Create duel",
            onClick = actions.onCreate,
            enabled = state.canCreate,
            loading = state.creating,
            modifier = Modifier.fillMaxWidth().testTag("duel.create"),
        )
    }
}

@Composable
private fun CustomizeBody(open: Boolean, state: NewDuelState, actions: NewDuelActions) {
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
    ) { DuelCustomizePanel(state, actions, Modifier.padding(top = Spacing.sm)) }
}

/** One choice: its [title] and [subtitle], marked when [selected], with [content] drawn under them. */
@Composable
private fun PresetCard(
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
private fun Chevron(expanded: Boolean) {
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

/** One chip per game for today's puzzle: its number, or "Solved today" and unavailable once the player has it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyGames(games: List<DuelGame>, draft: DuelDraft, onToggle: (DuelGame) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        games.forEach { game ->
            val label = when {
                game.solvedToday -> "${game.name} · Solved today"
                game.todayNumber != null -> "${game.name} #${game.todayNumber}"
                else -> game.name
            }
            DuelGameChip(game, selected = game.id in draft.games, enabled = !game.solvedToday, label = label, onClick = { onToggle(game) })
        }
    }
}

/** Why Create did not close the sheet, each in its own words; the choices above stay as they were. */
@Composable
private fun Notice(notice: DuelNotice) {
    val text = when (notice) {
        DuelNotice.Offline -> "You're offline. Connect and try again."
        DuelNotice.Busy -> "Duels are busy today. Try again later."
        DuelNotice.NoPuzzles -> "There are no puzzles to draw for these games. Pick more games."
        is DuelNotice.Failed -> notice.message ?: "Couldn't create the duel. Please try again."
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("duel.notice"))
}

@Composable
private fun SignInPanel(signIn: DuelSignIn, actions: NewDuelActions) {
    val signingIn = signIn == DuelSignIn.SigningIn
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Sign in to duel friends", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "Duels tie each result to you, so friends can find you and the outcome is fair. Playing puzzles never needs an account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (signIn) {
            DuelSignIn.Offline -> SignInProblem("No connection, so sign-in didn't finish. Try again when you're online.")
            DuelSignIn.Failed -> SignInProblem("Sign-in didn't finish. Please try again.")
            DuelSignIn.Idle, DuelSignIn.SigningIn -> Unit
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton(
                text = if (signingIn) "Signing in…" else "Continue with Google",
                onClick = actions.onGoogle,
                enabled = !signingIn,
                modifier = Modifier.fillMaxWidth().testTag("duel.signIn.google"),
            )
            SecondaryButton(text = "Cancel", onClick = actions.onCancel, modifier = Modifier.fillMaxWidth().testTag("duel.signIn.cancel"))
        }
        Text(
            text = "Friends' puzzles and your dailies stay right here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignInProblem(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("duel.signIn.problem"))
}
