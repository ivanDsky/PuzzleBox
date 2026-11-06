package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.StrandsHeader

/** "No. n · by <constructors>" under the theme card; an unnumbered day shows the credit alone. */
@Composable
internal fun StrandsHeaderLine(header: StrandsHeader, modifier: Modifier = Modifier) {
    Text(
        text = StrandsCopy.headerLine(header.puzzleNumber, header.credit),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.testTag("strands.header_line"),
    )
}
