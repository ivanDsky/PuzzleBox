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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.MorphCell
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.ScrubCell
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.tabular
import kotlin.math.roundToInt

// A place past the rows the board reads ranks after every place on them.
private const val BEYOND_RANK = 101

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
 * At rest a text that changes frosts over in place ([MorphCell]; reduced motion swaps it). Between two boards the card
 * is given the one it leaves as [was] and how far along it is as [progress] (0 to 1, read only where the card is
 * laid out and drawn): the place rolls from the old one to the new, the better of the two coming from above, and the
 * name and the score frost from one to the other ([ScrubCell]), all as far as [progress] says, so they follow a finger.
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
        if (was == null) {
            MorphCell(face.standing, identity = { it.label() }) { HeroPlace(it, color) }
        } else {
            RollingPlace(was.standing, face.standing, progress, color)
        }
        Column(Modifier.weight(1f)) {
            HeroCell(was?.board, face.board, progress) {
                BasicText(it, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HeroCell(was?.line(), face.line(), progress) {
                BasicText(it, Modifier.testTag("ranks.hero.score"), style = MaterialTheme.typography.bodyLarge.tabular(), color = color)
            }
        }
    }
}

/** The big place; [spoken] false keeps it from a screen reader and a test (the one rolling out). */
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

/**
 * The place on its way from [from] to [to], [progress] of the way: one rolls out of the cell as the other rolls in,
 * the better place from above, and the cell is as wide as the two are between them. Only [to] is read out.
 */
@Composable
private fun RollingPlace(from: Standing, to: Standing, progress: () -> Float, color: ColorProducer) {
    if (from.label() == to.label()) {
        HeroPlace(to, color)
        return
    }
    val down = if (to.rank() < from.rank()) 1 else -1
    Layout(
        content = {
            HeroPlace(from, color, spoken = false)
            HeroPlace(to, color)
        },
        modifier = Modifier.clipToBounds(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leaving = measurables[0].measure(loose)
        val arriving = measurables[1].measure(loose)
        val at = progress().coerceIn(0f, 1f)
        val height = maxOf(leaving.height, arriving.height)
        layout(lerp(leaving.width, arriving.width, at), height) {
            leaving.place(0, (down * at * height).roundToInt())
            arriving.place(0, (-down * (1f - at) * height).roundToInt())
        }
    }
}

private fun Standing.rank(): Int = when (this) {
    is Standing.Place -> place
    Standing.Beyond -> BEYOND_RANK
    else -> Int.MAX_VALUE
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
            // Halfway from Zip to Overall.
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
