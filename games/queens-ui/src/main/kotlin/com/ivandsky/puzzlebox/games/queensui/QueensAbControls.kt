package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * §7 A/B switcher controls (debug): variant A/B toggle pairs for the HITL questions —
 * X provenance cue, violation cue, palette density. Built as plain state-driven
 * controls so queens-09 can flip variants in place; losers are deleted after the
 * hardware verdict. (The former A/B-4 win-cascade pair is deleted with the win
 * overlay — the solved state presents as the result route, fix round 6.)
 */
@Composable
internal fun QueensAbControls(
    variants: QueensAbVariants,
    onChange: (QueensAbVariants) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("A/B (queens-09 walk)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = variants.violationCue == ViolationCue.STRIPES_SCRIM,
                onClick = { onChange(variants.copy(violationCue = ViolationCue.STRIPES_SCRIM)) },
                label = { Text("V: stripes") },
            )
            FilterChip(
                selected = variants.violationCue == ViolationCue.TINT,
                onClick = { onChange(variants.copy(violationCue = ViolationCue.TINT)) },
                label = { Text("V: tint") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = variants.paletteDensity == PaletteDensity.LIGHTNESS_ALTERNATED,
                onClick = { onChange(variants.copy(paletteDensity = PaletteDensity.LIGHTNESS_ALTERNATED)) },
                label = { Text("Palette: ΔL") },
            )
            FilterChip(
                selected = variants.paletteDensity == PaletteDensity.HUE_ONLY,
                onClick = { onChange(variants.copy(paletteDensity = PaletteDensity.HUE_ONLY)) },
                label = { Text("Palette: hue") },
            )
        }
    }
}
