package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strands.editor.StrandsPublishGates

private const val CREDIT =
    "Word list: ENABLE2K (Enhanced North American Benchmark LExicon) by Alan Beale and " +
        "M. Cooper, public domain. Source: enable2k.txt.gz, github.com/BartMassey/wordlists"

/**
 * Strands' publish details, at the foot of the Studio's details form: the theme clue
 * (`strands.editor.clue`) and the word list credit (`strands.editor.credit`). Every keystroke
 * sets the clue ([StrandsEditorController.setClue]), so the publish gate
 * ([StrandsPublishGates.CLUE_LENGTH]) follows it live; the counter (`.counter`) counts to
 * [StrandsPublishGates.MAX_CLUE], over it the field shows its error (`.error`) and while blank
 * says publishing needs it. Disabled while not [enabled] (a publish runs).
 */
@Composable
internal fun StrandsPublishFields(controller: StrandsEditorController, enabled: Boolean, modifier: Modifier = Modifier) {
    val draft by controller.draft.collectAsState()
    val clue = (draft.state.draft as StrandsDraft).clue
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StrandsClueField(clue, enabled, controller::setClue)
        Text(
            text = CREDIT,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("strands.editor.credit"),
        )
    }
}

@Composable
private fun StrandsClueField(clue: String, enabled: Boolean, onClue: (String) -> Unit) {
    // Held here so the caret survives the round trip through the draft.
    var text by remember(clue) { mutableStateOf(clue) }
    val tooLong = text.length > StrandsPublishGates.MAX_CLUE
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onClue(it)
        },
        enabled = enabled,
        singleLine = true,
        label = { Text(StrandsEditorCopy.CLUE_FIELD, Modifier.testTag("strands.editor.clue.label")) },
        isError = tooLong,
        supportingText = { ClueSupport(text.length, blank = text.isBlank(), tooLong = tooLong) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag("strands.editor.clue"),
    )
}

/** "Needed to publish" while blank, the cap's error over it, and the counter at the end. */
@Composable
private fun ClueSupport(length: Int, blank: Boolean, tooLong: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = when {
                tooLong -> StrandsEditorCopy.CLUE_TOO_LONG
                blank -> StrandsEditorCopy.CLUE_NEEDED
                else -> ""
            },
            color = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).then(if (tooLong) Modifier.testTag("strands.editor.clue.error") else Modifier),
        )
        Text(
            text = "$length/${StrandsPublishGates.MAX_CLUE}",
            color = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.testTag("strands.editor.clue.counter"),
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Clue and credit", showBackground = true, group = "Strands publish fields")
@Composable
private fun StrandsPublishFieldsPreview() {
    val draft = EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)
    val controller = remember { StrandsEditorController(draft) {}.apply { setClue("Around the house") } }
    PuzzleBoxTheme { StrandsPublishFields(controller, enabled = true, modifier = Modifier.padding(Spacing.md)) }
}
