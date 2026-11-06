package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.strands.StrandsRating
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy

/**
 * NYT's theme card: a label band over the clue, verbatim (A34, emoji included). Once
 * the board is solved the band carries the [rating] word instead of the label (A36). The
 * band is the soft primary container, so the loud brand green stays on the found words.
 */
@Composable
internal fun ThemeCard(clue: String, rating: StrandsRating?, modifier: Modifier = Modifier) {
    val band = MaterialTheme.colorScheme.primaryContainer
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(ThemeCardStroke, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("strands.theme_card"),
    ) {
        Column {
            Text(
                text = rating?.let(StrandsCopy::rating) ?: StrandsCopy.THEME_LABEL,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(band)
                    .padding(vertical = Spacing.xs)
                    .semantics { heading() }
                    .testTag("strands.theme_label"),
            )
            Text(
                text = clue,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                    .testTag("strands.clue"),
            )
        }
    }
}

/** NYT's 1 px tertiary card stroke. */
private val ThemeCardStroke = 1.dp
