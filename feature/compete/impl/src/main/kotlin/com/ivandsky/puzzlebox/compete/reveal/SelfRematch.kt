package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.navigation.Navigator
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A self duel's Rematch, which has no sheet: there is nobody to invite, so tapping it draws the new duel at once
 * ([RematchViewModel] through `CreateSelfDuel`) and opens it. A refusal ("no puzzles left") shows as a small dialog.
 * Returns the tap's action.
 */
@Composable
internal fun rememberSelfRematch(match: Match, navigator: Navigator): () -> Unit {
    val viewModel: RematchViewModel = koinViewModel(key = "rematch:${match.code.value}", parameters = { parametersOf(match) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open by rememberUpdatedState<(String) -> Unit> { navigator.push(DuelKey(it)) }
    LaunchedEffect(viewModel) {
        viewModel.created.collect {
            viewModel.reset()
            open(it.code.value)
        }
    }
    state.note?.let { RematchNoteDialog(it, onOk = viewModel::reset) }
    return viewModel::send
}

/** The refusal's dialog, the app's own: [note] under a title, one OK. */
@Composable
internal fun RematchNoteDialog(note: String, onOk: () -> Unit) {
    AppDialog(
        title = "Rematch",
        onDismissRequest = onOk,
        modifier = Modifier.testTag("reveal.rematch.note"),
        content = { Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        actions = { AppTextButton(text = "OK", onClick = onOk, modifier = Modifier.testTag("reveal.rematch.ok")) },
    )
}
