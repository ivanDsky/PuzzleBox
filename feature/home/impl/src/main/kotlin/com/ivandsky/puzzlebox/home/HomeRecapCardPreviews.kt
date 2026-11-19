package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.SummaryLinkRow

private val OctoberRecap = RecapSlot(label = "October 2026", onOpen = {}, onDismiss = {})

// Preview: the recap card above another summary row at the 360 dp floor, then a long month name that must not clip.
@PuzzleBoxPreviews
@Composable
internal fun HomeRecapCardPhonePreview() {
    PuzzleBoxTheme {
        Column(
            Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg).width(360.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            HomeRecapCard(OctoberRecap)
            SummaryLinkRow(text = "Today's rank: #3 on Zip", onClick = {})
            HomeRecapCard(RecapSlot(label = "September 2026 in a very long language", onOpen = {}, onDismiss = {}))
        }
    }
}
