package com.ivandsky.puzzlebox.game.linkedin

import com.ivandsky.puzzlebox.game.BestScore
import java.util.Locale

/**
 * The board after a LinkedIn time wins: each PuzzleBox row's [rowScores], and which ghosts are still [ghostKept].
 * [youRow] is the PuzzleBox row that is the player (null when none is), and [addOwnRow] says that none is but the
 * player's own LinkedIn row must be added to the board at their imported time.
 */
data class LinkedInTimeWins(
    val rowScores: List<BestScore>,
    val ghostKept: List<Boolean>,
    val youRow: Int? = null,
    val addOwnRow: Boolean = false,
)

private val Spaces = Regex("\\s+")

// The name is the only link the app has between a PuzzleBox player and a LinkedIn connection, so it is compared
// loosely: case, outer spaces and runs of spaces do not tell two people apart. A blank name links nobody.
private fun linkKey(name: String): String? =
    name.trim().lowercase(Locale.ROOT).replace(Spaces, " ").ifEmpty { null }

/**
 * Lets a LinkedIn time win over the PuzzleBox one, for a board's PuzzleBox rows ([rowNames], [rowScores]) and its
 * LinkedIn ghosts ([ghostNames], [ghostScores], a null score being a hidden time):
 *  - the player's row is the self row ([selfRow], null when this profile is not on the board); else the first row named
 *    like the owner's LinkedIn name ([ownName], the same loose match as for ghosts), which is the player's account under
 *    another profile; else, when the player has an imported [own] score, their own LinkedIn row is to be added
 *    ([LinkedInTimeWins.addOwnRow]), whether or not a LinkedIn name is known. The player's row takes the [own] score, when there is one;
 *  - a ghost whose name matches a PuzzleBox row that is not the player's row is that same person: the row takes the
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
    ownName: String? = null,
): LinkedInTimeWins {
    val scores = rowScores.toMutableList()
    val rowKeys = rowNames.map(::linkKey)
    val ownKey = ownName?.let(::linkKey)
    val youRow = selfRow ?: ownKey?.let { key -> rowKeys.indexOf(key).takeIf { it >= 0 } }
    if (own != null && youRow != null) scores[youRow] = own
    val rowsByKey = HashMap<String, MutableList<Int>>()
    rowKeys.forEachIndexed { row, key ->
        if (key != null && row != youRow) rowsByKey.getOrPut(key) { ArrayList(1) } += row
    }
    val takenFrom = HashSet<Int>()
    val kept = ghostNames.mapIndexed { ghost, name ->
        val rows = linkKey(name)?.let(rowsByKey::get) ?: return@mapIndexed true
        val score = ghostScores[ghost]
        // The first timed ghost of a name gives the time; later ones of that name are the same person too.
        if (score != null) rows.filter(takenFrom::add).forEach { scores[it] = score }
        false
    }
    return LinkedInTimeWins(scores, kept, youRow, addOwnRow = youRow == null && own != null)
}
