package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.domain.SelfDuelSettings
import com.ivandsky.puzzlebox.model.GameId

/** Who the sheet's duel is against. */
internal enum class Opponent { Friend, PastYou }

/** How many solved puzzles each game offers a self duel, as `ComputeSolvedPool` lists them. */
internal data class SolvedCounts(val byGame: Map<GameId, Int>) {
    fun of(game: GameId): Int = byGame[game] ?: 0

    /** Most rounds a duel over [games] can have: every solved puzzle once, never more than a duel holds. */
    fun cap(games: Set<GameId>): Int = games.sumOf(::of).coerceAtMost(Match.MAX_LEGS)
}

/**
 * What the sheet has chosen for a duel against Past you: Quick or Custom, never Daily, and no deadline. Pure like
 * [DuelDraft]: every edit returns a new form, and [rounds] / [toSettings] bring in the solved pool's [SolvedCounts].
 */
internal data class SelfDuelForm(
    val preset: DuelPreset,
    val count: Int,
    val games: Set<GameId>,
    val includeFriends: Boolean,
) {
    /** Rounds the duel will have: the chosen [count], held down to what the solved pool can fill. */
    fun rounds(counts: SolvedCounts): Int = count.coerceAtMost(counts.cap(games))

    fun toSettings(counts: SolvedCounts) = SelfDuelSettings(rounds(counts), games, includeFriends)

    fun withCount(value: Int): SelfDuelForm = edited().copy(count = value.coerceIn(DuelDraft.MIN_COUNT, Match.MAX_LEGS))
    fun withFriends(included: Boolean): SelfDuelForm = edited().copy(includeFriends = included)

    /** Flips [game]; the last chosen game stays. */
    fun toggled(game: GameId): SelfDuelForm {
        val next = if (game in games) games - game else games + game
        return if (next.isEmpty()) this else edited().copy(games = next)
    }

    /** Customize chosen: Quick becomes Custom, the form otherwise as it is. */
    fun customized(): SelfDuelForm = edited()

    private fun edited(): SelfDuelForm = if (preset == DuelPreset.Quick) copy(preset = DuelPreset.Custom) else this

    companion object {
        private const val QUICK_COUNT = 3

        /** Best of 3, mixed solved puzzles of every game. */
        fun quick(available: List<DuelGame>) = SelfDuelForm(
            preset = DuelPreset.Quick,
            count = QUICK_COUNT,
            games = available.mapTo(LinkedHashSet()) { it.id },
            includeFriends = false,
        )
    }
}

/** The self form with the pool it is measured against: everything the Past you panel draws. [counts] is null until the pool is first read. */
internal data class SelfDuelView(
    val form: SelfDuelForm,
    val counts: SolvedCounts?,
    val games: List<DuelGame>,
) {
    private val chosen: List<DuelGame> get() = games.filter { it.id in form.games }

    /** Solved puzzles the chosen games offer, up to a duel's most. */
    val cap: Int get() = counts?.cap(form.games) ?: 0

    /** Rounds shown and played; 0 while the pool is unread or empty. */
    val rounds: Int get() = counts?.let(form::rounds) ?: 0

    val canCreate: Boolean get() = rounds > 0

    /** The pool is read and has fewer puzzles than the chosen rounds. */
    val capped: Boolean get() = cap > 0 && form.count > cap

    /** The pool is read and the chosen games have no solved puzzle. */
    val empty: Boolean get() = counts != null && cap == 0

    fun solved(game: DuelGame): Int = counts?.of(game.id) ?: 0

    /** The chosen game's name when it is just one; null for several, which a sentence then calls "these games". */
    val soleGame: String? get() = chosen.singleOrNull()?.name

    /** "Best of 3 · mixed solved puzzles · against your first solves", plus " · incl. friends' puzzles" when friends are in. */
    val summary: String
        get() {
            val names = when {
                chosen.size == games.size -> "mixed solved puzzles"
                else -> chosen.joinToString(", ") { it.name }
            }
            return buildString {
                append("Best of ${if (counts == null) form.count else rounds} · $names")
                if (form.includeFriends) append(" · incl. friends' puzzles")
                append(" · against your first solves")
            }
        }

    companion object {
        fun initial() = SelfDuelView(SelfDuelForm.quick(emptyList()), counts = null, games = emptyList())
    }
}
