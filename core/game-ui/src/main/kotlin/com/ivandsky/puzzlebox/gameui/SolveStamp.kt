package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * The game's glyph stamped onto the board during the solve moment: [brand]'s `GameGlyph(Hero)` lands on the `Stamp`
 * spring [CelebrationMotion.BoardPopStartMs] after [rest] begins (the solve), as the sweep band leaves and the board
 * pops, and goes when the rest ends. Place it in the board's own box, inside the solve frame, so it centres on the
 * board and pops with it, and before the [SolveRestTapLayer], so a tap on it still skips. It is decorative: the glyph
 * has no description and the stamp takes no input. Reduced motion shows none.
 */
@Composable
fun SolveStamp(brand: GameBrand, rest: SolveRestState, modifier: Modifier = Modifier) {
    if (rememberReducedMotion()) return
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(rest.resting) {
        if (rest.resting) {
            delay(CelebrationMotion.BoardPopStartMs.toLong())
            landed = true
        } else {
            landed = false
        }
    }
    Stamp(visible = landed, modifier = modifier.testTag("play.solve_stamp")) {
        GameGlyph(brand, GameGlyphSize.Hero)
    }
}

// Preview: no rest is running, so no stamp has landed and the pane stays static (A8).
@PuzzleBoxPreviews
@Composable
internal fun SolveStampPreview() {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md)) {
            SolveStamp(GameBrand.Neutral, rememberSolveRest())
        }
    }
}
