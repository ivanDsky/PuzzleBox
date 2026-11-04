package com.ivandsky.puzzlebox.play

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.LocalDate

/**
 * A stand-in game play UI (feature:play:impl never sees a real game, ADR-0007 §7): its
 * session is a readable string — `fake|<moves>|<complete>|<date>|<number>` — so tests
 * can assert exactly what was adopted, published and restored.
 */
class FakePlayUi(override val gameId: GameId) : GamePlayUi {

    val controllers = mutableListOf<FakeController>()

    /** When set, [GamePlayController.adoptToday] rejects the envelope with this copy. */
    var rejectWith: String? = null

    /** Tango/Queens publish the freshly adopted session; Zip only publishes on change. */
    var publishesAdoptedSession = true

    val latest: FakeController get() = controllers.last()

    override fun createController(env: PlayEnvironment): GamePlayController =
        FakeController(gameId, env, this).also { controllers += it }

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) {
        val fake = controller as FakeController
        if (status is PlayBoardStatus.Ready) {
            Text("${gameId.value} board ${fake.moves}", modifier.testTag("fake.board"))
            // Keyed like the real games' handoff: on the completion VALUE, so re-adopting
            // the same completed bytes (a fresh controller) never hands off twice.
            val completed = fake.isComplete
            LaunchedEffect(completed, fake.ref, fake.replays) { if (completed) onSolved(FakeController.TIME_MS, 0) }
        } else {
            PlayBoardFallback(status, onRetry, "play")
        }
    }
}

class FakeController(
    private val game: GameId,
    private val env: PlayEnvironment,
    private val ui: FakePlayUi,
) : GamePlayController {

    var puzzle: Puzzle? = null
        private set
    var ref: PuzzleRef? by mutableStateOf(null)
        private set
    var moves by mutableIntStateOf(0)
        private set
    override var isComplete by mutableStateOf(false)
        private set
    var replays by mutableIntStateOf(0)
        private set

    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt {
        ui.rejectWith?.let { return Adopt.Rejected(it) }
        this.puzzle = puzzle
        this.ref = ref
        if (ui.publishesAdoptedSession) publish()
        return Adopt.Adopted
    }

    override fun adoptRestored(bytes: ByteArray): Restored? {
        val parts = bytes.decodeToString().split('|')
        if (parts.size != 5 || parts[0] != "fake") return null
        moves = parts[1].toInt()
        isComplete = parts[2].toBoolean()
        ref = TestGames.Ref(game, LocalDate.parse(parts[3]), parts[4].toIntOrNull())
        return Restored(ref)
    }

    override fun replay() {
        replays++
        moves = 0
        isComplete = false
        publish()
    }

    /** One committed move: published like every game's session change. */
    fun move() {
        moves++
        publish()
    }

    /** The win transition: one completion record for the adopted identity. */
    fun win() {
        isComplete = true
        publish()
        env.onWon(record(ref!!))
    }

    fun bytes(): ByteArray = encode(moves, isComplete, ref)

    private fun publish() = env.onSessionChanged(bytes())

    companion object {
        const val TIME_MS = 42_000L

        fun record(ref: PuzzleRef) = CompletionRecord(ref, TIME_MS, 0, RecordStats(1, "{}"))

        fun encode(moves: Int, complete: Boolean, ref: PuzzleRef?): ByteArray =
            "fake|$moves|$complete|${ref?.date}|${ref?.puzzleNumber}".encodeToByteArray()

        /** A [SessionBlob] envelope of a fake session, as the route's saveable holds it. */
        fun envelope(game: GameId, moves: Int, complete: Boolean = false, day: LocalDate, number: Int? = 7): SessionBlob.Decoded =
            SessionBlob.Decoded(game, encode(moves, complete, TestGames.Ref(game, day, number)))
    }
}

/** The fake games' play UIs plus the registries over them (registry order = [ids]). */
class FakeGames(ids: List<GameId>) {
    val uis: Map<GameId, FakePlayUi> = ids.associateWith(::FakePlayUi)
    val registry = GamePlayRegistry(uis.values.toList())
    val games = TestGames.registry(ids)

    operator fun get(id: GameId): FakePlayUi = uis.getValue(id)
}
