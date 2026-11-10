package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDictionary
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import kotlinx.coroutines.withContext

/** What the word field says of the text typed so far: the dictionary's verdict, or [NoPath] with no path to type onto. */
internal enum class WordCheck { Empty, NoPath, Prefix, Word, Unknown }

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
 * With no path selected the field is off and says so ([WordCheck.NoPath]): nothing waits for a
 * later trace. Text longer than the selected path stays in the field, and
 * `strands.editor.word.fit` ([StrandsEditorCopy.overflow], state `TooLong`) replaces the
 * dictionary line to say how much of it the path takes.
 *
 * [compact] (the phone board) keeps the field one 56dp box: the label rests as its placeholder
 * (the trace-first hint while off) and the verdict is a trailing icon whose description reads
 * it. Only the fit line still shows under the field, while the word is too long: it says what
 * to fix, so it is shown, not just announced.
 */
@Composable
internal fun StrandsWordField(controller: StrandsEditorController, modifier: Modifier = Modifier, compact: Boolean = false) {
    val word by controller.word.collectAsState()
    val selected by controller.selectedPath.collectAsState()
    val editorDraft by controller.draft.collectAsState()
    val cells = selected?.let { (editorDraft.state.draft as StrandsDraft).paths.getOrNull(it)?.cells?.size }
    val overflow = cells != null && word.length > cells
    val dispatchers = LocalEditorDispatchers.current
    // The first lookup loads the word set; it runs off the main thread, Empty until it answers.
    val lookup by produceState(WordCheck.Empty, word) {
        value = withContext(dispatchers.default) { wordCheck(word) }
    }
    val check = if (cells == null) WordCheck.NoPath else lookup
    val label: @Composable () -> Unit = {
        WordLabel(if (compact && check == WordCheck.NoPath) StrandsEditorCopy.TRACE_FIRST else StrandsEditorCopy.WORD_FIELD)
    }
    val verdictIcon: @Composable () -> Unit = { WordCheckIcon(check, overflow) }
    val verdictLine: @Composable () -> Unit = { WordCheckLine(check) }
    val fit: @Composable () -> Unit = {
        Text(
            text = StrandsEditorCopy.overflow(word.length, cells ?: 0),
            modifier = Modifier
                .testTag("strands.editor.word.fit")
                .semantics { stateDescription = "TooLong" },
        )
    }
    OutlinedTextField(
        value = word,
        onValueChange = controller::typeWord,
        enabled = cells != null,
        singleLine = true,
        label = label.takeUnless { compact },
        placeholder = label.takeIf { compact },
        trailingIcon = verdictIcon.takeIf { compact },
        isError = check == WordCheck.Unknown || overflow,
        // Too long, the dictionary verdict is moot: only the fit line shows.
        supportingText = if (overflow) fit else verdictLine.takeUnless { compact },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("strands.editor.word"),
    )
}

@Composable
private fun WordLabel(text: String) {
    Text(text, Modifier.testTag("strands.editor.word.label"), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The dictionary verdict under the full field. */
@Composable
private fun WordCheckLine(check: WordCheck) {
    Text(
        // A no-break space, not "": an empty paragraph measures shorter than a line, and the
        // field, and the phone board sized round it, would shrink and grow with the verdict.
        text = verdictOf(check) ?: "\u00A0",
        // The off field dims its supporting text below 4.5:1; the hint must stay readable.
        color = if (check == WordCheck.NoPath) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        modifier = Modifier
            .testTag("strands.editor.word.check")
            .semantics { stateDescription = check.name },
    )
}

/**
 * The compact field's verdict: a tick for a word, a cross for none, a pencil while it can still
 * become one, its description the verdict; nothing while empty or off, where the placeholder
 * speaks. Too long, the cross is the fit line's and carries no verdict.
 */
@Composable
private fun WordCheckIcon(check: WordCheck, overflow: Boolean) {
    if (overflow) {
        Icon(PuzzleIcons.Close, contentDescription = null, modifier = Modifier.size(CheckIconSize))
        return
    }
    val verdict = verdictOf(check).takeUnless { check == WordCheck.NoPath }
    Box(
        Modifier
            .size(CheckIconSize)
            .testTag("strands.editor.word.check")
            .semantics {
                stateDescription = check.name
                if (verdict != null) contentDescription = verdict
            },
    ) {
        val icon = when (check) {
            WordCheck.Word -> PuzzleIcons.Check
            WordCheck.Unknown -> PuzzleIcons.Close
            WordCheck.Prefix -> PuzzleIcons.Create
            WordCheck.Empty, WordCheck.NoPath -> null
        }
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (check == WordCheck.Word) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
    }
}

/** What the dictionary says of the word, or null while nothing is typed. */
private fun verdictOf(check: WordCheck): String? = when (check) {
    WordCheck.Empty -> null
    WordCheck.NoPath -> StrandsEditorCopy.TRACE_FIRST
    WordCheck.Prefix -> "Keep typing"
    WordCheck.Word -> "In the dictionary"
    WordCheck.Unknown -> "Not in the dictionary"
}

private val CheckIconSize = 24.dp

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
