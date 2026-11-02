package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.BoardTokens
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.EdgeOverlay
import com.ivandsky.puzzlebox.gameui.EdgeSide
import com.ivandsky.puzzlebox.gameui.GameBoardRenderer
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.rememberReducedMotion
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import kotlin.math.cos
import kotlin.math.sin


/**
 * Maps [TangoRendererModel] onto the shared game-ui [BoardGrid] (design-direction
 * §2–§7): sun/moon glyphs as vectors in cell content (silhouette identity, §3 fills),
 * `=`/`×` constraints as non-interactive [EdgeOverlay.Mark] decorations (A12),
 * violations as tint + dashed border + rule-naming caption (§6 copy), per-cell
 * TalkBack semantics (§9), both A11 placement variants behind [TangoAbVariants]
 * (§8 scaffold — verdicts are ticket 06; losers deleted then).
 */
class TangoBoardRenderer(
    private val variants: TangoAbVariants = TangoAbVariants(),
    private val onPlacement: ((TangoPlacement) -> Unit)? = null,
) : GameBoardRenderer {

    @Composable
    override fun Content(model: RendererModel, modifier: Modifier) {
        val tango = model as? TangoRendererModel
            ?: error("TangoBoardRenderer expects TangoRendererModel, got ${model::class.simpleName}")
        val tapPolicy = remember(variants.placement) { TangoTapPolicy(variants.placement) }
        val commit = onPlacement

        Column(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val pitch = maxWidth / tango.n
                        BoardGrid(
                            rows = tango.n,
                            cols = tango.n,
                            modifier = Modifier.aspectRatio(1f),
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
                            onCellTap = commit?.let { onCommit ->
                                { row: Int, col: Int ->
                                    tapPolicy.onTap(tango, row * tango.n + col).forEach(onCommit)
                                }
                            },
                            cellContent = { row, col ->
                                Cell(
                                    model = tango,
                                    variants = variants,
                                    tapPolicy = tapPolicy,
                                    onPlacement = commit,
                                    idx = row * tango.n + col,
                                    row = row,
                                    col = col,
                                )
                            },
                        )
                        Chooser(
                            variants = variants,
                            tapPolicy = tapPolicy,
                            onPlacement = commit,
                            pitch = pitch,
                        )
                    }
                    if (variants.countIndicators == TangoCountIndicators.On) {
                        ColumnCountRail(tango)
                    }
                }
                if (variants.countIndicators == TangoCountIndicators.On) {
                    RowCountRail(tango)
                }
            }
            CaptionSlot(tango, variants)
        }
    }
}

@Composable
private fun Cell(
    model: TangoRendererModel,
    variants: TangoAbVariants,
    tapPolicy: TangoTapPolicy,
    onPlacement: ((TangoPlacement) -> Unit)?,
    idx: Int,
    row: Int,
    col: Int,
) {
    val render = model.cells.getOrNull(idx)
    val violating = TangoSemantics.isViolating(model, idx)
    val scheme = MaterialTheme.colorScheme
    val boardColors = LocalBoardColors.current
    val gridLineWeight = BoardTokens.boardGridLineWeight
    val reducedMotion = rememberReducedMotion()

    // ViolationFlash (§7): scale 1→0.86 spring on the offending cell; reduced motion
    // snaps instantly — no pulse, tint + border carry the state.
    val scale by animateFloatAsState(
        targetValue = if (violating && !reducedMotion) 0.86f else 1f,
        animationSpec = if (violating && !reducedMotion) {
            ViolationFlashScale
        } else {
            tween(durationMillis = MotionTokens.NavTransitionExit)
        },
        label = "violationFlashScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .then(
                if (variants.cellTreatment == TangoCellTreatment.RoundedTiles) {
                    // Discrete rounded tile (A/B item 6-B): fill only — the gridline
                    // doubles as the gap seam; radius through the spec fraction.
                    Modifier.drawBehind {
                        val radiusPx = maxOf(
                            TangoBoardSpec.tileRadiusMin.toPx(),
                            size.minDimension * TangoBoardSpec.tileRadiusFraction,
                        )
                        drawRoundRect(
                            color = scheme.surfaceContainerLowest,
                            cornerRadius = CornerRadius(radiusPx),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .drawBehind {
                // Reference pitch is irrelevant to shape: fractions read the cell box,
                // dashes read the pitch-equivalent cell size (minDimension = pitch px).
                val pitchPx = size.minDimension
                if (violating) {
                    // errorContainer tint 0.7 (Zip rejection precedent) + dashed border
                    // in colorScheme.error — the shape signal keeps color from being
                    // the sole carrier (§2; tint alone is 1.29:1 on white, by design).
                    drawRoundRect(
                        color = scheme.errorContainer.copy(alpha = TangoBoardSpec.violationTintAlpha),
                        cornerRadius = CornerRadius(pitchPx * 0.04f),
                    )
                    drawRoundRect(
                        color = scheme.error,
                        cornerRadius = CornerRadius(pitchPx * 0.04f),
                        style = Stroke(
                            width = gridLineWeight.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(
                                    pitchPx * TangoBoardSpec.violationDashFraction,
                                    pitchPx * TangoBoardSpec.violationDashGapFraction,
                                ),
                            ),
                        ),
                    )
                }
                // Selection ring (variant B only) — boardSelection role, ring geometry
                // keeps selection from ever being color-only (§2/§3).
                if (variants.placement == TangoPlacementVariant.TwoTapChooser &&
                    tapPolicy.pendingIdx == idx
                ) {
                    drawRoundRect(
                        color = boardColors.boardSelection,
                        cornerRadius = CornerRadius(pitchPx * 0.04f),
                        style = Stroke(width = gridLineWeight.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (render?.value) {
            TangoCell.SUN -> SunGlyph(
                Modifier.fillMaxSize(),
                style = variants.glyphStyle,
                fill = boardColors.sunFill,
                n = model.n,
            )
            TangoCell.MOON -> MoonGlyph(
                Modifier.fillMaxSize(),
                style = variants.glyphStyle,
                fill = boardColors.moonFill,
                n = model.n,
            )
            null -> Unit
        }

        // Per-cell TalkBack semantics (§9): one focusable node per cell, row-major;
        // givens distinguished by words ("given sun"), not visuals (§2).
        val actionLabels = TangoSemantics.customActions
        Box(
            Modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        listOf(TangoSemantics.cellContentDescription(row, col))
                            .plus(TangoSemantics.markerPhrases(model, idx))
                            .joinToString(separator = "; ")
                    stateDescription = TangoSemantics.cellStateDescription(model, idx)
                    customActions = actionLabels.map { label ->
                        CustomAccessibilityAction(label) {
                            if (onPlacement == null) {
                                false
                            } else {
                                tapPolicy.onCustomAction(model, idx, label).forEach(onPlacement)
                                true
                            }
                        }
                    }
                },
        )
    }
}

@Composable
private fun SunGlyph(modifier: Modifier, style: TangoGlyphStyle, fill: Color, n: Int) {
    Canvas(modifier) {
        val glyph = size.minDimension * TangoBoardSpec.glyphSizeFraction(n)
        val center = Offset(size.width / 2f, size.height / 2f)
        val discRadius = glyph * TangoBoardSpec.sunDiscFraction / 2f
        val rayInner = discRadius + glyph * TangoBoardSpec.sunRayGapFraction
        val rayLength = glyph * TangoBoardSpec.sunRayLengthFraction
        val rayWidth = glyph * TangoBoardSpec.sunRayWidthFraction
        if (style == TangoGlyphStyle.Hollow) {
            drawCircle(
                fill,
                radius = discRadius,
                center = center,
                style = Stroke(glyph * TangoBoardSpec.glyphRingStrokeFraction),
            )
        } else {
            drawCircle(fill, radius = discRadius, center = center)
        }
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45.0))
            val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                fill,
                center + dir * rayInner,
                center + dir * (rayInner + rayLength),
                strokeWidth = rayWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun MoonGlyph(modifier: Modifier, style: TangoGlyphStyle, fill: Color, n: Int) {
    Canvas(modifier) {
        val glyph = size.minDimension * TangoBoardSpec.glyphSizeFraction(n)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = glyph * TangoBoardSpec.sunDiscFraction / 2f
        val punch = glyph * TangoBoardSpec.moonPunchDiameterFraction / 2f
        val punchOffset = glyph * TangoBoardSpec.moonPunchOffsetFraction
        val punchCenter = center + Offset(punchOffset, -punchOffset) // toward upper-right; one orientation, never mirrored (§2)
        when (style) {
            TangoGlyphStyle.Solid -> {
                drawCircle(fill, radius = radius, center = center)
                drawCircle(Color.Black, radius = punch, center = punchCenter, blendMode = BlendMode.DstOut)
            }
            TangoGlyphStyle.Hollow -> {
                // Zip dot lineage: ring outline, punched by a hollow ring (§8 item 4-B).
                val ring = glyph * TangoBoardSpec.glyphRingStrokeFraction
                drawCircle(fill, radius = radius, center = center, style = Stroke(ring))
                drawCircle(
                    Color.Black,
                    radius = punch,
                    center = punchCenter,
                    style = Stroke(ring),
                    blendMode = BlendMode.DstOut,
                )
            }
        }
    }
}

/**
 * Chooser popover (variant B, §5): two sun/moon targets anchored above/below the
 * pending cell, scaling from the cell's edge (never the screen center), top-half rows
 * flip below. Exact Popup mechanics are §11-deferred — this is the in-board anchor.
 */
@Composable
private fun BoxScope.Chooser(
    variants: TangoAbVariants,
    tapPolicy: TangoTapPolicy,
    onPlacement: ((TangoPlacement) -> Unit)?,
    pitch: Dp,
) {
    val pending = if (variants.placement == TangoPlacementVariant.TwoTapChooser) {
        tapPolicy.pendingIdx
    } else {
        null
    } ?: return
    val target = TangoBoardSpec.chooserTargetSize(pitch, 6)
    val chooserWidth = target * 2 + Spacing.sm
    val chooserHeight = target + 32.dp // targets + the Cancel row
    val row = pending / 6
    val col = pending % 6
    val below = row < 3 // top half flips below (§5 excludedBounds handling)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(
                x = pitch * (col + 0.5f) - chooserWidth / 2,
                y = if (below) {
                    pitch * (row + 1) + Spacing.sm
                } else {
                    pitch * row - chooserHeight - Spacing.sm
                },
            ),
    ) {
        Column(
            Modifier.padding(Spacing.sm).width(chooserWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ChooserTarget(
                    label = TangoSemantics.chooserSunLabel,
                    targetSize = target,
                    onClick = { onPlacement?.invoke(tapPolicy.chooserChoose(pending, TangoCell.SUN)) },
                ) {
                    SunGlyph(
                        Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
                        style = variants.glyphStyle,
                        fill = LocalBoardColors.current.sunFill,
                        n = 6,
                    )
                }
                ChooserTarget(
                    label = TangoSemantics.chooserMoonLabel,
                    targetSize = target,
                    onClick = { onPlacement?.invoke(tapPolicy.chooserChoose(pending, TangoCell.MOON)) },
                ) {
                    MoonGlyph(
                        Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
                        style = variants.glyphStyle,
                        fill = LocalBoardColors.current.moonFill,
                        n = 6,
                    )
                }
            }
            TextButton(
                onClick = { tapPolicy.dismissChooser() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp),
            ) {
                Text(TangoSemantics.chooserDismissLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ChooserTarget(
    label: String,
    targetSize: Dp,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
) {
    // Hit area = target + min touch padding (§5 policy-as-test, floor §10.7): never
    // smaller than the cell.
    Box(
        Modifier
            .size(targetSize)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small)
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

/**
 * Count rails (§2, A/B item 3): bottom rail (columns) and trailing rail (rows) of
 * `countPipFraction` pips (filled = placed suns). A tango-ui wrapper outside
 * BoardGrid — no BoardGrid change.
 */
@Composable
private fun ColumnCountRail(model: TangoRendererModel) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val pip = TangoBoardSpec.countPipDiameter(40.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs, start = Spacing.xs, end = Spacing.xs)
            .height(pip),
    ) {
        for (col in 0 until model.n) {
            PipGroup(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = TangoSemantics.columnCountDescription(
                        col,
                        TangoSemantics.sunsInColumn(model, col),
                        model.n / 2,
                    )
                },
                placed = TangoSemantics.sunsInColumn(model, col),
                total = model.n / 2,
                pip = pip,
                color = color,
                horizontal = true,
            )
        }
    }
}

@Composable
private fun RowCountRail(model: TangoRendererModel) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val pip = TangoBoardSpec.countPipDiameter(40.dp)
    Column(
        Modifier
            .padding(start = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs)
            .width(pip),
    ) {
        for (row in 0 until model.n) {
            PipGroup(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = TangoSemantics.rowCountDescription(
                        row,
                        TangoSemantics.sunsInRow(model, row),
                        model.n / 2,
                    )
                },
                placed = TangoSemantics.sunsInRow(model, row),
                total = model.n / 2,
                pip = pip,
                color = color,
                horizontal = false,
            )
        }
    }
}

@Composable
private fun PipGroup(
    modifier: Modifier,
    placed: Int,
    total: Int,
    pip: Dp,
    color: Color,
    horizontal: Boolean,
) {
    val content: @Composable () -> Unit = {
        for (i in 0 until total) {
            Box(
                Modifier
                    .padding(1.dp)
                    .size(pip)
                    .then(
                        if (i < placed) {
                            Modifier.background(color, CircleShape)
                        } else {
                            Modifier.drawBehind {
                                drawCircle(
                                    color = color,
                                    radius = size.minDimension / 2f,
                                    style = Stroke(width = 1.dp.toPx()),
                                )
                            }
                        },
                    ),
            )
        }
    }
    if (horizontal) {
        Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    }
}

/**
 * Fixed one-line caption slot (§4, 20 dp): violations announce rule-naming copy
 * (§6, polite liveRegion, once per new violation via the content change), completion
 * announces "Puzzle solved" before the route handoff. Text fades (ViolationFlash
 * exit ≤ entrance); layout never jumps; caption suppressed in the silent A/B variant.
 */
@Composable
private fun CaptionSlot(model: TangoRendererModel, variants: TangoAbVariants) {
    val rejection = model.rejection
    val violatingIdx = rejection?.idx
    val caption = when {
        model.status == TangoRendererModel.Status.COMPLETE -> "Puzzle solved"
        variants.violationStrength == TangoViolationStrength.Captioned && violatingIdx != null ->
            TangoSemantics.violationPhrase(model, violatingIdx)
        else -> null
    }
    val reducedMotion = rememberReducedMotion()
    Box(
        Modifier
            .fillMaxWidth()
            .height(TangoBoardSpec.captionSlotHeight)
            .padding(top = Spacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (reducedMotion) {
            if (caption != null) CaptionText(caption)
        } else {
            AnimatedVisibility(
                visible = caption != null,
                enter = fadeIn(tween(captionFadeMillis, easing = MotionTokens.EasingEmphasizedDecelerate)),
                exit = fadeOut(tween(captionFadeOutMillis, easing = MotionTokens.EasingEmphasizedAccelerate)),
            ) {
                CaptionText(caption.orEmpty())
            }
        }
    }
}

@Composable
private fun CaptionText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .padding(start = Spacing.lg)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
