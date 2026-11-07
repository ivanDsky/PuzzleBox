package com.ivandsky.puzzlebox.create.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * A verdict at a glance, shared by the draft row's [VerdictChip] and the Studio's pill: both
 * read their state, label and tone from here so a blocked (needs-guessing) Unique can never
 * look publishable in one of them.
 */
internal enum class VerdictTone { Ok, Warn, Error, Neutral }

/** The verdict's machine name ("Unique", "Checking", …): the glance's state description. */
internal fun Verdict.stateName(): String = when (this) {
    is Verdict.Incomplete -> "Incomplete"
    Verdict.Checking -> "Checking"
    is Verdict.Unique -> "Unique"
    is Verdict.Multiple -> "Multiple"
    is Verdict.None -> "None"
    Verdict.Undetermined -> "Undetermined"
}

/** The grade's machine name on a Unique: its level ("Medium"), "NeedsGuessing", or null for a game without a ladder. */
internal fun Verdict.Unique.gradeState(): String? = when {
    needsGuessing -> "NeedsGuessing"
    else -> difficulty?.level?.name
}

/** The grade as the author reads it: "Medium", "needs guessing", or null. */
internal fun Verdict.Unique.gradeLabel(): String? = when {
    needsGuessing -> "needs guessing"
    else -> difficulty?.level?.name
}

/** What a glance crossfades on: the verdict's kind and grade, not a new solution. */
internal fun Verdict.glanceKey(): String = stateName() + (this as? Verdict.Unique)?.gradeState().orEmpty()

internal fun Verdict.glanceLabel(): String = when (this) {
    is Verdict.Incomplete -> "Not checked"
    Verdict.Checking -> "Checking…"
    is Verdict.Unique -> gradeLabel()?.let { "Unique · $it" } ?: "Unique"
    is Verdict.Multiple -> "2+ solutions"
    is Verdict.None -> "No solution"
    Verdict.Undetermined -> "Couldn't decide"
}

internal fun Verdict.tone(): VerdictTone = when (this) {
    is Verdict.Unique -> if (needsGuessing) VerdictTone.Warn else VerdictTone.Ok
    is Verdict.Multiple -> VerdictTone.Warn
    is Verdict.None -> VerdictTone.Error
    is Verdict.Incomplete, Verdict.Checking, Verdict.Undetermined -> VerdictTone.Neutral
}

/**
 * The tone's container and content colours. [neutralContent] differs by host: the Studio's
 * handoff reads neutral text in onSurface, the draft row's in onSurfaceVariant.
 */
@Composable
internal fun VerdictTone.colors(neutralContent: Color = MaterialTheme.colorScheme.onSurface): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        VerdictTone.Ok -> scheme.primaryContainer to scheme.onPrimaryContainer
        VerdictTone.Warn -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        VerdictTone.Error -> scheme.errorContainer to scheme.onErrorContainer
        VerdictTone.Neutral -> scheme.surfaceContainerHighest to neutralContent
    }
}
