package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.play.domain.ArchiveProgress
import com.ivandsky.puzzlebox.resultui.GameCardModel
import java.time.LocalDate

/**
 * One game in the library: its [card] (status null = still loading; puzzle number =
 * [today]), today's puzzle number, its archive [progress] (null for a game without an
 * archive, or before it loads), its [stats] and its three how-to-play [rules].
 */
internal data class LibraryRowModel(
    val card: GameCardModel,
    val today: Int?,
    val progress: ArchiveProgress?,
    val stats: GameStats,
    val rules: List<String>,
    /** Whether the game's source is a LinkedIn one; a game with no LinkedIn feed (Strands) has no LinkedIn line on its page. */
    val linkedInGame: Boolean = true,
) {
    /** The day the card shows with nothing picked: the latest published day, null until the archive loads. */
    val cardDay: LocalDate? get() = progress?.range?.endInclusive
}
