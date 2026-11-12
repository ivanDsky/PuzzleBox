package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.Timeline

private const val SEPARATOR = " · "

/**
 * The compare's one line on who is ahead at the playback position. Null when [ReplayPlaybackState.lead]
 * is null. [pieces] is the game's piece noun, plural ("queens").
 */
fun leadLineText(
    state: ReplayPlaybackState,
    mineOutcome: ReplayOutcome,
    theirsOutcome: ReplayOutcome,
    mineEndMs: Long,
    theirsEndMs: Long,
    names: CompareNames,
    pieces: String,
): String? {
    val lead = state.lead ?: return null
    val mineFrame = state.mineFrame ?: return null
    val theirsFrame = state.theirsFrame ?: return null
    val mine = mineFrame.facts
    val theirs = theirsFrame.facts
    val mineEnded = mineFrame.ended
    val theirsEnded = theirsFrame.ended
    val mineGaveUp = mineEnded && mineOutcome == ReplayOutcome.GaveUp
    val theirsGaveUp = theirsEnded && theirsOutcome == ReplayOutcome.GaveUp
    val mineGaveUpLine = ReplayCopy.gaveUp(names.mine, formatDuration(mineEndMs))
    val theirsGaveUpLine = ReplayCopy.gaveUp(names.theirs, formatDuration(theirsEndMs))

    if (mineEnded && theirsEnded) {
        if (lead == Lead.Level) return ReplayCopy.LEVEL
        val mineLeads = lead == Lead.Mine
        val leaderName = if (mineLeads) names.mine else names.theirs
        val leaderSolved = if (mineLeads) mineOutcome == ReplayOutcome.Solved else theirsOutcome == ReplayOutcome.Solved
        if (leaderSolved) {
            val otherGaveUp = if (mineLeads) theirsGaveUp else mineGaveUp
            return if (otherGaveUp) {
                ReplayCopy.wonOverGaveUp(leaderName, if (mineLeads) theirsGaveUpLine else mineGaveUpLine)
            } else {
                val leaderMs = if (mineLeads) mineEndMs else theirsEndMs
                val otherMs = if (mineLeads) theirsEndMs else mineEndMs
                ReplayCopy.won(leaderName, formatDuration(leaderMs), formatDuration(otherMs))
            }
        }
        // Nobody solved: the rule separated them by hints or pieces, or only by an earlier end.
        return aheadLine(lead, mine.hints, theirs.hints, mine.correct, theirs.correct, names, pieces)
            ?: if (mineLeads) ReplayCopy.bothGaveUp(mineGaveUpLine, theirsGaveUpLine)
            else ReplayCopy.bothGaveUp(theirsGaveUpLine, mineGaveUpLine)
    }

    if (mineGaveUp) {
        return ReplayCopy.bothGaveUp(mineGaveUpLine, ReplayCopy.playingAt(names.theirs, theirs.correct, pieces))
    }
    if (theirsGaveUp) {
        return ReplayCopy.bothGaveUp(theirsGaveUpLine, ReplayCopy.playingAt(names.mine, mine.correct, pieces))
    }
    return aheadLine(lead, mine.hints, theirs.hints, mine.correct, theirs.correct, names, pieces) ?: ReplayCopy.LEVEL
}

/** The "ahead" line, or null when the facts do not tell the sides apart. Mirrors ticket 24's hints-then-pieces rule; the leader itself comes from `state.lead`. */
private fun aheadLine(
    lead: Lead,
    mineHints: Int,
    theirsHints: Int,
    mineCorrect: Int,
    theirsCorrect: Int,
    names: CompareNames,
    pieces: String,
): String? {
    if (lead == Lead.Level) return null
    val mineLeads = lead == Lead.Mine
    val name = if (mineLeads) names.mine else names.theirs
    val leaderHints = if (mineLeads) mineHints else theirsHints
    val otherHints = if (mineLeads) theirsHints else mineHints
    if (leaderHints != otherHints) return ReplayCopy.ahead(name, ReplayCopy.hintsVs(leaderHints, otherHints))
    val leaderCorrect = if (mineLeads) mineCorrect else theirsCorrect
    val otherCorrect = if (mineLeads) theirsCorrect else mineCorrect
    if (leaderCorrect != otherCorrect) return ReplayCopy.ahead(name, ReplayCopy.piecesVs(leaderCorrect, otherCorrect, pieces))
    return null
}

/** What a screen reader hears: the line up to its first figure, so a count ticking up is not read out. */
internal fun leadAnnouncement(text: String): String = text.substringBefore(SEPARATOR)

/** The lead line as plain text, centred; pass `Modifier.fillMaxWidth()`. A live region that changes only when the leader or the state does. */
@Composable
fun ReplayLeadLine(
    state: ReplayPlaybackState,
    mine: Timeline,
    theirs: Timeline,
    names: CompareNames,
    pieces: String,
    modifier: Modifier = Modifier,
) {
    val text = leadLineText(state, mine.outcome, theirs.outcome, mine.endMs, theirs.endMs, names, pieces) ?: return
    val announcement = remember(text) { leadAnnouncement(text) }
    Text(
        text = text,
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = announcement
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
