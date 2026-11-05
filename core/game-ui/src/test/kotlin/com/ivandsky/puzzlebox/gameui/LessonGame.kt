package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

private data class LessonRef(
    val lesson: Int,
    override val gameId: GameId = GameId("lesson"),
    override val date: LocalDate = LocalDate.of(2026, 11, 4),
    override val puzzleNumber: Int? = null,
) : PuzzleRef

/**
 * A stand-in game for the tutorial tests: lesson N's puzzle payload is the byte N, and
 * its board shows that number plus a "Solve" tap that wins the way a real game does —
 * it publishes the session bytes, then emits one completion record. Every controller it
 * makes is kept in [controllers] so a test can check what the game was told. Lessons in
 * [guided] are guided steps; when [canGuide] is off the game hands out controllers that
 * are not a [PlayGuide].
 */
class LessonGame(lessons: Int = 3, guided: Set<Int> = emptySet()) : GamePlayUi {

    override val gameId = GameId("lesson")

    override val tutorial: List<TutorialStep> = (1..lessons).map { lesson ->
        TutorialStep(
            instruction = "Lesson $lesson: tap Solve.",
            puzzle = Puzzle(gameId, VariantId("lesson/v1"), 1, byteArrayOf(lesson.toByte())),
            ref = LessonRef(lesson),
            guided = lesson in guided,
        )
    }

    val controllers = mutableListOf<Controller>()

    /** When set, [Controller.adoptToday] rejects every envelope with this copy. */
    var rejectWith: String? = null

    /** When off, controllers come without [PlayGuide]. */
    var canGuide = true

    override fun createController(env: PlayEnvironment): GamePlayController {
        val controller = Controller(env, this).also { controllers += it }
        return if (canGuide) controller else Unguided(controller)
    }

    /** The same controller with its [PlayGuide] side hidden. */
    private class Unguided(val inner: Controller) : GamePlayController by inner

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        val lesson = controller as Controller
        Column(modifier) {
            Text("Board ${lesson.lesson}", Modifier.testTag("lesson.board"))
            Text("Solve", Modifier.testTag("lesson.solve").clickable { lesson.solve() })
        }
    }

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        onHowToPlay: (() -> Unit)?,
        modifier: Modifier,
    ) = Board(controller, modifier)

    class Controller(private val env: PlayEnvironment, private val game: LessonGame) :
        GamePlayController, PlayFeedback, PlayGuide {

        /** The adopted lesson's number (its payload byte); 0 before adoption. */
        var lesson by mutableStateOf(0)
            private set

        private var ref: PuzzleRef? = null

        override var isComplete by mutableStateOf(false)
            private set

        override var violation: String? by mutableStateOf(null)
        override val rejection: String? = null

        /** Session bytes this controller handed to its environment. */
        val published = mutableListOf<ByteArray>()

        /** Completion records this controller handed to its environment. */
        val won = mutableListOf<CompletionRecord>()

        /** How many times the tutorial asked this controller to guide; with the lesson it held then. */
        val guidedLessons = mutableListOf<Int>()

        override fun guide() {
            guidedLessons += lesson
        }

        override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt {
            game.rejectWith?.let { return Adopt.Rejected(it) }
            lesson = puzzle.payload.single().toInt()
            this.ref = ref
            return Adopt.Adopted
        }

        override fun adoptRestored(bytes: ByteArray): Restored? = null

        override fun replay() = Unit

        fun solve() {
            if (isComplete) return
            isComplete = true
            val bytes = byteArrayOf(lesson.toByte(), 1)
            published += bytes
            env.onSessionChanged(bytes)
            val record = CompletionRecord(checkNotNull(ref), time = 1_000L, hints = 0, stats = RecordStats(1, "{}"))
            won += record
            env.onWon(record)
        }
    }
}
