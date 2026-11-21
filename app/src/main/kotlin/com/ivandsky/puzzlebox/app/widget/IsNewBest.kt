package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

/**
 * Whether [game]'s solve on [day] beats or ties the best of its earlier days, by the game's own [RankOrder]. A first
 * ever solve has no earlier day to beat, so it is not a new best. Reads daily first solves only, as Today's set does.
 */
class IsNewBest {

    operator fun invoke(records: List<CompletionRecord>, game: GameId, day: LocalDate, order: RankOrder): Boolean {
        var today: BestScore? = null
        var earlier: BestScore? = null
        for (record in records) {
            if (record.puzzleRef.gameId != game) continue
            val date = (record.puzzleRef.key as? PuzzleKey.Daily)?.date ?: continue
            val score = BestScore(record.time, record.hints)
            when {
                date == day -> today = score
                date.isBefore(day) -> earlier = listOfNotNull(earlier, score).minWith(order.comparator)
            }
        }
        return today != null && earlier != null && order.comparator.compare(today, earlier) <= 0
    }
}
