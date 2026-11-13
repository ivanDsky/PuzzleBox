package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The scoring rule in one sentence (A10). */
internal const val RANKS_RULE =
    "Each board scores you by place, 100 points for first and fewer the more players are ahead of you, and Overall adds those scores up."

/** The lead word of each line, then what it says: how a board orders, what Overall sums, and the unofficial and 100+ cases (A9, A11, A14, A36). */
private val Rules = listOf(
    "Per game" to "fastest time wins and fewer hints break a tie. Strands ranks hints first.",
    "Overall" to "your scores from every game's board in the window. Players with the same total share a place.",
    "Week and Month" to "each game ranks the average of the days you played.",
    "Top 100" to "past that you see 100+ with no exact place, and your Overall total shows as at least (≥).",
    "Unofficial" to "a solve posted after its day's submit window closed stays on this phone. Only you see it, marked. It earns no points and takes no one's place.",
)

/** "How ranks work": the rule and what it means for ties, windows, the cap and unofficial solves. [onDismiss] closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HowRanksWorkSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier.testTag("ranks.how.sheet")) {
        HowRanksWork()
    }
}

@Composable
private fun HowRanksWork() {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("How ranks work", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(RANKS_RULE, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Rules.forEach { (lead, rest) ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(lead) }
                    append(": ")
                    append(rest)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun HowRanksWorkPreview() {
    PuzzleBoxTheme {
        HowRanksWork()
    }
}
