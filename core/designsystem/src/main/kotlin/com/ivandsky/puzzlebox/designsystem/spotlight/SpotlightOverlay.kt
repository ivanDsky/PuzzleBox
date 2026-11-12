package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.ivandsky.puzzlebox.designsystem.BoardTokens
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * Draws [spotlight] over the board this overlay is mounted on (spec A12–A16, the Quiet Spotlight of prototype 07):
 * unlisted cells dim, a board-ink hairline traces the lit area (none on a culprit step), excluded cells get the
 * game's × at half alpha, the target its glyph as a ghost, a culprit its glyph in the error colour plus a minus
 * badge, and the callout sits beside the anchor without covering a lit cell. Glyph shapes come from
 * [Spotlight.glyphs]; this composable never sees a hint or a game.
 *
 * Its bounds are the cell grid's bounds (it matches the parent's size and adds no padding): mount it inside any
 * frame padding. A new [Spotlight.playId] runs again the ≤400 ms sequence; null clears everything in the same frame.
 */
@Composable
fun BoxScope.SpotlightOverlay(spotlight: Spotlight?, modifier: Modifier = Modifier) {
    if (spotlight == null) return
    val lit = remember(spotlight) { spotlight.litCells() }
    if (lit.isEmpty()) return
    val sequence = remember(spotlight) { SpotlightSequence(spotlight, lit) }
    val clock = rememberSequenceClock(spotlight.playId, sequence.endMs)
    val scheme = MaterialTheme.colorScheme
    val surface = LocalBoardColors.current.boardSurface
    Box(modifier.matchParentSize()) {
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val dim = dimPath(spotlight, lit, size)
                    val hairline = hairlinePath(spotlight.hairlineEdges(lit), spotlight.rows, spotlight.cols, size)
                    val hairlineStroke = Stroke(width = SpotlightTokens.HairlineWidth.toPx(), cap = StrokeCap.Square)
                    val excluded = RoleCells(spotlight, lit, SpotlightRole.EXCLUDED, size)
                    val targets = RoleCells(spotlight, lit, SpotlightRole.TARGET, size)
                    val culprits = RoleCells(spotlight, lit, SpotlightRole.CULPRIT, size)
                    val dimColor = SpotlightTokens.dimOverlay(surface)
                    val hairlineColor = SpotlightTokens.hairline(scheme)
                    val excludedColor = SpotlightTokens.excluded(scheme)
                    val ghostColor = SpotlightTokens.targetGhost(scheme)
                    val culpritColor = SpotlightTokens.culprit(scheme)
                    val badgeBar = SpotlightTokens.culpritBadgeBar(scheme)
                    onDrawBehind {
                        val ms = clock.value
                        val fade = sequence.fade(ms)
                        drawPath(dim, dimColor, alpha = fade)
                        drawHairline(hairline, hairlineColor.copy(alpha = fade), hairlineStroke)
                        for (i in excluded.cells.indices) {
                            val p = sequence.excluded(i, ms)
                            val scale = scaleIn(SpotlightTokens.EXCLUDED_START_SCALE, p)
                            drawSpotlightGlyph(spotlight.glyphs, excluded.cells[i], SpotlightRole.EXCLUDED, excluded.rects[i], scale, excludedColor.faded(p))
                        }
                        val pulse = sequence.pulseScale(ms)
                        val pulseAlpha = sequence.pulseAlpha(ms)
                        drawCells(spotlight.glyphs, targets, SpotlightRole.TARGET, pulse, ghostColor.faded(pulseAlpha))
                        drawCells(spotlight.glyphs, culprits, SpotlightRole.CULPRIT, pulse, culpritColor.faded(pulseAlpha))
                        for (rect in culprits.rects) {
                            drawCulpritBadge(rect, scale = pulse, alpha = pulseAlpha, disc = culpritColor, bar = badgeBar, halo = surface)
                        }
                    }
                },
        )
        spotlight.callout?.let { callout ->
            SpotlightCalloutBubble(callout, spotlight, lit) { sequence.callout(clock.value) }
        }
    }
}

/** One role's on-board cells (ascending) and their rects, built once per size so the draw pass allocates nothing. */
private class RoleCells(spotlight: Spotlight, lit: Set<Int>, role: SpotlightRole, size: Size) {
    val cells: IntArray = lit.filter { spotlight.roles[it] == role }.sorted().toIntArray()
    val rects: Array<Rect> = Array(cells.size) { cellRect(cells[it], spotlight.rows, spotlight.cols, size) }
}

private fun DrawScope.drawCells(glyphs: SpotlightGlyphs, cells: RoleCells, role: SpotlightRole, scale: Float, color: Color) {
    for (i in cells.cells.indices) drawSpotlightGlyph(glyphs, cells.cells[i], role, cells.rects[i], scale, color)
}

private fun Color.faded(alpha: Float): Color = copy(alpha = this.alpha * alpha)

// Preview — Studio tooling and the Catalog's Showkase browser. The four states on a 5×5 board with a stand-in glyph
// (a disc, an ×) at their final frame, as previews run with no motion: deduction, elimination, culprit, reveal.
@ShowkaseComposable(name = "SpotlightOverlay", group = "PuzzleBoard")
@PuzzleBoxPreviews
@Composable
internal fun SpotlightOverlayPreview() {
    val row = setOf(11, 12, 13)
    val excluded = listOf(2, 7, 17, 22).associateWith { SpotlightRole.EXCLUDED }
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PreviewBoard { SpotlightOverlay(previewSpotlight(row.associateWith { SpotlightRole.EVIDENCE } + excluded + (12 to SpotlightRole.TARGET), "Last cell: one open cell left.", row)) }
                PreviewBoard { SpotlightOverlay(previewSpotlight(row.associateWith { SpotlightRole.EVIDENCE } + excluded, "Pointing: no 4 in these cells.", row)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PreviewBoard { SpotlightOverlay(previewSpotlight(mapOf(12 to SpotlightRole.CULPRIT), "Wrong queen: hint again removes it.", setOf(12))) }
                PreviewBoard { SpotlightOverlay(previewSpotlight(mapOf(12 to SpotlightRole.TARGET), "Reveal: this cell is a moon.", setOf(12))) }
            }
        }
    }
}

private fun previewSpotlight(roles: Map<Int, SpotlightRole>, text: String, anchor: Set<Int>) =
    Spotlight(PREVIEW_SIDE, PREVIEW_SIDE, roles, SpotlightCallout(text, anchor), PreviewGlyphs, playId = 0)

/** A blank board under [overlay]: surface and grid lines. */
@Composable
private fun PreviewBoard(overlay: @Composable BoxScope.() -> Unit) {
    val board = LocalBoardColors.current
    Box(Modifier.size(160.dp)) {
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                drawRect(board.boardSurface)
                val cell = size.width / PREVIEW_SIDE
                for (i in 0..PREVIEW_SIDE) {
                    drawLine(board.boardGridLine, Offset(i * cell, 0f), Offset(i * cell, size.height), BoardTokens.boardGridLineWeight.toPx())
                    drawLine(board.boardGridLine, Offset(0f, i * cell), Offset(size.width, i * cell), BoardTokens.boardGridLineWeight.toPx())
                }
            },
        )
        overlay()
    }
}

private const val PREVIEW_SIDE = 5

/** Stand-in game glyph: a disc for a piece, an × for an excluded cell. */
private data object PreviewGlyphs : SpotlightGlyphs {
    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.3f
        if (role == SpotlightRole.EXCLUDED) {
            drawLine(color, c - Offset(r / 2, r / 2), c + Offset(r / 2, r / 2), r / 4)
            drawLine(color, c + Offset(r / 2, -r / 2), c - Offset(r / 2, -r / 2), r / 4)
        } else {
            drawCircle(color, r, c)
        }
    }
}
