package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.ScoredGrader
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.model.GameId

/**
 * [FakeDraftCompleter] with a score (90): a Unique board grades to [levelOf] its marks, with the
 * mark count as its score and no ladder rung, the way a game without rungs (Zip) grades. Tests
 * of the Studio's level picker, its "finished at another level" message and the scored label set
 * [levelOf]; [graded] holds every board the grader was asked about.
 */
class FakeScoredGrader(
    gameId: GameId = GameId("fake"),
) : FakeDraftCompleter(gameId), ScoredGrader<TestDraft> {

    var levelOf: (TestDraft) -> Difficulty.Level = { Difficulty.Level.Medium }

    val graded = mutableListOf<TestDraft>()

    override fun grade(draft: TestDraft, solution: Solution, budget: Budget): Difficulty {
        graded += draft
        return Difficulty(levelOf(draft), score = draft.marks, hardestRung = null)
    }
}
