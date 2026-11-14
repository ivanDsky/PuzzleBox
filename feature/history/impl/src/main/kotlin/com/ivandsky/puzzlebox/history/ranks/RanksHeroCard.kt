package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.MorphCell
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.ScrubCell
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.board.RollingNumber

/**
 * What the hero card says of one board: the [board]'s name, where the viewer stands on it ([standing]) and the
 * [score] that put them there (null: nothing to say, as with no solve in the window).
 */
internal data class HeroFace(val board: String, val standing: Standing, val score: String?) {
    /** The line under the board's name: the score, or why there is none. */
    fun line(): String = score ?: when (standing) {
        Standing.Pending -> "Loading the board"
        Standing.Beyond -> "Outside the top 100"
        else -> "Not ranked here yet"
    }
}

/**
 * The tablet's placement hero card above the board, one compact line: [face]'s place (big), board name and score. It
 * wears the board's accent: [container] fills it and [ink] colours its text, both read while drawing, so a card
 * between two boards blends their colours without recomposing.
 *
 * At rest a change plays on a clock: a place rolls to the new place through the places between them, the numbers of
 * the score roll ([RollingLine]) and a text that changes frosts over in place ([MorphCell]); reduced motion swaps them.
 * Between two boards the card is given the one it leaves as [was] and how far along it is as [progress] (0 to 1, read
 * only where the card is laid out and drawn): the same rolls and frosts then go as far as [progress] says, so they
 * follow a finger, and the card lands without moving again.
 */
@Composable
internal fun RanksHeroCard(
    face: HeroFace,
    container: () -> Color,
    ink: () -> Color,
    modifier: Modifier = Modifier,
    was: HeroFace? = null,
    progress: () -> Float = { 1f },
) {
    val color = remember(ink) { ColorProducer { ink() } }
    Row(
        modifier
            .fillMaxWidth()
            .testTag("ranks.hero")
            .clip(MaterialTheme.shapes.large)
            .drawBehind { drawRect(container()) }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        HeroStanding(was?.standing, face.standing, progress, color)
        Column(Modifier.weight(1f)) {
            HeroCell(was?.board, face.board, progress) {
                BasicText(it, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RollingLine(
                face.line(),
                MaterialTheme.typography.bodyLarge.tabular(),
                color,
                Modifier.testTag("ranks.hero.score"),
                was?.line(),
                progress,
            )
        }
    }
}

/**
 * Where the viewer stands: [now] at rest, and between two boards [was] on its way to [now], [progress] of the way. A
 * place that becomes another place rolls through the places between them, the better one from above
 * ([RollingNumber]); a standing that is no place on either side ("100+", loading, none) frosts over.
 */
@Composable
private fun HeroStanding(was: Standing?, now: Standing, progress: () -> Float, color: ColorProducer) {
    if (was != null && was != now && !(was is Standing.Place && now is Standing.Place)) {
        ScrubCell(was, now, progress) { HeroPlace(it, color, spoken = it == now) }
    } else {
        MorphCell(now, identity = { if (it is Standing.Place) Standing.Place::class else it }) { layer ->
            // A layer that is a place is the place of this frame: it rolls there, and is not drawn a frame late.
            val shown = if (layer is Standing.Place && now is Standing.Place) now else layer
            if (shown is Standing.Place) {
                RollingNumber(shown.place, scrub = (was as? Standing.Place)?.let { it.place to progress }) {
                    HeroPlace(Standing.Place(it), color, spoken = it == shown.place)
                }
            } else {
                HeroPlace(shown, color)
            }
        }
    }
}

/** The big place; [spoken] false keeps it from a screen reader and a test (one that is leaving or rolling past). */
@Composable
private fun HeroPlace(standing: Standing, color: ColorProducer, spoken: Boolean = true) {
    BasicText(
        standing.label(),
        if (spoken) {
            Modifier.testTag("ranks.hero.place").semantics { contentDescription = "Your place: ${standing.describe()}" }
        } else {
            Modifier.clearAndSetSemantics { }
        },
        style = MaterialTheme.typography.displaySmall.tabular().copy(fontWeight = FontWeight.Bold),
        color = color,
        maxLines = 1,
    )
}

/** A line of the card: [now] at rest, frosting over what it was; between two boards, [was] frosted into [now] by [progress]. */
@Composable
private fun HeroCell(was: String?, now: String, progress: () -> Float, content: @Composable (String) -> Unit) {
    if (was == null) MorphCell(now, content = content) else ScrubCell(was, now, progress, content = content)
}

@PuzzleBoxPreviews
@Composable
private fun RanksHeroCardPreview() {
    PuzzleBoxTheme {
        val zip = AccentSlot.Blue.colors()
        val overall = AccentSlot.Neutral.colors()
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            RanksHeroCard(HeroFace("Zip", Standing.Place(3), "1:15 · 1 hint"), { zip.container }, { zip.onContainer })
            RanksHeroCard(HeroFace("Overall", Standing.Place(12), "1,240 pts"), { overall.container }, { overall.onContainer })
            RanksHeroCard(HeroFace("Tango", Standing.None, null), { overall.container }, { overall.onContainer })
            // Halfway from Zip to Overall: the place mid-roll, the name and the score mid-frost.
            RanksHeroCard(
                HeroFace("Overall", Standing.Place(12), "1,240 pts"),
                { zip.container },
                { zip.onContainer },
                was = HeroFace("Zip", Standing.Place(3), "1:15 · 1 hint"),
                progress = { 0.5f },
            )
        }
    }
}
