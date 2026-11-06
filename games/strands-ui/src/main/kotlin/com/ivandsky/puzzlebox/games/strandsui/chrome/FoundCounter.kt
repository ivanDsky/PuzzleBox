package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy

/** "n of N theme words found", the two numbers bold as NYT sets them. */
@Composable
internal fun FoundCounter(found: Int, total: Int, modifier: Modifier = Modifier) {
    val text = remember(found, total) { boldNumbers(StrandsCopy.found(found, total)) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.testTag("strands.counter"),
    )
}

private val Digits = Regex("""\d+""")

private fun boldNumbers(copy: String): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (match in Digits.findAll(copy)) {
        append(copy.substring(at, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.value) }
        at = match.range.last + 1
    }
    append(copy.substring(at))
}
