package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDictionary
import kotlinx.coroutines.withContext

/** What the dictionary says of the text typed so far. */
internal enum class WordCheck { Empty, Prefix, Word, Unknown }

/** [text] is a dictionary word, the start of one, or neither. */
internal fun wordCheck(text: String): WordCheck = when {
    text.isEmpty() -> WordCheck.Empty
    StrandsDictionary.contains(text) -> WordCheck.Word
    StrandsDictionary.hasPrefix(text) -> WordCheck.Prefix
    else -> WordCheck.Unknown
}

/** The dispatchers the editor tools work on; tests swap in their own. */
internal val LocalEditorDispatchers = staticCompositionLocalOf<DispatcherProvider> {
    DefaultDispatcherProvider()
}

/**
 * The word field (`strands.editor.word`): letters only, checked against the dictionary as
 * they are typed; the verdict is on `strands.editor.word.check` as its state description.
 */
@Composable
internal fun StrandsWordField(controller: StrandsEditorController, modifier: Modifier = Modifier) {
    val word by controller.word.collectAsState()
    val dispatchers = LocalEditorDispatchers.current
    // The first lookup loads the word set; it runs off the main thread, Empty until it answers.
    val check by produceState(WordCheck.Empty, word) {
        value = withContext(dispatchers.default) { wordCheck(word) }
    }
    OutlinedTextField(
        value = word,
        onValueChange = controller::typeWord,
        singleLine = true,
        label = {
            Text(
                StrandsEditorCopy.WORD_FIELD,
                Modifier.testTag("strands.editor.word.label"),
            )
        },
        isError = check == WordCheck.Unknown,
        supportingText = {
            Text(
                text = when (check) {
                    WordCheck.Empty -> ""
                    WordCheck.Prefix -> "Keep typing"
                    WordCheck.Word -> "In the dictionary"
                    WordCheck.Unknown -> "Not in the dictionary"
                },
                modifier = Modifier
                    .testTag("strands.editor.word.check")
                    .semantics { stateDescription = check.name },
            )
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("strands.editor.word"),
    )
}

/** The spangram switch (`strands.editor.spangram.<index>`) of the selected path. */
@Composable
internal fun StrandsSpangramToggle(
    index: Int,
    isSpangram: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = isSpangram,
        onClick = onToggle,
        label = { Text("Spangram") },
        modifier = modifier.testTag("strands.editor.spangram.$index"),
    )
}

@PuzzleBoxPreviews
@Preview(name = "Marked", showBackground = true, group = "Strands spangram toggle")
@Composable
private fun StrandsSpangramTogglePreview() {
    PuzzleBoxTheme {
        StrandsSpangramToggle(index = 0, isSpangram = true, onToggle = {}, modifier = Modifier.padding(Spacing.md))
    }
}
