package com.ivandsky.puzzlebox.create.importtext

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ImportResult

private const val BYTES_PER_KB = 1024
private const val LIMIT_KB = 64

/** NotationText's messages for a text with no readable header; it reports them as Malformed. */
private val HeaderMessages = setOf("missing header", "garbled header")

/**
 * The one plain line under the box after a failed import, tagged `create.import.error.<kind>`
 * (see [importErrorKind]) and announced politely to screen readers. Nothing for
 * [ImportResult.Imported], which is never an error.
 */
@Composable
fun ImportErrorLine(error: ImportResult, modifier: Modifier = Modifier) {
    if (error is ImportResult.Imported) return
    Text(
        text = importErrorCopy(error),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("create.import.error.${importErrorKind(error)}"),
    )
}

/** The kind a tag ends in: malformed, header, unknownGame, unsupportedVersion or tooLarge. */
internal fun importErrorKind(error: ImportResult): String = when (error) {
    is ImportResult.Malformed -> if (error.message in HeaderMessages) "header" else "malformed"
    is ImportResult.UnknownGame -> "unknownGame"
    is ImportResult.UnsupportedVersion -> "unsupportedVersion"
    is ImportResult.TooLarge -> "tooLarge"
    is ImportResult.Imported -> "imported"
}

/** One sentence that says what is wrong, where, and what to do about it. */
internal fun importErrorCopy(error: ImportResult): String = when (error) {
    is ImportResult.Malformed ->
        if (error.message in HeaderMessages) {
            "Line ${error.line}: expected a header like “puzzlebox queens v1”."
        } else {
            "Line ${error.line}, column ${error.col}: ${error.message}"
        }
    is ImportResult.UnknownGame -> "This PuzzleBox has no ${error.gameId} editor."
    is ImportResult.UnsupportedVersion ->
        "This text is version ${error.found}; this PuzzleBox reads up to ${error.maxKnown}. Update PuzzleBox."
    is ImportResult.TooLarge ->
        "That text is ${(error.bytes + BYTES_PER_KB - 1) / BYTES_PER_KB} KB (line ${error.line}); " +
            "the limit is $LIMIT_KB KB."
    is ImportResult.Imported -> ""
}

@PuzzleBoxPreviews
@Composable
private fun ImportErrorLinePreview() {
    PuzzleBoxTheme {
        ImportErrorLine(ImportResult.Malformed(line = 3, col = 2, message = "expected 5 rows"))
    }
}
