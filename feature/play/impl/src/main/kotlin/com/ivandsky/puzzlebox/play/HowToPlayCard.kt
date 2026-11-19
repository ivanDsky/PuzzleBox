package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors

/**
 * How to play (06-a §How-to-play card): the game's three [LibraryRowModel.rules], each on
 * a numbered accent disc, and "Open the lesson" ([onOpenLesson]) below them;
 * [hasLesson] false hides it. A non-null [onMakeOwn] ends the card with "Make your own <Game>" in the same pill.
 */
@Composable
internal fun HowToPlayCard(
    row: LibraryRowModel,
    hasLesson: Boolean,
    onOpenLesson: () -> Unit,
    modifier: Modifier = Modifier,
    onMakeOwn: (() -> Unit)? = null,
) {
    val accent = row.card.brand.accent.colors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShapes.extraLarge)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("library.howto"),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Eyebrow("HOW TO PLAY")
        row.rules.forEachIndexed { index, rule -> NumberedRule(index + 1, rule, accent) }
        if (hasLesson || onMakeOwn != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (hasLesson) CardPill(PuzzleIcons.HowTo, "Open the lesson", onOpenLesson, "library.howto.lesson")
                onMakeOwn?.let { make -> CardPill(PuzzleIcons.Create, "Make your own ${row.card.name}", make, "library.howto.makeOwn") }
            }
        }
    }
}

@Composable
private fun CardPill(icon: ImageVector, text: String, onClick: () -> Unit, tag: String) {
    FilledTonalButton(
        onClick = onClick,
        shape = PillShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.height(48.dp).testTag(tag),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
