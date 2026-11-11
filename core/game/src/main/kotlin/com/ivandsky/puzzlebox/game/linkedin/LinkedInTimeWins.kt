package com.ivandsky.puzzlebox.game.linkedin

import com.ivandsky.puzzlebox.game.BestScore
import java.util.Locale

/** The board after a LinkedIn time wins: each PuzzleBox row's [rowScores], and which ghosts are still [ghostKept]. */
data class LinkedInTimeWins(val rowScores: List<BestScore>, val ghostKept: List<Boolean>)

private val Spaces = Regex("\\s+")

// The name is the only link the app has between a PuzzleBox player and a LinkedIn connection, so it is compared
// loosely: case, outer spaces and runs of spaces do not tell two people apart. A blank name links nobody.
private fun linkKey(name: String): String? =
    name.trim().lowercase(Locale.ROOT).replace(Spaces, " ").ifEmpty { null }

/**
 * Lets a LinkedIn time win over the PuzzleBox one, for a board's PuzzleBox rows ([rowNames], [rowScores]) and its
 * LinkedIn ghosts ([ghostNames], [ghostScores], a null score being a hidden time):
 *  - the self row ([selfRow], null when the player is not on the board) takes the player's imported [own] score;
 *  - a ghost whose name matches a PuzzleBox row that is not the self row is that same person: the row takes the
 *    ghost's score (a hidden-time ghost leaves the row's own) and the ghost is dropped, so the person is drawn once.
 * Display only: nothing here is posted, and the rows' PuzzleBox ranks are the caller's to keep.
 */
fun linkedInTimeWins(
    rowNames: List<String>,
    rowScores: List<BestScore>,
    selfRow: Int?,
    ghostNames: List<String>,
    ghostScores: List<BestScore?>,
    own: BestScore?,
): LinkedInTimeWins {
    val scores = rowScores.toMutableList()
    if (own != null && selfRow != null) scores[selfRow] = own
    val rowsByKey = HashMap<String, MutableList<Int>>()
    rowNames.forEachIndexed { row, name ->
        val key = linkKey(name)
        if (key != null && row != selfRow) rowsByKey.getOrPut(key) { ArrayList(1) } += row
    }
    val takenFrom = HashSet<Int>()
    val kept = ghostNames.mapIndexed { ghost, name ->
        val rows = linkKey(name)?.let(rowsByKey::get) ?: return@mapIndexed true
        val score = ghostScores[ghost]
        // The first timed ghost of a name gives the time; later ones of that name are the same person too.
        if (score != null) rows.filter(takenFrom::add).forEach { scores[it] = score }
        false
    }
    return LinkedInTimeWins(scores, kept)
}
