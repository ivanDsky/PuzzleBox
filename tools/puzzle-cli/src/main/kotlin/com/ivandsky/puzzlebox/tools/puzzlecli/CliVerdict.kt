package com.ivandsky.puzzlebox.tools.puzzlecli

import kotlinx.serialization.Serializable

/** The one JSON line `puzzle-cli validate` prints (§9.2). */
@Serializable
data class CliVerdict(
    val schema: Int = SCHEMA,
    val game: String, // header game id; "" when the header is missing
    val verdict: String, // "unique" | "multiple" | "none" | "incomplete" | "undetermined" | "malformed"
    val difficulty: String?, // Difficulty.Level lower-case; null when none
    val needsGuessing: Boolean?, // null unless verdict == "unique"
    val differingCells: List<List<Int>>, // [row, col], 1-based (EditorNotation.columns)
    val conflicts: List<List<Int>>, // [row, col], 1-based
    val blocks: List<String>, // GameEditor.publishBlocks ids (board gates of spec A8)
    val errors: List<CliError>, // non-empty iff verdict == "malformed"
    val solution: List<Int>? = null, // since M5-04: Solution.values, `solve` on "unique" only
) {
    companion object {
        const val SCHEMA: Int = 1
    }
}
