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
 * §7 A/B switcher controls (debug): variant A/B toggle pairs for the four HITL
 * questions — X provenance cue, violation cue, palette density, cascade order.
 * Built as plain state-driven controls so queens-09 can flip variants in place;
 * losers are deleted after the hardware verdict.
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
                selected = variants.xProvenanceCue == XProvenanceCue.GEOMETRY,
                onClick = { onChange(variants.copy(xProvenanceCue = XProvenanceCue.GEOMETRY)) },
                label = { Text("X: geometry") },
            )
            FilterChip(
                selected = variants.xProvenanceCue == XProvenanceCue.SIZE_DOT,
                onClick = { onChange(variants.copy(xProvenanceCue = XProvenanceCue.SIZE_DOT)) },
                label = { Text("X: dot") },
            )
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = variants.cascadeOrder == CascadeOrder.PLACEMENT,
                onClick = { onChange(variants.copy(cascadeOrder = CascadeOrder.PLACEMENT)) },
                label = { Text("Win: placement") },
            )
            FilterChip(
                selected = variants.cascadeOrder == CascadeOrder.SCANLINE,
                onClick = { onChange(variants.copy(cascadeOrder = CascadeOrder.SCANLINE)) },
                label = { Text("Win: scanline") },
            )
        }
    }
}
