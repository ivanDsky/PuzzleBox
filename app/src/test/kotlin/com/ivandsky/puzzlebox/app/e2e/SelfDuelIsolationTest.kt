package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.di.MatchRemote
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.MatchResultPoster
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.data.match.WriteResult
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.PostResult
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.domain.DeleteSelfDuel
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.LegFinish
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.RecordRevealSeen
import com.ivandsky.puzzlebox.domain.SelfDuelSettings
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One differential run: with one friend duel, three solved Zip dailies and the daily board
 * already fed, a signed-in player creates two self duels, plays and reveals one and removes the other. Everything
 * that is not a self duel reads exactly as before: Today, History and stats, the daily board's submissions, the
 * friends list, the friend-duel server (not one write), and the Duels badge (spec A1, A12).
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class SelfDuelIsolationTest {

    private val writes = AtomicInteger()
    private val graph: E2eKoinGraph = E2eKoinGraph(
        E2E_TODAY,
        overrides = module { single<MatchRepository>(MatchRemote) { CountingMatchRepository(graph.matches, writes) } },
        releaseFlags = FakeReleaseFlags(ReleaseFlag.VirtualDuel),
    )

    @Before
    fun start() = graph.start()

    @After
    fun stop() = graph.stop()

    @Test
    fun aWholeSelfDuelLeavesEveryFriendDailyBoardAndServerReadAsItWas() = runBlocking {
        graph.identity.currentIdentity = Identity(uid = ME, displayName = "Me", photoUrl = null)
        graph.matches.now = Instant.now()
        graph.matches.signedInUid = ME
        graph.seedSolvedZips(E2E_TODAY)
        friendDuelClaimedBySam()
        graph.koin.get<LeaderboardSubmitWatcher>().start()
        graph.koin.get<MatchResultPoster>().start()
        // The one in-window daily (yesterday's) reaches the board before the snapshot, so a leak cannot hide as a late submit.
        eventually { graph.leaderboard.submitted.isNotEmpty() }
        val before = snapshot()
        assertEquals("the friend duel counts, so the badge and friends are not trivially empty", setOf(SAM), before.friends)
        assertEquals(1, before.needYou)

        val kept = createSelfDuel()
        val removed = createSelfDuel()
        playEveryLeg(kept)
        graph.koin.get<RecordRevealSeen>()(kept, myScore = 1, theirScore = 0)
        graph.koin.get<DeleteSelfDuel>()(removed)

        val self = graph.koin.get<SelfMatchRepository>()
        assertEquals("the kept duel is on the device, the removed one is gone", listOf(kept), self.observeAll().first().map { it.code })
        assertEquals("Past you is not a friend, and the self duels reached the server not once", before, snapshot())
    }

    private suspend fun createSelfDuel(): ShareCode {
        val created = graph.koin.get<CreateSelfDuel>()(SelfDuelSettings(rounds = SOLVED_ZIPS, games = setOf(ZipDefinition.id), includeFriends = false))
        return (created as CreateOutcome.Created).code
    }

    /** Finishes every leg of [code] through the real use case and waits until the poster settled each one. */
    private suspend fun playEveryLeg(code: ShareCode) {
        val finish = graph.koin.get<FinishLeg>()
        repeat(SOLVED_ZIPS) { leg -> finish(code, leg, LegFinish.Solved(timeMs = FIRST_SOLVE_MS - 1_000L, hints = 0)) }
        val outbox = graph.koin.get<MatchResultOutbox>()
        eventually { outbox.observe(code).first().count { it.state == ResultPostState.Posted } == SOLVED_ZIPS }
    }

    /** Sam claims the seat of a duel I made: one Your move row, and Sam is my friend. */
    private suspend fun friendDuelClaimedBySam() {
        val code = checkNotNull(ShareCode.parse("K7Q2MX"))
        val leg = MatchLeg(LegRef.Daily(ZipDefinition.id, E2E_TODAY, 592), authorUid = null)
        val draft = MatchDraft(code, Match.DEADLINE_CHOICES.first(), Player(ME, "Me", null), null, listOf(leg), false, null)
        check(graph.matches.create(draft) is CreateResult.Created) { "the friend duel was not created" }
        graph.matches.signedInUid = SAM
        check(graph.matches.claim(code, Player(SAM, "Sam", null)) == ClaimResult.Claimed) { "Sam's claim failed" }
        graph.matches.signedInUid = ME
        writes.set(0)
    }

    private suspend fun snapshot(): Snapshot {
        val stats = graph.koin.get<ObserveGameStats>()
        return Snapshot(
            today = graph.koin.get<ObserveTodaySet>()().first(),
            history = graph.koin.get<ObserveGameHistory>()().first(),
            stats = listOf(null, ZipDefinition.id).associateWith { stats(it).first() },
            submitted = graph.leaderboard.submitted.toList(),
            friends = graph.koin.get<ObserveFriendUids>()().first(),
            serverMatches = graph.matches.matches.keys,
            serverWrites = writes.get(),
            needYou = graph.koin.get<ObserveMyDuels>()().first().needYou,
        )
    }

    private data class Snapshot(
        val today: List<TodayEntry>,
        val history: List<GameHistoryDay>,
        val stats: Map<GameId?, GameStats>,
        val submitted: List<LeaderboardEntry>,
        val friends: Set<String>,
        val serverMatches: Set<ShareCode>,
        val serverWrites: Int,
        val needYou: Int,
    )

    private suspend fun eventually(condition: suspend () -> Boolean) = withTimeout(TIMEOUT_MS) {
        while (!condition()) delay(POLL_MS)
    }

    /** The friend-duel server fake, counting every write call (reads and sealed-result reads pass through). */
    private class CountingMatchRepository(private val inner: MatchRepository, private val writes: AtomicInteger) : MatchRepository by inner {
        override suspend fun create(draft: MatchDraft): CreateResult = inner.create(draft).also { writes.incrementAndGet() }
        override suspend fun claim(code: ShareCode, me: Player): ClaimResult = inner.claim(code, me).also { writes.incrementAndGet() }
        override suspend fun cancel(code: ShareCode): WriteResult = inner.cancel(code).also { writes.incrementAndGet() }
        override suspend fun decline(code: ShareCode): WriteResult = inner.decline(code).also { writes.incrementAndGet() }
        override suspend fun post(code: ShareCode, result: LegResult): PostResult = inner.post(code, result).also { writes.incrementAndGet() }
    }

    private companion object {
        const val ME = "me"
        const val SAM = "sam"
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 20L
    }
}
