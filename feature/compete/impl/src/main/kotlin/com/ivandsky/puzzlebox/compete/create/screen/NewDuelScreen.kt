package com.ivandsky.puzzlebox.compete.create.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.create.DuelStep
import com.ivandsky.puzzlebox.compete.create.NewDuelActions
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.SignInPanel
import com.ivandsky.puzzlebox.compete.create.lineup.LineupPickerModel
import com.ivandsky.puzzlebox.compete.create.lineup.lineupPicker
import com.ivandsky.puzzlebox.compete.create.lineup.title
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.PanePolicy

/** The form, the picker page and the Create bar stop growing here and sit centred, so a wide window does not stretch them. */
internal val NewDuelMaxWidth = 640.dp

private const val FORM_PAGE = "form"

/**
 * New duel as a page: "New duel" in the bar, the scrolling form, and the Create bar pinned under it. A signed-out
 * friend duel shows the sign-in panel in their place. [onBack] is the bar's arrow. [pane]: the tablet's pane draws it,
 * so the bar shows Close in place of the Back arrow.
 *
 * While the lineup editor has a target row and the screen is under [PanePolicy.ExpandedMinWidth] wide, the [picker]
 * replaces the form and the Create bar, the bar reads "Add puzzle N" or "Change puzzle N", and its arrow and the system
 * Back close the picker, not the screen. The form leaves composition meanwhile; its saved state is kept, so it comes
 * back open and scrolled where it was. At that width or more, [NewDuelWide] puts the picker beside the live form, the
 * bar keeps "New duel", and the picker gets an `onClose` to draw its own header and Close; the system Back still
 * closes the picker first.
 */
@Composable
internal fun NewDuelScreen(
    state: NewDuelState,
    actions: NewDuelActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pane: Boolean = false,
    picker: @Composable (LineupPickerModel, onClose: (() -> Unit)?) -> Unit = { _, _ -> },
) {
    // The sign-in step has no lineup to pick for.
    val page = if (state.step == DuelStep.Create) state.lineupPicker() else null
    val closePicker = { actions.onEditLineup { it.dismiss() } }
    BackHandler(enabled = page != null, onBack = closePicker)
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("duel.new.screen")) {
        val wide = maxWidth >= PanePolicy.ExpandedMinWidth
        // The page that takes the form's place; beside the form the bar keeps its own title and arrow.
        val swapped = page.takeUnless { wide }
        Column(Modifier.fillMaxSize()) {
            CompactTopBar(
                title = swapped?.title() ?: "New duel",
                navigationIcon = {
                    when {
                        swapped != null -> IconButton(onClick = closePicker, modifier = Modifier.testTag("duel.lineup.picker.back")) { Icon(PuzzleIcons.Back, contentDescription = "Back") }
                        pane -> IconButton(onClick = onBack, modifier = Modifier.testTag("duel.new.close")) { Icon(PuzzleIcons.Close, contentDescription = "Close") }
                        else -> IconButton(onClick = onBack, modifier = Modifier.testTag("duel.new.back")) { Icon(PuzzleIcons.Back, contentDescription = "Back") }
                    }
                },
            )
            when {
                state.step == DuelStep.SignIn -> Box(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.widthIn(max = NewDuelMaxWidth).fillMaxWidth()) { SignInPanel(state, actions) }
                }
                wide -> NewDuelWide(state, actions, page, { picker(it, closePicker) }, Modifier.weight(1f))
                else -> FormOrPicker(swapped, state, actions, { picker(it, null) }, Modifier.weight(1f))
            }
        }
    }
}

/** The form over its Create bar, or the picker in its place; the swap fades, and snaps under reduced motion. */
@Composable
private fun FormOrPicker(
    page: LineupPickerModel?,
    state: NewDuelState,
    actions: NewDuelActions,
    picker: @Composable (LineupPickerModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val saved = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = page,
        modifier = modifier,
        transitionSpec = {
            val enter = if (reduced) MotionTokens.DurationNone else MotionTokens.DurationMedium
            val exit = if (reduced) MotionTokens.DurationNone else MotionTokens.DurationShort
            fadeIn(tween(enter, easing = MotionTokens.EasingStandard)) togetherWith fadeOut(tween(exit, easing = MotionTokens.EasingStandard))
        },
        // Whatever the page holds changes in place: a later count of solved puzzles must not replay the swap.
        contentKey = { it != null },
        label = "newDuelPage",
    ) { shown ->
        if (shown == null) {
            saved.SaveableStateProvider(FORM_PAGE) {
                Column(Modifier.fillMaxSize()) {
                    NewDuelForm(state, actions, Modifier.weight(1f))
                    NewDuelCreateBar(state, actions)
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = NewDuelMaxWidth).fillMaxSize()) { picker(shown) }
            }
        }
    }
}
