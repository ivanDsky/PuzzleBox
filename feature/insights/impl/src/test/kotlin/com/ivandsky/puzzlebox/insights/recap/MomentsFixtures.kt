package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment.Kind
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.FastestFixtures.miniSudoku
import com.ivandsky.puzzlebox.insights.recap.FastestFixtures.queens
import com.ivandsky.puzzlebox.insights.recap.FastestFixtures.strands
import com.ivandsky.puzzlebox.insights.recap.FastestFixtures.tango
import com.ivandsky.puzzlebox.insights.recap.FastestFixtures.zip
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** The October 2026 dataset the Moments card's references are drawn from (contract-shared.md). */
internal object MomentsFixtures {
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun best(day: Int, game: GameId) = EarnedMoment(Kind.PersonalBest, oct(day), game)
    private fun under(day: Int, game: GameId, ms: Long) = EarnedMoment(Kind.FirstSubX, oct(day), game, thresholdMs = ms)
    private fun set(day: Int) = EarnedMoment(Kind.FullSet, oct(day))
    private fun streak(day: Int, days: Int) = EarnedMoment(Kind.Streak, oct(day), streakDays = days)

    /** Six stickers, one of each kind but a best and a full set twice; the fifth is the one the peeled frame lifts. */
    val default = RecapCard.Moments(
        listOf(best(9, tango), set(11), streak(12, 7), best(17, queens), under(22, zip, 60_000), set(25)),
    )

    /** One sticker: a large one alone on the sheet. */
    val single = RecapCard.Moments(listOf(best(3, queens)))

    /** Fourteen stickers, the most the card is drawn for: 5 bests, 3 firsts, 4 full sets and both streaks. */
    val longest = RecapCard.Moments(
        listOf(
            best(3, queens), under(5, tango, 60_000), best(6, tango), streak(7, 7), set(8), best(9, zip), under(12, zip, 30_000),
            best(14, queens), set(16), best(18, strands), set(20), under(22, miniSudoku, 60_000), set(27), streak(30, 30),
        ),
    )

    /** [card] with no game on its moments, for a page whose game-name map does not know this dataset's games. */
    fun withoutGames(card: RecapCard.Moments) = card.copy(earned = card.earned.map { it.copy(game = null) })

    /** The recap story round a Moments card, opening on it with a Solved card before and cards after. */
    fun story(card: RecapCard.Moments): RecapUiState.Ready {
        val others = listOf(RecapFixtures.streak, RecapFixtures.secondSolved, RecapFixtures.streak)
        return RecapUiState.Ready(
            period = RecapFixtures.november, title = "October 2026",
            cards = listOf(RecapFixtures.solved, card) + others, initialPage = 1,
        )
    }
}
