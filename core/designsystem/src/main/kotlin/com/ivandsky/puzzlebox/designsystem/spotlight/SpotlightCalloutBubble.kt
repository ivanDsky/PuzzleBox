package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import com.ivandsky.puzzlebox.designsystem.Spacing
import kotlin.math.max

/** Test tag of the callout bubble's body. */
internal const val SpotlightCalloutTag = "SpotlightCallout"

/**
 * The callout of [spotlight]: an inverse-surface bubble as wide as the board less a margin, placed by
 * [calloutPlacement] so it never covers a lit cell, with a pointer at its anchor. It Scales in from
 * CALLOUT_START_SCALE out of the pointer tip as [progress] runs 0 → 1; [progress] is read in the layer only.
 */
@Composable
internal fun SpotlightCalloutBubble(callout: SpotlightCallout, spotlight: Spotlight, lit: Set<Int>, progress: () -> Float) {
    val fill = SpotlightTokens.calloutBubble(MaterialTheme.colorScheme)
    Layout(
        content = {
            CalloutBody(callout.text, callout.action, fill)
            CalloutPointer(fill)
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = Spacing.sm.roundToPx()
        val pointerW = Spacing.md.roundToPx()
        val pointerH = pointerW / 2
        val inset = margin + SpotlightTokens.CalloutRadius.roundToPx() + pointerW / 2
        val body = measurables[0].measure(Constraints.fixedWidth((width - 2 * margin).coerceAtLeast(0)))
        val pointer = measurables[1].measure(Constraints.fixed(pointerW, pointerH))
        val placement = spotlight.calloutPlacement(lit, Size(width.toFloat(), height.toFloat()), (body.height + pointerH).toFloat())
        layout(width, height) {
            if (placement == null) return@layout
            val top = placement.top.toInt()
            val bodyY = if (placement.side == CalloutSide.BELOW) top + pointerH else top
            val tipX = if (inset <= width - inset) placement.anchorX.toInt().coerceIn(inset, width - inset) else width / 2
            val tip = when (placement.side) {
                CalloutSide.ABOVE -> Offset(tipX.toFloat(), (bodyY + body.height + pointerH).toFloat())
                CalloutSide.BELOW -> Offset(tipX.toFloat(), top.toFloat())
                CalloutSide.OVER -> Offset(width / 2f, bodyY + body.height / 2f)
            }
            fun Placeable.placeScaledIn(x: Int, y: Int, flip: Boolean = false) = placeWithLayer(x, y) {
                applyCalloutScale(progress(), tip - Offset(x.toFloat(), y.toFloat()), this@placeScaledIn)
                if (flip) rotationZ = HALF_TURN
            }
            body.placeScaledIn(margin, bodyY)
            // The pointer is drawn tip up, its tip at its top centre: the scale pivot. Below the bubble (ABOVE) it is
            // placed with its tip on the anchor and turned half about that tip, so it hangs from the bubble tip down.
            when (placement.side) {
                CalloutSide.ABOVE -> pointer.placeScaledIn(tipX - pointerW / 2, bodyY + body.height + pointerH, flip = true)
                CalloutSide.BELOW -> pointer.placeScaledIn(tipX - pointerW / 2, top)
                CalloutSide.OVER -> Unit
            }
        }
    }
}

private const val HALF_TURN = 180f

private fun GraphicsLayerScope.applyCalloutScale(progress: Float, pivot: Offset, placeable: Placeable) {
    val scale = scaleIn(SpotlightTokens.CALLOUT_START_SCALE, progress)
    scaleX = scale
    scaleY = scale
    alpha = progress
    transformOrigin = TransformOrigin(
        if (placeable.width > 0) pivot.x / placeable.width else 0.5f,
        if (placeable.height > 0) pivot.y / placeable.height else 0.5f,
    )
}

/** The bubble body; the deduction name (the text up to its first colon) is bold, as in the handoff's copy. */
@Composable
private fun CalloutBody(text: String, action: SpotlightAction?, fill: Color) {
    val colon = text.indexOf(':')
    val styled = buildAnnotatedString {
        if (colon > 0) {
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(text, 0, colon + 1) }
            append(text, colon + 1, text.length)
        } else {
            append(text)
        }
    }
    Box(
        Modifier
            .testTag(SpotlightCalloutTag)
            .background(fill, RoundedCornerShape(SpotlightTokens.CalloutRadius))
            .padding(horizontal = Spacing.md)
            // With an action the text's own padding and the pill's 48 dp height set the bubble: padding is not added twice.
            .then(if (action == null) Modifier.padding(vertical = Spacing.sm) else Modifier),
    ) {
        if (action == null) CalloutText(styled) else CalloutTextWithAction(styled, action)
    }
}

@Composable
private fun CalloutText(styled: AnnotatedString, modifier: Modifier = Modifier) {
    Text(
        text = styled,
        modifier = modifier,
        color = SpotlightTokens.calloutText(MaterialTheme.colorScheme),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = SpotlightTokens.CalloutTextSize,
            lineHeight = SpotlightTokens.CalloutLineHeight,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

/**
 * The text with [action]'s pill at the trailing edge, both centred on the row; the text wraps beside the pill. When the
 * text would keep less than ActionMinTextWidth (a large font on a narrow board) the pill drops under it, still trailing.
 */
@Composable
private fun CalloutTextWithAction(styled: AnnotatedString, action: SpotlightAction) {
    Layout(
        content = {
            CalloutText(styled, Modifier.padding(vertical = Spacing.sm))
            SpotlightActionPill(action)
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = Spacing.sm.roundToPx()
        val pill = measurables[1].measure(Constraints(maxWidth = width))
        val inline = width - pill.width - gap >= SpotlightTokens.ActionMinTextWidth.roundToPx()
        val text = measurables[0].measure(Constraints(maxWidth = if (inline) width - pill.width - gap else width))
        if (inline) {
            val height = max(text.height, pill.height)
            layout(width, height) {
                text.place(0, (height - text.height) / 2)
                pill.place(width - pill.width, (height - pill.height) / 2)
            }
        } else {
            layout(width, text.height + pill.height) {
                text.place(0, 0)
                pill.place(width - pill.width, text.height)
            }
        }
    }
}

/** The pointer triangle, tip up at its top centre; the layout turns it to point down. */
@Composable
private fun CalloutPointer(fill: Color) {
    Spacer(
        Modifier.drawWithCache {
            val path = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            onDrawBehind { drawPath(path, fill) }
        },
    )
}
