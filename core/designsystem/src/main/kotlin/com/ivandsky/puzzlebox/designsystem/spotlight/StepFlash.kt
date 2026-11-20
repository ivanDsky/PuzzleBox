package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalInspectionMode
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/** How long the flash takes to fade out. */
private const val FlashMs = 600

/** The tint's alpha at the start of the flash. */
private const val FlashAlpha = 0.35f

/**
 * Briefly tints the lit cells of [spotlight] in the primary color and fades out, with no callout, no dim and no hold on
 * touches: the board under it stays playable throughout. A Right move's acknowledgement on a practice board. Its bounds
 * are the cell grid's bounds, as with [SpotlightOverlay]. A new [spotlight] value flashes again; reduced motion skips
 * the flash, and previews hold its first frame.
 */
@Composable
fun BoxScope.StepFlash(spotlight: Spotlight?, modifier: Modifier = Modifier) {
    if (spotlight == null) return
    val lit = remember(spotlight) { spotlight.litCells() }
    if (lit.isEmpty()) return
    val reduced = rememberReducedMotion()
    val still = LocalInspectionMode.current
    val fade = remember(spotlight) { Animatable(if (reduced) 0f else 1f) }
    LaunchedEffect(fade, still) { if (!still) fade.animateTo(0f, tween(FlashMs, easing = LinearEasing)) }
    val tint = MaterialTheme.colorScheme.primary
    Spacer(
        modifier.matchParentSize().drawBehind {
            val alpha = FlashAlpha * fade.value
            if (alpha > 0f) {
                for (cell in lit) {
                    val rect = cellRect(cell, spotlight.rows, spotlight.cols, size)
                    drawRect(tint.copy(alpha = alpha), rect.topLeft, rect.size)
                }
            }
        },
    )
}

// Preview — Studio tooling and the Catalog's Showkase browser: a finished three-square step at the flash's first frame.
@ShowkaseComposable(name = "StepFlash", group = "PuzzleBoard")
@PuzzleBoxPreviews
@Composable
internal fun StepFlashPreview() {
    val row = setOf(11, 12, 13)
    PuzzleBoxTheme {
        Box(Modifier.padding(Spacing.md)) {
            PreviewBoard { StepFlash(previewSpotlight(row.associateWith { SpotlightRole.TARGET }, "", row)) }
        }
    }
}
