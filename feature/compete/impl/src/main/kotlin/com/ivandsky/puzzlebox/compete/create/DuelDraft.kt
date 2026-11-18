package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.compete.duel.formatEnds
import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.domain.DuelSettings
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Which preset the sheet shows as chosen; any edit to a Quick draft leaves it Custom. */
internal enum class DuelPreset { Quick, Daily, Custom }

/**
 * A game the sheet can offer: its [name], today's puzzle [todayNumber] (null when its source numbers none) and the
 * [todayDay] that is today for it, and whether the player already [solvedToday].
 */
internal data class DuelGame(
    val id: GameId,
    val name: String,
    val todayNumber: Int?,
    val solvedToday: Boolean,
    val todayDay: LocalDate? = null,
)

/**
 * What the sheet has chosen so far. [todayOnly] is the Daily duel's rule; everything else is the customize panel.
 * Pure: [toSettings] and [summary] hold the mapping and the footer text, and every edit returns a new draft.
 */
internal data class DuelDraft(
    val preset: DuelPreset,
    val count: Int,
    val games: Set<GameId>,
    val todayOnly: Boolean,
    val deadlineHours: Int,
    val includeFriends: Boolean,
) {

    /** The request [CreateMatch] takes; this sheet never pastes codes (Enter code does). A [lineup] decides the puzzles instead of the count and games. */
    fun toSettings(lineup: List<LineupEntry>? = null) = DuelSettings(
        count = count,
        games = games,
        todayOnly = todayOnly,
        deadlineHours = deadlineHours,
        includeFriends = includeFriends,
        pastedCodes = emptyList(),
        lineup = lineup,
    )

    /** "Best of 3 · mixed past dailies · ends Sun 18:00", plus " · incl. friends' puzzles" when friends are in. */
    fun summary(now: Instant, zone: ZoneId, available: List<DuelGame>, locale: Locale): String {
        val chosen = available.filter { it.id in games }.map { it.name }
        val gamesText = when {
            todayOnly -> "today's ${chosen.joinToString(", ")}"
            chosen.size == available.size -> "mixed past dailies"
            else -> chosen.joinToString(", ")
        }
        val ends = formatEnds(now.plusSeconds(deadlineHours * SECONDS_PER_HOUR), zone, locale)
        return buildString {
            append("Best of $count · $gamesText · ends $ends")
            if (includeFriends) append(" · incl. friends' puzzles")
        }
    }

    fun withDeadline(hours: Int): DuelDraft = edited().copy(deadlineHours = hours)
    fun withFriends(included: Boolean): DuelDraft = edited().copy(includeFriends = included)

    /** Flips [game]; the last chosen game stays, and a solved game cannot join a today-only duel. A today-only duel plays each chosen game once. */
    fun toggled(game: DuelGame): DuelDraft {
        if (todayOnly && game.solvedToday) return this
        val next = if (game.id in games) games - game.id else games + game.id
        if (next.isEmpty()) return this
        val rounds = if (todayOnly) next.size.coerceIn(MIN_COUNT, Match.MAX_LEGS) else count
        return edited().copy(games = next, count = rounds)
    }

    /** Customize as a preset of its own, which the lineup editor needs: neither Quick's nor Daily's rules apply any more. */
    fun customized(): DuelDraft = copy(preset = DuelPreset.Custom, todayOnly = false)

    /** Daily with today's puzzles as lineup rows, which take the place of its game chips. */
    fun showsRows(lineup: LineupEditor, available: List<DuelGame>): Boolean = preset == DuelPreset.Daily && lineup.draft.isToday(available)

    /** Quick leaves its preset on the first edit; the Daily duel stays today-only whatever else changes. */
    private fun edited(): DuelDraft = if (preset == DuelPreset.Quick) copy(preset = DuelPreset.Custom) else this

    companion object {
        const val MIN_COUNT = 1
        private const val QUICK_COUNT = 3
        private const val SECONDS_PER_HOUR = 3_600L

        /** Best of 3, mixed past dailies of every game, 24 h. */
        fun quick(available: List<DuelGame>) = DuelDraft(
            preset = DuelPreset.Quick,
            count = QUICK_COUNT,
            games = available.mapTo(LinkedHashSet()) { it.id },
            todayOnly = false,
            deadlineHours = Match.DEADLINE_CHOICES.first(),
            includeFriends = false,
        )

        /** One puzzle of [game] alone, mixed past dailies, 24 h: a challenge to a friend in that game. */
        fun single(game: GameId) = DuelDraft(
            preset = DuelPreset.Custom,
            count = MIN_COUNT,
            games = setOf(game),
            todayOnly = false,
            deadlineHours = Match.DEADLINE_CHOICES.first(),
            includeFriends = false,
        )

        /** Today's puzzle of each game not solved yet, 24 h; one round per game. */
        fun daily(available: List<DuelGame>): DuelDraft {
            val open = available.filterNot { it.solvedToday }.mapTo(LinkedHashSet()) { it.id }
            return quick(available).copy(
                preset = DuelPreset.Daily,
                count = open.size.coerceIn(MIN_COUNT, Match.MAX_LEGS),
                games = open,
                todayOnly = true,
            )
        }
    }
}
