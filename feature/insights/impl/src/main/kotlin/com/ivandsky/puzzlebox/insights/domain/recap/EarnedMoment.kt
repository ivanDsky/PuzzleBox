package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * One Moment the period earned, as the Moments card shows it: its [kind], the puzzle [day] and, for a personal best or
 * a first under a time, its [game]. [thresholdMs] is that time, and [streakDays] the length a streak reached; each is null for the other kinds.
 */
internal data class EarnedMoment(
    val kind: Kind,
    val day: LocalDate,
    val game: GameId? = null,
    val thresholdMs: Long? = null,
    val streakDays: Int? = null,
) {
    /** The local kinds, strongest first (A6: friend Moments are not among them). */
    enum class Kind { PersonalBest, FirstSubX, FullSet, Streak }
}
