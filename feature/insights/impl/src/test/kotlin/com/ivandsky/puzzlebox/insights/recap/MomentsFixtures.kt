package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment.Kind
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
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
    private fun y(month: Int, day: Int) = LocalDate.of(2025, month, day)
    private fun best(day: LocalDate, game: GameId) = EarnedMoment(Kind.PersonalBest, day, game)
    private fun under(day: LocalDate, game: GameId, ms: Long) = EarnedMoment(Kind.FirstSubX, day, game, thresholdMs = ms)
    private fun set(day: LocalDate) = EarnedMoment(Kind.FullSet, day)
    private fun streak(day: LocalDate, days: Int) = EarnedMoment(Kind.Streak, day, streakDays = days)

    /** Six stickers, one of each kind but a best and a full set twice; the fifth is the one the peeled frame lifts. */
    val default = RecapCard.Moments(
        listOf(best(oct(9), tango), set(oct(11)), streak(oct(12), 7), best(oct(17), queens), under(oct(22), zip, 60_000), set(oct(25))),
    )

    /** One sticker: a large one alone on the sheet. */
    val single = RecapCard.Moments(listOf(best(oct(3), queens)))

    /** Fourteen stickers, the most the card is drawn for: 5 bests, 3 firsts, 4 full sets and both streaks. */
    val longest = RecapCard.Moments(
        listOf(
            best(oct(3), queens), under(oct(5), tango, 60_000), best(oct(6), tango), streak(oct(7), 7), set(oct(8)), best(oct(9), zip),
            under(oct(12), zip, 30_000), best(oct(14), queens), set(oct(16)), best(oct(18), strands), set(oct(20)),
            under(oct(22), miniSudoku, 60_000), set(oct(27)), streak(oct(30), 30),
        ),
    )

    /** A year of stickers, 32 across 2025 as on the reported year card: 12 bests, 6 firsts, 11 full sets and three streaks. */
    val year = RecapCard.Moments(
        listOf(
            best(y(1, 4), queens), set(y(1, 15)), streak(y(1, 26), 7), best(y(2, 6), tango), under(y(2, 17), zip, 60_000),
            set(y(2, 28)), best(y(3, 11), miniSudoku), set(y(3, 22)), best(y(4, 2), strands), streak(y(4, 13), 30),
            under(y(4, 24), queens, 30_000), set(y(5, 5)), best(y(5, 16), tango), set(y(5, 27)), best(y(6, 7), zip),
            under(y(6, 18), miniSudoku, 90_000), set(y(6, 29)), best(y(7, 10), strands), set(y(7, 21)),
            best(y(8, 1), queens), set(y(8, 12)), under(y(8, 23), tango, 60_000), best(y(9, 3), zip), set(y(9, 14)),
            streak(y(9, 25), 100), best(y(10, 6), miniSudoku), set(y(10, 17)), under(y(10, 28), strands, 45_000),
            best(y(11, 8), queens), set(y(11, 19)), best(y(11, 30), tango), under(y(12, 11), zip, 30_000),
        ),
    )

    /** [card] with no game on its moments, for a page whose game-name map does not know this dataset's games. */
    fun withoutGames(card: RecapCard.Moments) = card.copy(earned = card.earned.map { it.copy(game = null) })

    /** The recap story round a Moments card for [period] titled [title], opening on it with a Solved card before and cards after. */
    fun story(card: RecapCard.Moments, period: RecapPeriod = RecapFixtures.november, title: String = "October 2026"): RecapUiState.Ready {
        val others = listOf(RecapFixtures.streak, RecapFixtures.secondSolved, RecapFixtures.streak)
        return RecapUiState.Ready(
            period = period, title = title,
            cards = listOf(RecapFixtures.solved, card) + others, initialPage = 1,
        )
    }
}
