package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.EdgeOverlay
import com.ivandsky.puzzlebox.gameui.EdgeSide
import com.ivandsky.puzzlebox.gameui.GameBoardRenderer
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * Board anatomy fractions (design-direction §1/§2; all × cell pitch so n6→n8 and
 * phone→tablet scale without new values). Ticket 04 owns the final tuning; the values
 * below are the skeleton's baseline.
 */
object TangoBoardSpec {
    /** Sun/moon glyph box, fraction of cell pitch (0.66 at n>=8 is ticket-04 D5 parity). */
    const val glyphSizeFraction: Float = 0.60f

    /** `=`/`×` mark size, fraction of cell pitch (n<=7 baseline; floor 10.3dp at worst case). */
    const val markSizeFraction: Float = 0.24f

    /** Mark stroke thickness, fraction of the mark box. */
    const val markStrokeFraction: Float = 0.14f

    /** Sun disc diameter, fraction of the glyph box. */
    const val sunDiscFraction: Float = 0.64f

    /** Sun ray length/width, fractions of the glyph box. */
    const val sunRayLengthFraction: Float = 0.18f
    const val sunRayWidthFraction: Float = 0.08f

    /** Moon punch offset / diameter, fractions of the glyph box (crescent cut). */
    const val moonPunchOffsetFraction: Float = 0.28f
    const val moonPunchDiameterFraction: Float = 0.80f
}

/**
 * Maps [TangoRendererModel] onto the shared game-ui [BoardGrid] (F2 §7): sun/moon glyphs
 * drawn as vectors in cell content, `=`/`×` constraints as non-interactive
 * [EdgeOverlay.Mark] decorations (A12), violations tinted with the error container.
 * Pure render — this class paints the model it is given, nothing more.
 */
class TangoBoardRenderer : GameBoardRenderer {

    @Composable
    override fun Content(model: RendererModel, modifier: Modifier) {
        val tango = model as? TangoRendererModel
            ?: error("TangoBoardRenderer expects TangoRendererModel, got ${model::class.simpleName}")
        BoardGrid(
            rows = tango.n,
            cols = tango.n,
            modifier = modifier.aspectRatio(1f),
            edgeOverlays = tango.markers.map { marker ->
                EdgeOverlay.Mark(
                    cell = GridCell(
                        row = marker.cellIdx / tango.n,
                        col = marker.cellIdx % tango.n,
                    ),
                    side = when (marker.side) {
                        TangoEdgeSide.RIGHT -> EdgeSide.RIGHT
                        TangoEdgeSide.DOWN -> EdgeSide.DOWN
                    },
                    symbol = when (marker.kind) {
                        TangoMarkerKind.SAME -> "="
                        TangoMarkerKind.OPPOSITE -> "×"
                    },
                )
            },
            cellContent = { row, col -> Cell(tango, row * tango.n + col) },
        )
    }
}

@Composable
private fun Cell(model: TangoRendererModel, idx: Int) {
    val render = model.cells.getOrNull(idx) ?: return
    val rejected = model.rejection?.idx == idx
    val container = if (rejected) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        when (render.value) {
            TangoCell.SUN -> SunGlyph(Modifier.fillMaxSize())
            TangoCell.MOON -> MoonGlyph(Modifier.fillMaxSize())
            null -> Unit
        }
    }
}

@Composable
private fun SunGlyph(modifier: Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val glyph = size.minDimension * TangoBoardSpec.glyphSizeFraction
        val center = Offset(size.width / 2f, size.height / 2f)
        val discRadius = glyph * TangoBoardSpec.sunDiscFraction / 2f
        val rayLength = glyph * TangoBoardSpec.sunRayLengthFraction
        val rayWidth = glyph * TangoBoardSpec.sunRayWidthFraction
        drawCircle(fill, radius = discRadius, center = center)
        // 8 rays at 45-degree steps
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45.0))
            val from = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * discRadius * 1.25f
            val to = from + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * rayLength
            drawLine(fill, from, to, strokeWidth = rayWidth, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun MoonGlyph(modifier: Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val glyph = size.minDimension * TangoBoardSpec.glyphSizeFraction
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = glyph * TangoBoardSpec.sunDiscFraction / 2f
        drawCircle(fill, radius = radius, center = center)
        // Crescent: punch an offset disc out of the filled one (offscreen layer upstream).
        val punch = glyph * TangoBoardSpec.moonPunchDiameterFraction / 2f
        val offset = glyph * TangoBoardSpec.moonPunchOffsetFraction
        drawCircle(
            Color.Black,
            radius = punch,
            center = center - Offset(offset, offset),
            blendMode = BlendMode.DstOut,
        )
    }
}
