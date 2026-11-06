package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
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
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayClockEffects
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.LocalDate

/**
 * A stand-in game play UI (feature:play:impl never sees a real game, ADR-0007 §7): its
 * session is a readable string — `fake|<moves>|<complete>|<date>|<number>|<elapsedMs>`
 * — so tests can assert exactly what was adopted, published and restored. Its clock is
 * the real games' shape: the first move opens a window, a background fold banks it into
 * the session (and publishes), folding a paused clock banks nothing. It has no tutorial
 * until a test calls [teach]; the route's top bar then offers How to play.
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

    /** None by default, like a game whose lessons have not landed yet. */
    override var tutorial: List<TutorialStep> = emptyList()
        private set

    /** Gives the game [count] lessons; each is won with one [FakeController.win]. */
    fun teach(count: Int) {
        tutorial = (1..count).map { lesson ->
            TutorialStep("Lesson $lesson", TestGames.puzzle(gameId, lesson.toByte()), TestGames.Ref(gameId, PuzzleKey.Daily(LESSON_DAY, null)))
        }
    }

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        Text("${gameId.value} board ${(controller as FakeController).moves}", modifier.testTag("fake.board"))
    }

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
        Column {
            if (status is PlayBoardStatus.Ready) {
                // The real games' chrome clock: leaving the composition folds the window.
                PlayClockEffects(fake)
                Board(fake, modifier)
                // Keyed like the real games' handoff: on the completion VALUE, so re-adopting
                // the same completed bytes (a fresh controller) never hands off twice.
                val completed = fake.isComplete
                LaunchedEffect(completed, fake.ref, fake.replays) { if (completed) onSolved(FakeController.TIME_MS, 0) }
            } else {
                PlayBoardFallback(status, onRetry, "play")
            }
        }
    }

    private companion object {
        /** Tutorial refs are custom, never today's daily. */
        val LESSON_DAY: LocalDate = LocalDate.of(2000, 1, 1)
    }
}

class FakeController(
    private val game: GameId,
    private val env: PlayEnvironment,
    private val ui: FakePlayUi,
) : GamePlayController, PlayClock {

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

    /** Banked clock time (in the session bytes) and the open running window, if any. */
    var bankedMs = 0L
        private set
    private var windowMs: Long? = null
    private var windows = 0

    /** How many background folds actually banked time. */
    var folds = 0
        private set

    override val clockWindow: Any? get() = windowMs?.let { windows }

    override fun tick() = Unit

    override fun onBackground() {
        val window = windowMs ?: return
        bankedMs += window
        windowMs = null
        folds++
        publish()
    }

    override fun onForeground() {
        if (windowMs == null && moves > 0 && !isComplete) openWindow()
    }

    override fun displayedElapsedMs(): Long = bankedMs + (windowMs ?: 0L)

    /** Running play time passes (the tick loop's job in a real game). */
    fun advance(ms: Long) {
        windowMs = windowMs?.plus(ms)
    }

    private fun openWindow() {
        windows++
        windowMs = 0L
    }

    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt {
        ui.rejectWith?.let { return Adopt.Rejected(it) }
        this.puzzle = puzzle
        this.ref = ref
        if (ui.publishesAdoptedSession) publish()
        return Adopt.Adopted
    }

    override fun adoptRestored(bytes: ByteArray): Restored? {
        val parts = bytes.decodeToString().split('|')
        if (parts.size !in 5..6 || parts[0] != "fake") return null
        moves = parts[1].toInt()
        isComplete = parts[2].toBoolean()
        ref = TestGames.Ref(game, PuzzleKey.Daily(LocalDate.parse(parts[3]), parts[4].toIntOrNull()))
        bankedMs = parts.getOrNull(5)?.toLong() ?: 0L
        windowMs = null
        return Restored(ref)
    }

    override fun replay() {
        replays++
        moves = 0
        isComplete = false
        bankedMs = 0L
        windowMs = null
        publish()
    }

    /** One committed move: opens the clock on the first, published like every game's change. */
    fun move() {
        moves++
        if (windowMs == null) openWindow()
        publish()
    }

    /** The win transition: the clock stops, then one completion record for the adopted identity. */
    fun win() {
        windowMs?.let { bankedMs += it }
        windowMs = null
        isComplete = true
        publish()
        env.onWon(record(ref!!))
    }

    fun bytes(): ByteArray = encode(moves, isComplete, ref, bankedMs)

    private fun publish() = env.onSessionChanged(bytes())

    companion object {
        const val TIME_MS = 42_000L

        fun record(ref: PuzzleRef) = CompletionRecord(ref, TIME_MS, 0, RecordStats(1, "{}"))

        fun encode(moves: Int, complete: Boolean, ref: PuzzleRef?, elapsedMs: Long = 0L): ByteArray {
            val daily = ref?.key as? PuzzleKey.Daily
            return "fake|$moves|$complete|${daily?.date}|${daily?.puzzleNumber}|$elapsedMs".encodeToByteArray()
        }

        /** A [SessionBlob] envelope of a fake session, as the route's saveable holds it. */
        fun envelope(
            game: GameId,
            moves: Int,
            complete: Boolean = false,
            day: LocalDate,
            number: Int? = 7,
            elapsedMs: Long = 0L,
        ): SessionBlob.Decoded = SessionBlob.Decoded(game, encode(moves, complete, TestGames.Ref(game, PuzzleKey.Daily(day, number)), elapsedMs))
    }
}

/**
 * The fake games' play UIs plus the registries over them (registry order = [ids]); the
 * [hintsRanked] ones rank their bests hints first, as Strands does.
 */
class FakeGames(ids: List<GameId>, hintsRanked: Set<GameId> = emptySet()) {
    val uis: Map<GameId, FakePlayUi> = ids.associateWith(::FakePlayUi)
    val registry = GamePlayRegistry(uis.values.toList())
    val games = GameRegistry(ids.map { if (it in hintsRanked) hintsFirst(TestGames.plugin(it)) else TestGames.plugin(it) })

    operator fun get(id: GameId): FakePlayUi = uis.getValue(id)

    private fun <P> hintsFirst(
        plugin: GamePlugin<P, TestGames.Session, TestGames.Model>,
    ): GamePlugin<P, TestGames.Session, TestGames.Model> = GamePlugin(
        definition = plugin.definition,
        rules = plugin.rules,
        session = plugin.session,
        score = object : ScoreModel<TestGames.Session> {
            override fun completion(session: TestGames.Session) = plugin.score.completion(session)
            override val rankOrder = RankOrder.HintsThenTime
        },
        stateOf = plugin.stateOf,
        initialSession = plugin.initialSession,
        refOf = plugin.refOf,
        replaySession = plugin.replaySession,
        rendererModel = plugin.rendererModel,
    )
}
