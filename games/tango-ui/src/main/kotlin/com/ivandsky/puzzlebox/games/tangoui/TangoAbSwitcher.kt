package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel

/**
 * Debug A/B switcher (design-direction §8, Showkase-family surface): every open
 * verdict parameter is toggleable, both placement variants reachable in light AND
 * dark (two Showkase entries). Marker scale (§8 item 5) is intentionally absent —
 * it is a live-measurement gate on the tablet, not a preset. Ticket 06 records the
 * user's verdicts; losers are DELETED from [TangoAbVariants], not kept behind flags.
 */
@Composable
fun TangoAbSwitcher(darkTheme: Boolean) {
    var variants by remember { mutableStateOf(TangoAbVariants()) }
    var board by remember { mutableStateOf(sampleAbBoard()) }
    PuzzleBoxTheme(darkTheme = darkTheme) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Tango A/B", style = MaterialTheme.typography.titleLarge)
            VariantRow("Placement (A11)") {
                FilterChip(
                    selected = variants.placement == TangoPlacementVariant.TapCycle,
                    onClick = { variants = variants.copy(placement = TangoPlacementVariant.TapCycle) },
                    label = { Text("A · tap-cycle") },
                )
                FilterChip(
                    selected = variants.placement == TangoPlacementVariant.TwoTapChooser,
                    onClick = { variants = variants.copy(placement = TangoPlacementVariant.TwoTapChooser) },
                    label = { Text("B · two-tap") },
                )
            }
            VariantRow("Violation strength") {
                FilterChip(
                    selected = variants.violationStrength == TangoViolationStrength.Captioned,
                    onClick = { variants = variants.copy(violationStrength = TangoViolationStrength.Captioned) },
                    label = { Text("A · caption") },
                )
                FilterChip(
                    selected = variants.violationStrength == TangoViolationStrength.Silent,
                    onClick = { variants = variants.copy(violationStrength = TangoViolationStrength.Silent) },
                    label = { Text("B · silent") },
                )
            }
            VariantRow("Count indicators") {
                for (option in TangoCountIndicators.entries) {
                    FilterChip(
                        selected = variants.countIndicators == option,
                        onClick = { variants = variants.copy(countIndicators = option) },
                        label = { Text(option.name.lowercase()) },
                    )
                }
            }
            VariantRow("Glyph style") {
                FilterChip(
                    selected = variants.glyphStyle == TangoGlyphStyle.Solid,
                    onClick = { variants = variants.copy(glyphStyle = TangoGlyphStyle.Solid) },
                    label = { Text("A · solid") },
                )
                FilterChip(
                    selected = variants.glyphStyle == TangoGlyphStyle.Hollow,
                    onClick = { variants = variants.copy(glyphStyle = TangoGlyphStyle.Hollow) },
                    label = { Text("B · hollow") },
                )
            }
            VariantRow("Cell treatment") {
                FilterChip(
                    selected = variants.cellTreatment == TangoCellTreatment.GridlineMesh,
                    onClick = { variants = variants.copy(cellTreatment = TangoCellTreatment.GridlineMesh) },
                    label = { Text("A · mesh") },
                )
                FilterChip(
                    selected = variants.cellTreatment == TangoCellTreatment.RoundedTiles,
                    onClick = { variants = variants.copy(cellTreatment = TangoCellTreatment.RoundedTiles) },
                    label = { Text("B · tiles") },
                )
            }
            TangoBoardRenderer(
                variants = variants,
                onPlacement = { placement ->
                    // Debug board: apply placements to the local copy so both
                    // placement variants are fully playable in the browser surface.
                    board = board.copy(
                        cells = board.cells.toMutableList().also {
                            it[placement.idx] = it[placement.idx].copy(value = placement.value)
                        },
                        rejection = null,
                    )
                },
            ).Content(board, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun VariantRow(label: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { chips() }
    }
}

/** Mid-play state exercising givens, both values, a violation, and all four markers. */
internal fun sampleAbBoard(): TangoRendererModel {
    val suns = setOf(0, 1, 6, 7, 8, 16, 29)
    val moons = setOf(2, 14, 22, 32, 33, 34)
    return TangoRendererModel(
        n = 6,
        cells = (0 until 36).map { idx ->
            TangoRendererModel.CellRender(
                value = when (idx) {
                    in suns -> TangoCell.SUN
                    in moons -> TangoCell.MOON
                    else -> null
                },
                isGiven = idx in setOf(0, 1, 6, 7, 14, 16, 22, 29, 32, 33),
            )
        },
        markers = listOf(
            TangoRendererModel.MarkerRender(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
            TangoRendererModel.MarkerRender(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            TangoRendererModel.MarkerRender(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            TangoRendererModel.MarkerRender(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        ),
        rejection = TangoRendererModel.RejectedMove(8, com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict.Reason.ThreeInARow),
        status = TangoRendererModel.Status.PLAYING,
    )
}

// Showkase entries: light + dark both reachable from the debug browser (§8).
@ShowkaseComposable(name = "Tango A/B switcher — light", group = "Tango")
@Preview(showBackground = true)
@Composable
fun TangoAbSwitcherLight() = TangoAbSwitcher(darkTheme = false)

@ShowkaseComposable(name = "Tango A/B switcher — dark", group = "Tango")
@Preview(showBackground = true)
@Composable
fun TangoAbSwitcherDark() = TangoAbSwitcher(darkTheme = true)
