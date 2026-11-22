package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

private val RingSize = 112.dp
private const val RingPopMillis = 380
private const val BadgePopMillis = 320

/** The ring, the eyebrow, the title, the badge and the one sentence: how the chapter went, in words. */
@Composable
internal fun ResultHero(result: ChapterResult, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val newly = result.outcome == ChapterOutcome.NewlyMastered
    val (title, badge, sentence) = when (result.outcome) {
        ChapterOutcome.NewlyMastered ->
            Triple("Mastered!", "Newly mastered", "$MasteryRun right without a miss did it. Your best streak was ${result.best}.")
        ChapterOutcome.StillMastered ->
            Triple("Still mastered", "Mastered", "You already had this one. Your best streak was ${result.best}.")
        ChapterOutcome.NotMastered ->
            Triple(
                "Not mastered yet",
                "Streak ${minOf(result.best, MasteryRun)} of $MasteryRun",
                "Your best streak was ${result.best}. $MasteryRun right without a miss masters it.",
            )
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Ring(result, Modifier.pop(RingPopMillis, enabled = newly))
        Text("${result.technique.name} · Chapter done", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold),
            color = scheme.onSurface,
            modifier = Modifier.semantics { heading() }.testTag("train.result.title"),
        )
        Badge(badge, result.mastered, Modifier.pop(BadgePopMillis, enabled = newly).testTag("train.result.badge"))
        Text(sentence, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
        freshRunNote(result)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.testTag("train.result.fresh")) }
    }
}

/** What a missed run adds under the sentence: a miss left too few moves, so another run is the way to master it. Null when nothing was missed or it is mastered. */
internal fun freshRunNote(result: ChapterResult): String? =
    if (result.missedRun && !result.mastered) "A miss left too few moves for $MasteryRun right in a row. Practice again for a fresh run." else null

/** A mastered chapter's star, or the not-mastered "best/5" in a ring that is plainly not a prize. */
@Composable
private fun Ring(result: ChapterResult, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    if (result.mastered) {
        Box(modifier.size(RingSize).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(PuzzleIcons.Star, contentDescription = null, modifier = Modifier.size(56.dp), tint = scheme.onPrimaryContainer)
        }
    } else {
        Box(
            modifier.size(RingSize).clip(CircleShape).background(scheme.surfaceVariant).border(3.dp, scheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${minOf(result.best, MasteryRun)}/$MasteryRun",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Badge(text: String, mastered: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier,
        shape = PillShape,
        color = if (mastered) scheme.primaryContainer else scheme.surfaceVariant,
        contentColor = if (mastered) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mastered) Icon(PuzzleIcons.Star, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** How strongly an action is drawn: filled, outlined, or only its label. */
private enum class Weight { Primary, Outlined, Quiet }

/** One action of the result page: its [label], its test [tag], and whether it ends in a chevron (it goes on). */
private class Action(val label: String, val tag: String, val chevron: Boolean, val onClick: () -> Unit)

/**
 * The page's actions in the order the outcome calls for: the next technique first when this one is mastered, Practice
 * again first when it is not, and no Next at all on the catalog's last technique. The next technique starts only from its
 * button here, never by itself.
 */
@Composable
internal fun ResultActions(result: ChapterResult, onNext: () -> Unit, onAgain: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val next = result.next?.let { Action("Next: ${it.name}", "train.result.next", chevron = true, onNext) }
    val again = Action("Practice again", "train.result.again", chevron = false, onAgain)
    val back = Action("Back to techniques", "train.result.back", chevron = false, onBack)
    val order = when {
        next != null && result.mastered -> listOf(next to Weight.Primary, back to Weight.Outlined, again to Weight.Quiet)
        next != null -> listOf(again to Weight.Primary, next to Weight.Outlined, back to Weight.Quiet)
        result.mastered -> listOf(back to Weight.Primary, again to Weight.Outlined)
        else -> listOf(again to Weight.Primary, back to Weight.Outlined)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        order.forEach { (action, weight) -> ResultButton(action, weight) }
    }
}

/** A 52 dp pill, full width; a long label wraps to a second line and the chevron stays on the right. */
@Composable
private fun ResultButton(action: Action, weight: Weight) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(action.tag)
    val padding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
    val content: @Composable RowScope.() -> Unit = {
        Text(
            action.label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (action.chevron) Icon(PuzzleIcons.Chevron, contentDescription = null, modifier = Modifier.padding(start = Spacing.sm))
    }
    when (weight) {
        Weight.Primary -> Button(action.onClick, modifier, shape = PillShape, contentPadding = padding, content = content)
        Weight.Outlined -> OutlinedButton(
            action.onClick,
            modifier,
            shape = PillShape,
            contentPadding = padding,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            content = content,
        )
        Weight.Quiet -> TextButton(
            action.onClick,
            modifier,
            shape = PillShape,
            contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(contentColor = LocalPrimaryText.current),
            content = content,
        )
    }
}

/** What the catalog's last technique adds under the actions; null for any other, which has a next technique. */
internal fun lastTechniqueNote(result: ChapterResult): String? = when {
    result.next != null -> null
    !result.mastered -> "This is the last technique. Nothing comes after it."
    result.allMastered -> "That was the last technique. All ${result.techniques} are mastered."
    else -> "That was the last technique."
}

/** The note on the catalog's last technique, in the tertiary container. */
@Composable
internal fun LastTechniqueNote(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth().testTag("train.result.note"), shape = RoundedCornerShape(12.dp), color = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(PuzzleIcons.Info, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}
