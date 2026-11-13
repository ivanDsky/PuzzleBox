package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.LocalMatch
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.LoadCustomReplay
import com.ivandsky.puzzlebox.domain.LoadDailyReplay
import com.ivandsky.puzzlebox.domain.LoadLegReplay
import com.ivandsky.puzzlebox.domain.LoadPlayerReplay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.domain.RecordRevealSeen
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.FixtureSession
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyReplayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * The reveal's state holder over a hand-fed match: Ann is the viewer, Bob the opponent, two puzzles. The race is
 * fixed at the first load with a settled puzzle; the summary follows every later load.
 */
class RevealViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)
    private val createdAt = Instant.parse("2026-11-09T10:00:00Z")
    private val day = LocalDate.of(2026, 11, 9)
    private val registry = GameRegistry(
        listOf(TestGames.plugin(TestGames.alpha), TestGames.plugin(TestGames.beta), fixturePlugin()),
    )
    private val server = FakeMatchRepository()
    private var theirsOnline = true
    private val gates = HashMap<Int, CompletableDeferred<Unit>>()

    /** The match store as the leg resolver sees it: my side rides the cache, their read needs the network. */
    private val matchStore = object : MatchRepository by server {
        override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead {
            gates[legIndex]?.await()
            return if (theirsOnline) server.readResult(code, legIndex, uid) else ResultRead.Offline
        }
    }
    private val outbox = FakeMatchResultOutbox()
    private val identity = FakeIdentityService()
    private val daily = FakeDailyRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val solved = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES)
    private val gaveUp = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES, outcome = ReplayOutcome.GaveUp)
    private val match = Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = createdAt,
        deadlineHours = 24,
        challenger = ann,
        invitedUid = null,
        opponent = bob,
        legs = listOf(
            MatchLeg(LegRef.Daily(TestGames.alpha, day, 1), authorUid = null),
            MatchLeg(LegRef.Daily(TestGames.beta, day, 2), authorUid = null),
        ),
        includeFriends = false,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = emptyMap(),
    )
    private val matches = MutableStateFlow<MatchState?>(null)
    private val local = FakeMatchLocalRepository()
    private var marks = 0
    private val counted = object : MatchLocalRepository by local {
        override suspend fun markRevealSeen(code: ShareCode, at: Instant, myScore: Int, theirScore: Int) {
            marks++
            local.markRevealSeen(code, at, myScore, theirScore)
        }
    }

    private fun loadReplay(flags: FakeReleaseFlags = FakeReleaseFlags(ReleaseFlag.FullReplay)): LoadSolveReplay {
        val completions = FakeCompletionRepository()
        val loadShared = LoadSharedPuzzle(
            registry = registry,
            shared = FakeSharedPuzzleRepository(),
            received = received,
            budget = { Budget.within(1_000, System::nanoTime) },
            clock = { Instant.EPOCH },
        )
        return LoadSolveReplay(
            flags = flags,
            games = registry,
            daily = LoadDailyReplay(completions, daily),
            custom = LoadCustomReplay(completions, received, loadShared),
            leg = LoadLegReplay(matchStore, outbox, identity, daily, received, loadShared),
            player = LoadPlayerReplay(FakeDailyReplayRepository(), daily),
            completions = completions,
            dispatchers = InlineDispatcherProvider,
        )
    }

    private fun viewModel(load: LoadSolveReplay = loadReplay()) = RevealViewModel(
        code = code.value,
        reducedMotion = false,
        zone = ZoneOffset.UTC,
        locale = Locale.UK,
        observeMatch = { requested -> matches.filterNotNull().also { check(requested == code) } },
        registry = registry,
        brands = GamePlayRegistry(emptyList()),
        recordSeen = RecordRevealSeen(counted) { createdAt.plusSeconds(3_600) },
        loadReplay = load,
    )

    private fun TestScope.started(vm: RevealViewModel): RevealViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return vm
    }

    private fun mine(leg: Int, timeMs: Long) =
        OutboxResult(code, LegResult("ann", leg, timeMs, 0, completedAtMs = 1, gaveUp = false), ResultPostState.Posted, attempts = 0)

    private fun theirs(leg: Int, timeMs: Long) = LegSeen.Revealed(LegResult("bob", leg, timeMs, 0, completedAtMs = 1, gaveUp = false))

    /** Both of my times posted; Bob's [revealed] puzzles read, the rest only played; [seenAt] is this device's marker. */
    private fun loaded(
        revealed: Set<Int>,
        seenAt: Instant? = null,
        of: Match = match,
        won: (Int) -> LegVerdict = { LegVerdict.Win(LegBasis.Time) },
    ): MatchState {
        val theirs = listOf(0, 1).map { if (it in revealed) theirs(it, 72_000) else LegSeen.Played }
        val verdicts = listOf(0, 1).map { if (it in revealed) won(it) else LegVerdict.Pending }
        val wins = verdicts.count { it is LegVerdict.Win }
        val view = MatchView(
            match = of,
            me = "ann",
            mine = listOf(mine(0, 58_000), mine(1, 61_000)),
            theirs = theirs,
            outcome = Outcome(verdicts, wins, 0, settled = wins == 2, verdict = null),
            local = LocalMatch(code, LocalRole.Participant, "Bob", createdAt, seenAt, dismissed = false),
        )
        return MatchState.Loaded(view)
    }

    private fun running(vm: RevealViewModel) = vm.state.value as RevealUiState.Running

    @Test
    fun aSummaryFollowsAResultThatArrivesAfterTheRevealOpened() = runTest {
        val vm = started(viewModel())
        matches.value = loaded(revealed = setOf(0))
        val opened = running(vm)

        matches.value = loaded(revealed = setOf(0, 1))

        val now = running(vm)
        assertEquals("the summary counts the puzzle settled later", 2, now.summary.myScore)
        assertEquals("the summary lists both puzzles", listOf("Alpha #1", "Beta #2"), now.summary.rows.map { it.title })
        assertSame("the race being watched keeps its cards", opened.race, now.race)
        assertEquals("the race holds only the puzzle settled when it opened", 1, now.race.cards.size)
    }

    @Test
    fun aReEmissionNeverRestartsTheRaceOrMarksTheRevealSeenTwice() = runTest {
        val vm = started(viewModel())
        matches.value = loaded(revealed = setOf(0, 1))
        val opened = running(vm)
        assertFalse("a first opening plays the race", opened.start.startAtEnd)
        assertEquals("a first opening records that it was seen", 1, marks)

        matches.value = loaded(revealed = setOf(0, 1), seenAt = createdAt.plusSeconds(3_600))
        matches.value = MatchState.Offline
        matches.value = loaded(revealed = setOf(0, 1), seenAt = createdAt.plusSeconds(3_600))

        val now = running(vm)
        assertSame("the race is the one that opened", opened.race, now.race)
        assertEquals("the opening still plays from the start", opened.start, now.start)
        assertEquals("no replay was asked for", opened.rerun, now.rerun)
        assertEquals("the marker is written once", 1, marks)
    }

    @Test
    fun anEmptyFirstRunBecomesTheRaceOnceTheResultsArrive() = runTest {
        local.remember(code, LocalRole.Participant, "Bob")
        val vm = started(viewModel())
        matches.value = loaded(revealed = emptySet())
        assertEquals("nothing settled yet", RevealUiState.Unsettled, vm.state.value)
        assertEquals("an empty reveal is not a seen one", 0, marks)

        matches.value = loaded(revealed = setOf(0, 1))

        val now = running(vm)
        assertEquals("the race holds both puzzles", 2, now.race.cards.size)
        assertFalse("the race plays from the start", now.start.startAtEnd)
        assertEquals("the reveal that ran is recorded as seen", 1, marks)
        val stored = local.observeAll().first().single()
        assertEquals("the stamp carries my score", 2, stored.myScore)
        assertEquals("the stamp carries their score", 0, stored.theirScore)
    }

    @Test
    fun watchAgainRacesEveryPuzzleSettledSince() = runTest {
        val vm = started(viewModel())
        matches.value = loaded(revealed = setOf(0))
        matches.value = loaded(revealed = setOf(0, 1))

        vm.watchAgain()

        val again = running(vm)
        assertEquals("the new race holds both puzzles", 2, again.race.cards.size)
        assertEquals("it is the first replay", 1, again.rerun)
        assertEquals("a replay records nothing new", 1, marks)
    }

    // The compare: a duel over the fixture game, so the replays fold. Bob and Ann each played both legs.

    private val compareMatch = match.copy(
        legs = listOf(
            MatchLeg(LegRef.Daily(ReplayFixtures.GAME, day, 1), authorUid = null),
            MatchLeg(LegRef.Daily(ReplayFixtures.GAME, day.plusDays(1), 2), authorUid = null),
        ),
    )

    private suspend fun duel() {
        daily.setCached(ReplayFixtures.GAME, day, puzzle(ReplayFixtures.SHORT_MOVES))
        daily.setCached(ReplayFixtures.GAME, day.plusDays(1), puzzle(ReplayFixtures.SHORT_MOVES + 1))
        server.signedInUid = ann.uid
        server.create(MatchDraft(code, 24, ann, null, compareMatch.legs, includeFriends = false, rematchOf = null))
        server.signedInUid = bob.uid
        server.claim(code, bob)
        identity.currentIdentity = Identity(ann.uid, ann.name, null)
        server.signedInUid = ann.uid
    }

    private suspend fun play(leg: Int, mine: SolveReplay?, theirs: SolveReplay?) {
        server.signedInUid = bob.uid
        server.post(code, legResult(bob.uid, leg, theirs))
        server.signedInUid = ann.uid
        server.post(code, legResult(ann.uid, leg, null))
        if (mine != null) outbox.enqueue(code, legResult(ann.uid, leg, mine))
    }

    private fun legResult(uid: String, leg: Int, replay: SolveReplay?) =
        LegResult(uid, leg, 12_000, 0, completedAtMs = 1_000, gaveUp = false, replay = replay)

    private fun puzzle(pieces: Int) = Puzzle(ReplayFixtures.GAME, VariantId("fixture/v1"), 1, byteArrayOf(pieces.toByte()))

    private fun TestScope.compared(won: (Int) -> LegVerdict = { LegVerdict.Win(LegBasis.Time) }): RevealViewModel {
        val vm = started(viewModel())
        matches.value = loaded(revealed = setOf(0, 1), of = compareMatch, won = won)
        return vm
    }

    @Test
    fun noLegOffersACompareBeforeTheRevealRan() = runTest {
        duel()
        play(0, solved, solved)
        val vm = compared()

        assertEquals("nothing to compare yet", emptySet<Int>(), running(vm).compareLegs)
        vm.openCompare(0)
        assertNull("an open before the end is refused", vm.compare.value)
    }

    @Test
    fun everySettledLegOffersACompareOnceTheRevealRan() = runTest {
        duel()
        play(0, solved, solved)
        val vm = compared()

        vm.revealEnded()

        assertEquals(setOf(0, 1), running(vm).compareLegs)
    }

    @Test
    fun aLegWithBothReplaysOpensWithBothTimelines() = runTest {
        duel()
        play(0, solved, gaveUp)
        val vm = compared()
        vm.revealEnded()

        vm.openCompare(0)

        val open = vm.compare.value!!
        assertEquals(0, open.leg)
        assertNotNull("my side", open.mine)
        assertNotNull("their side", open.theirs)
        assertEquals("Bob", open.opponent)
    }

    @Test
    fun aLegWithOnlyMyReplayOpensWithMineAlone() = runTest {
        duel()
        play(0, solved, null)
        val vm = compared()
        vm.revealEnded()

        vm.openCompare(0)

        val open = vm.compare.value!!
        assertNotNull(open.mine)
        assertNull(open.theirs)
    }

    @Test
    fun aLegWithOnlyTheirReplayOpensWithTheirsAlone() = runTest {
        duel()
        play(0, null, solved)
        val vm = compared()
        vm.revealEnded()

        vm.openCompare(0)

        val open = vm.compare.value!!
        assertNull(open.mine)
        assertNotNull(open.theirs)
    }

    @Test
    fun theirSideIsReadOnlineAtTheMomentOfOpening() = runTest {
        duel()
        play(0, solved, solved)
        val vm = compared()
        vm.revealEnded()
        theirsOnline = false

        vm.openCompare(0)

        val open = vm.compare.value!!
        assertNotNull(open.mine)
        assertNull("offline, theirs is not cached", open.theirs)
    }

    @Test
    fun aLegWithNeitherReplayCannotBeOpened() = runTest {
        duel()
        play(0, null, null)
        val vm = compared()
        vm.revealEnded()

        vm.openCompare(0)

        assertNull(vm.compare.value)
        assertEquals("the leg stops being offered", setOf(1), running(vm).compareLegs)
    }

    @Test
    fun theFinalLeadIsTheLegsVerdict() = runTest {
        duel()
        play(0, solved, solved)
        play(1, gaveUp, gaveUp)
        val vm = compared { if (it == 0) LegVerdict.Win(LegBasis.Time) else LegVerdict.Loss(LegBasis.Time) }
        vm.revealEnded()

        vm.openCompare(0)
        assertEquals(Lead.Mine, vm.compare.value!!.finalLead)
        vm.openCompare(1)
        assertEquals(Lead.Theirs, vm.compare.value!!.finalLead)
    }

    @Test
    fun aDrawIsLevel() = runTest {
        duel()
        play(0, solved, solved)
        val vm = compared { LegVerdict.Draw }
        vm.revealEnded()

        vm.openCompare(0)

        assertEquals(Lead.Level, vm.compare.value!!.finalLead)
    }

    @Test
    fun closingTheCompareDropsBothTimelines() = runTest {
        duel()
        play(0, solved, solved)
        val vm = compared()
        vm.revealEnded()
        vm.openCompare(0)

        vm.closeCompare()

        assertNull(vm.compare.value)
    }

    @Test
    fun aRewatchHidesTheCompareAgainUntilItEnds() = runTest {
        duel()
        play(0, solved, solved)
        val vm = compared()
        vm.revealEnded()
        vm.openCompare(0)
        assertEquals(setOf(0, 1), running(vm).compareLegs)

        vm.watchAgain()

        assertEquals("no spoiler while the rewatch runs", emptySet<Int>(), running(vm).compareLegs)
        assertNull("an open compare is dropped", vm.compare.value)
        vm.openCompare(0)
        assertNull(vm.compare.value)
        vm.revealEnded()
        assertEquals(setOf(0, 1), running(vm).compareLegs)
    }

    @Test
    fun openingAnotherLegNeverLeavesTheFirstOnShow() = runTest {
        duel()
        play(0, solved, solved)
        play(1, null, null)
        val vm = compared()
        vm.revealEnded()
        vm.openCompare(0)

        vm.openCompare(1)

        assertNull("leg 1 has no replay and leg 0's compare is gone", vm.compare.value)
    }

    @Test
    fun theLastOpenedLegWinsOverASlowerEarlierOne() = runTest {
        duel()
        play(0, solved, solved)
        play(1, gaveUp, gaveUp)
        val vm = compared()
        vm.revealEnded()
        val slow = CompletableDeferred<Unit>().also { gates[0] = it }

        vm.openCompare(0)
        vm.openCompare(1)
        slow.complete(Unit)

        assertEquals(1, vm.compare.value!!.leg)
    }

    @Test
    fun closingWhileLoadingLeavesNoCompare() = runTest {
        duel()
        play(0, solved, solved)
        val vm = compared()
        vm.revealEnded()
        val slow = CompletableDeferred<Unit>().also { gates[0] = it }

        vm.openCompare(0)
        vm.closeCompare()
        slow.complete(Unit)

        assertNull(vm.compare.value)
    }

    @Test
    fun withTheFlagOffNoLegCanBeOpened() = runTest {
        duel()
        play(0, solved, solved)
        val vm = started(viewModel(loadReplay(FakeReleaseFlags())))
        matches.value = loaded(revealed = setOf(0, 1), of = compareMatch)
        vm.revealEnded()

        vm.openCompare(0)

        assertNull(vm.compare.value)
        assertFalse("the leg is not offered again", 0 in running(vm).compareLegs)
    }

    private object InlineDispatcherProvider : DispatcherProvider {
        private val inline = object : CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext) = false
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        }
        override val main: CoroutineDispatcher = inline
        override val io: CoroutineDispatcher = inline
        override val default: CoroutineDispatcher = inline
    }
}

/** The fixture game's plugin; a puzzle's one payload byte is its piece count. */
private fun fixturePlugin(): GamePlugin<Unit, FixtureSession, TestGames.Model> = GamePlugin(
    definition = TestGames.definition(ReplayFixtures.GAME),
    rules = object : GameRules<Unit> {
        override fun validate(move: Move, state: Unit): MoveVerdict = object : MoveVerdict {}
        override fun isComplete(state: Unit) = false
        override fun hint(state: Unit): Hint = object : Hint {}
    },
    session = object : GameSessionCodec<FixtureSession> {
        override fun serialize(session: FixtureSession) = ByteArray(0)
        override fun deserialize(bytes: ByteArray): DecodeResult<FixtureSession> =
            DecodeResult.Ok(ReplayFixtures.initial(0))
    },
    score = object : ScoreModel<FixtureSession> {
        override fun completion(session: FixtureSession): CompletionRecord? = null
    },
    stateOf = { },
    initialSession = { DecodeResult.Ok(ReplayFixtures.initial(it.payload.single().toInt())) },
    refOf = { TestGames.Ref(ReplayFixtures.GAME, it) },
    playAgainSession = { _, _ -> DecodeResult.Ok(ByteArray(0)) },
    rendererModel = { _, _, _ -> TestGames.Model },
    replay = ReplayFixtures.replay,
)
