package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import java.time.format.TextStyle
import java.util.Locale

/**
 * The cover the story opens on: an eyebrow, the period as a kinetic [title], a fan of game letters, the [hook], who the
 * recap is for ([name], left out when null) and the cue to tap. It draws in the cover palette's ink on the deck's plate.
 */
@Composable
internal fun RecapCover(title: String, hook: String, name: String?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("recap.cover")) {
        val wide = maxWidth >= WideWidth
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (wide) Spacing.xl else Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                "PuzzleBox Insights".uppercase(),
                modifier = Modifier.clearAndSetSemantics { text = AnnotatedString("PuzzleBox Insights") },
                style = MaterialTheme.typography.labelMedium,
                fontSize = EyebrowSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = EyebrowTracking,
            )
            RecapKineticTitle(title, fontSize = if (wide) WideTitleSize else TitleSize, modifier = Modifier.padding(top = Spacing.xs))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { RecapCoverFan() }
            Text(hook, style = MaterialTheme.typography.titleMedium, fontSize = HookSize, fontWeight = FontWeight.Bold)
            if (name != null) Text("Recap for $name", style = MaterialTheme.typography.bodyLarge)
            Text("Tap to begin ›", style = MaterialTheme.typography.labelLarge, fontSize = CueSize)
        }
    }
}

/** The cover's title: "Your October" for a month, the year's own [title] ("2026 so far", "2025") for a year. */
internal fun coverTitle(period: RecapPeriod, title: String, locale: Locale = Locale.getDefault()): String = when (period) {
    is RecapPeriod.Month -> "Your ${period.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)}"
    is RecapPeriod.Year -> title
}

/**
 * The cover's hook: how many games the period played, spelled out up to ten, and how long it ran. A period with one
 * solve says so instead, and one with no Solved card has no game count to give.
 */
internal fun coverHook(period: RecapPeriod, solved: RecapCard.Solved?): String {
    val span = if (period is RecapPeriod.Month) "One month." else "One year."
    return when {
        solved == null -> "$span Let's look back."
        solved.total == 1 -> "One puzzle so far. Every streak starts with one."
        else -> "${solved.perGame.size.spelled()} ${if (solved.perGame.size == 1) "game" else "games"}. $span Let's look back."
    }
}

private val Spelled = listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

private fun Int.spelled(): String = Spelled.getOrNull(this - 1) ?: toString()

private val WideWidth = 600.dp
private val TitleSize = 48.sp
private val WideTitleSize = 88.sp
private val EyebrowSize = 14.sp
private val EyebrowTracking = 2.sp
private val HookSize = 22.sp
private val CueSize = 20.sp
