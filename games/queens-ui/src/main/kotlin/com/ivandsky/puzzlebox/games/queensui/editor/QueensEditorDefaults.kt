package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlin.math.PI

/**
 * Editor-local sizes and motion for the Queens select-then-colour ring (prototype 03 b).
 * No colours here: every hue comes from the theme or [com.ivandsky.puzzlebox.games.queensui.QueensRegionPalette].
 */
object QueensEditorDefaults {
    /**
     * The ghost crown's dashed outline over the diff ring's tint (40): fainter than a placed
     * crown, yet ≥ 3:1 over every region fill (the play ghost's 0.7 is 2.7:1 on light fills).
     */
    const val ghostOutlineAlpha: Float = 0.85f

    /** An auto × under a ghost crown steps back, so the ghost reads first and the two never tangle. */
    const val ghostedXAlpha: Float = 0.3f

    /** Accent wash over a selected cell. */
    const val selectionOverlayAlpha: Float = 0.28f

    /** Accent outline around the selection's outer edge. */
    val selectionStrokeWeight: Dp = 3.dp

    /** One colour circle on the ring (a full touch target). */
    val ringPetalSize: Dp = 48.dp

    /** The ring never gets tighter than this, whatever the board size. */
    val ringMinRadius: Dp = 80.dp

    /** Arc length one colour circle takes on the ring. */
    val ringPetalPitch: Dp = 50.dp

    /** Slack added to the pitch-derived radius. */
    val ringRadiusPad: Dp = 10.dp

    /** Surface disc behind the ring, so circles read over any region fill. */
    const val ringDiscAlpha: Float = 0.88f

    /** Disc margin around the outermost circles. */
    val ringDiscPad: Dp = 8.dp

    /** Shadow under the disc. */
    val ringDiscElevation: Dp = 6.dp

    /** Outline of the centre ✕ (Unpaint) button. */
    val ringCentreStroke: Dp = 1.dp

    /** How far the "next" tag hangs below its circle's bottom edge. */
    val ringNextDrop: Dp = 8.dp

    /** Dashed outline of an unused colour: stroke, dash and gap. */
    val ringUnusedStroke: Dp = 1.5.dp
    val ringUnusedDash: Dp = 4.dp

    /** Accent ring around the colour the whole selection already has. */
    val ringCurrentStroke: Dp = 3.dp

    /** Ring entry: Scale in from this fraction plus Fade in (never from 0). */
    const val ringEnterScale: Float = 0.9f

    /** Ring exit: Fade out while scaling to this fraction. */
    const val ringExitScale: Float = 0.96f

    /** Ring entry length (ease-out). */
    val ringEnterMillis: Int = MotionTokens.DurationShort

    /** Ring exit length; exit is faster than entry. */
    const val ringExitMillis: Int = 90

    /** Ring radius for [colours] circles: `max(80.dp, 50.dp · N / 2π + 10.dp)`. */
    fun ringRadius(colours: Int): Dp = maxOf(ringMinRadius, ringPetalPitch * colours / (2 * PI).toFloat() + ringRadiusPad)

    /** The whole ring disc's diameter for [colours] circles. */
    fun ringDiameter(colours: Int): Dp = (ringRadius(colours) + ringPetalSize / 2 + ringDiscPad) * 2
}
