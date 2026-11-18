package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.compete.create.NewDuelSheet
import com.ivandsky.puzzlebox.compete.create.NewDuelViewModel
import com.ivandsky.puzzlebox.compete.create.screen.NewDuelHost
import com.ivandsky.puzzlebox.compete.duel.DuelContent
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import org.koin.androidx.compose.koinViewModel

/**
 * The tablet's right pane while Games shows the Duels tab: the duel [DuelsTab] has selected, with every control of
 * the full duel screen ([DuelContent]) and no bar of its own, or [DuelsPaneEmpty] with a New duel button when there
 * are none. It reads the same [DuelsTabViewModel] as the list, so a tap on a row swaps the duel here.
 * Start, Resume and See results push their screens on [navigator]; [layout] is the app's play layout.
 *
 * A factory, not a composable: the pane is a Koin entry that cannot be previewed whole, so its previewed body is
 * [DuelsPaneContent] and the app gets this as the `@Composable (Modifier) -> Unit` its Games slot takes.
 */
fun duelsPane(navigator: Navigator, layout: PlayLayout): @Composable (Modifier) -> Unit =
    { modifier -> DuelsPaneHost(navigator, layout, modifier) }

/**
 * [DuelsPaneContent] over [DuelsTabViewModel]. With [DuelsTabViewModel.newDuelAsScreen] New duel is the pane's own
 * [NewDuelHost], on a [NewDuelViewModel] held at this entry's scope: the draft survives a look at another tab and is
 * reset once no New duel is being composed. Without it [sheet] draws the New duel sheet, so a test can stand in for its own view model.
 */
@Composable
internal fun DuelsPaneHost(
    navigator: Navigator,
    layout: PlayLayout,
    modifier: Modifier = Modifier,
    sheet: @Composable (inviter: String?, onDismiss: () -> Unit) -> Unit = { inviter, onDismiss -> NewDuelSheet(inviter, onDismiss) },
) {
    val viewModel: DuelsTabViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val request by viewModel.newDuel.collectAsStateWithLifecycle()
    val newDuelViewModel: NewDuelViewModel? = if (viewModel.newDuelAsScreen) koinViewModel() else null
    var newDuelOpen by rememberSaveable { mutableStateOf(false) }
    val composing = request
    if (newDuelViewModel != null) {
        LaunchedEffect(newDuelViewModel, composing == null) { if (composing == null) newDuelViewModel.reset() }
    }
    val form: (@Composable () -> Unit)? = if (newDuelViewModel != null && composing != null) {
        {
            NewDuelHost(
                challenge = composing.challenge,
                onClose = viewModel::endNewDuel,
                onOpened = viewModel::select,
                pane = true,
                viewModel = newDuelViewModel,
            )
        }
    } else {
        null
    }
    DuelsPaneContent(
        selected = state.selected,
        onNewDuel = { if (newDuelViewModel != null) viewModel.startNewDuel() else newDuelOpen = true },
        modifier = modifier,
        form = form,
    ) { code ->
        DuelContent(code.value, navigator, layout, onClose = { viewModel.leave(code) }, modifier = Modifier.fillMaxSize().testTag("duels.pane.duel"))
    }
    if (newDuelViewModel == null && newDuelOpen) sheet(state.inviter) { newDuelOpen = false }
}
