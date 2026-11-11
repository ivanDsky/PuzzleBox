package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
import com.ivandsky.puzzlebox.designsystem.LocalWarning
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDictionary
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import kotlinx.coroutines.withContext

/** What the dictionary says of the text typed so far. */
internal enum class WordMatch { Empty, Prefix, Word, Unknown }

/** [text] is a dictionary word, the start of one, or neither. */
internal fun wordMatch(text: String): WordMatch = when {
    text.isEmpty() -> WordMatch.Empty
    StrandsDictionary.contains(text) -> WordMatch.Word
    StrandsDictionary.hasPrefix(text) -> WordMatch.Prefix
    else -> WordMatch.Unknown
}

/**
 * What the word field shows under its border, when it shows anything: [NoPath] with no path to
 * type onto, or [Warn] for text outside the dictionary. A quiet field has no check at all.
 */
internal enum class WordCheck { NoPath, Warn }

/**
 * Whether text of [length] letters that is a [match] of the dictionary can't become a word on a
 * path of [cells] cells: it is not a word and not the start of one, or it fills the path and is
 * still only the start of one. A word, an empty field and a prefix with room left are quiet.
 */
internal fun warnsFor(match: WordMatch, length: Int, cells: Int): Boolean = when (match) {
    WordMatch.Unknown -> true
    WordMatch.Prefix -> length >= cells
    WordMatch.Empty, WordMatch.Word -> false
}

/** The dispatchers the editor tools work on; tests swap in their own. */
internal val LocalEditorDispatchers = staticCompositionLocalOf<DispatcherProvider> {
    DefaultDispatcherProvider()
}

/**
 * The word field (`strands.editor.word`): letters only, checked against the dictionary as
 * they are typed. It is quiet for a dictionary word, a word still being typed and an empty
 * field. Only text that is not a dictionary word and can't become one ([warnsFor]) warns: an
 * amber border ([LocalWarning], not the error red) and `strands.editor.word.check` ([WordCheck.Warn],
 * [StrandsEditorCopy.NOT_IN_DICTIONARY]) under the field, which says it can still be used.
 * With no path selected the field is off and says so ([WordCheck.NoPath]): nothing waits for a
 * later trace. Text longer than the selected path stays in the field, and
 * `strands.editor.word.fit` ([StrandsEditorCopy.overflow], state `TooLong`) replaces the
 * dictionary line, so the field is an error and not a warning.
 *
 * [compact] (the phone board) keeps the field one 56dp box: the label rests as its placeholder
 * (the trace-first hint while off, kept at onSurfaceVariant to stay 4.5:1); once a word hides
 * the placeholder, the field's description still names it. The warning line and the fit line
 * show under the field only while they apply: each says what to know or fix, so it is shown,
 * not just announced.
 */
@Composable
internal fun StrandsWordField(controller: StrandsEditorController, modifier: Modifier = Modifier, compact: Boolean = false) {
    val word by controller.word.collectAsState()
    val selected by controller.selectedPath.collectAsState()
    val editorDraft by controller.draft.collectAsState()
    val cells = selected?.let { (editorDraft.state.draft as StrandsDraft).paths.getOrNull(it)?.cells?.size }
    val overflow = cells != null && word.length > cells
    val dispatchers = LocalEditorDispatchers.current
    // The first lookup loads the word set; it runs off the main thread. Its answer is kept with
    // the text it is for, so a stale one never warns about newer text: quiet until it answers.
    val lookup by produceState(Pair("", WordMatch.Empty), word) {
        value = word to withContext(dispatchers.default) { wordMatch(word) }
    }
    val match = lookup.second.takeIf { lookup.first == word } ?: WordMatch.Empty
    val check = when {
        cells == null -> WordCheck.NoPath
        !overflow && warnsFor(match, word.length, cells) -> WordCheck.Warn
        else -> null
    }
    val label: @Composable () -> Unit = {
        WordLabel(if (compact && check == WordCheck.NoPath) StrandsEditorCopy.TRACE_FIRST else StrandsEditorCopy.WORD_FIELD)
    }
    val trailing: (@Composable () -> Unit)? = when {
        !compact -> null
        overflow -> {
            { Icon(PuzzleIcons.Close, contentDescription = null, modifier = Modifier.size(CheckIconSize)) }
        }
        check == WordCheck.NoPath -> {
            { NoPathMark() }
        }
        else -> null
    }
    val fit: @Composable () -> Unit = {
        Text(
            text = StrandsEditorCopy.overflow(word.length, cells ?: 0),
            modifier = Modifier
                .testTag("strands.editor.word.fit")
                .semantics { stateDescription = "TooLong" },
        )
    }
    val line: @Composable () -> Unit = { WordCheckLine(check) }
    // The off field dims its placeholder below 4.5:1; the trace-first hint must stay readable.
    val readable = MaterialTheme.colorScheme.onSurfaceVariant
    val amber = LocalWarning.current
    val colors = if (check == WordCheck.Warn) {
        OutlinedTextFieldDefaults.colors(
            disabledPlaceholderColor = readable,
            focusedBorderColor = amber,
            unfocusedBorderColor = amber,
            focusedLabelColor = amber,
        )
    } else {
        OutlinedTextFieldDefaults.colors(disabledPlaceholderColor = readable)
    }
    OutlinedTextField(
        value = word,
        onValueChange = controller::typeWord,
        enabled = cells != null,
        singleLine = true,
        label = label.takeUnless { compact },
        placeholder = label.takeIf { compact },
        trailingIcon = trailing,
        // Only a word the path can't hold is an error: a word outside the dictionary is allowed.
        isError = overflow,
        supportingText = when {
            // Too long, the dictionary verdict is moot: only the fit line shows.
            overflow -> fit
            check == WordCheck.Warn -> line
            // The phone's field is one box: its placeholder carries the hint, a quiet field no line.
            compact -> null
            else -> line
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        colors = colors,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm)
            .testTag("strands.editor.word")
            .then(if (compact && word.isNotEmpty()) Modifier.semantics { contentDescription = StrandsEditorCopy.WORD_FIELD } else Modifier),
    )
}

@Composable
private fun WordLabel(text: String) {
    Text(text, Modifier.testTag("strands.editor.word.label"), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * The line under the full field: the trace-first hint, the dictionary warning, or, for a quiet
 * field, an unseen blank that keeps the line's height.
 */
@Composable
private fun WordCheckLine(check: WordCheck?) {
    if (check == null) {
        // A no-break space, not "": an empty paragraph measures shorter than a line, and the
        // pane below would shrink and grow with the warning.
        Text("\u00A0")
        return
    }
    Text(
        text = when (check) {
            WordCheck.NoPath -> StrandsEditorCopy.TRACE_FIRST
            WordCheck.Warn -> StrandsEditorCopy.NOT_IN_DICTIONARY
        },
        // The off field dims its supporting text below 4.5:1; the hint must stay readable.
        color = when (check) {
            WordCheck.NoPath -> MaterialTheme.colorScheme.onSurfaceVariant
            WordCheck.Warn -> LocalWarning.current
        },
        modifier = Modifier
            .testTag("strands.editor.word.check")
            .semantics { stateDescription = check.name },
    )
}

/** The compact off field's check: no mark, only the state; the placeholder speaks the hint. */
@Composable
private fun NoPathMark() {
    Box(
        Modifier
            .size(CheckIconSize)
            .testTag("strands.editor.word.check")
            .semantics { stateDescription = WordCheck.NoPath.name },
    )
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
