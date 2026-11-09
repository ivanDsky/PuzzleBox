package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.DAILY_MATCH_CAP
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LocalMatch
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Creating a duel (ticket 27): the puzzles are drawn once, the code is free as a puzzle and as a match, the
 * document is written once and the duel remembered as mine; every failure comes back as its own outcome.
 */
class CreateMatchTest {

    private val today = LocalDate.of(2026, 11, 9)
    private val gamma = GameId("gamma")
    private val registry = TestGames.registry(listOf(alpha, beta, gamma))
    private val daily = FakeDailyRepository().apply {
        listOf(alpha, beta).forEach { game ->
            setArchiveRange(game, today.minusDays(29)..today)
            setPuzzleNumbers(game) { day -> (day.toEpochDay() % 10_000).toInt() + 1 }
        }
    }
    private val received = FakeReceivedPuzzleRepository()
    private val history = ObserveGameHistory(registry, daily, FakeCompletionRepository())
    private val identity = FakeIdentityService(ANN_IDENTITY)
    private val server = FakeMatchRepository().apply { signedInUid = ANN.uid }
    private val repo = ScriptedMatchRepository(server)
    private val local = FakeMatchLocalRepository()
    private val create = createMatch()

    private fun drawer() = ComputeMatchLegs(registry, daily, FakeGameCalendar(today), history, received, Random(DRAW_SEED))

    private fun createMatch(repository: MatchRepository = repo) =
        CreateMatch(repository, local, drawer(), identity, Random(CODE_SEED))

    /** The codes a fresh [createMatch] tries, in order. */
    private fun codes(count: Int): List<ShareCode> = Random(CODE_SEED).let { random -> List(count) { ShareCode.random(random) } }

    private fun settings(
        count: Int = 3,
        games: Set<GameId> = setOf(alpha, beta),
        todayOnly: Boolean = false,
        includeFriends: Boolean = true,
        pasted: List<ShareCode> = emptyList(),
    ) = DuelSettings(count, games, todayOnly, deadlineHours = 48, includeFriends, pasted)

    private suspend fun rows(): List<LocalMatch> = local.observeAll().first()

    private suspend fun friendPuzzle(code: ShareCode) = received.upsert(
        ReceivedPuzzle(
            code = code, gameId = alpha, variant = TestGames.variant(alpha), sharedFormatVersion = 1,
            puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "t", authorName = BO.name, note = "",
            authorTimeMs = 1_000, authorHints = 0, authorUid = BO.uid, openedAt = Instant.EPOCH,
            unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
        ),
    )

    private fun oldMatch(challenger: Player, opponent: Player) = Match(
        code = OLD_CODE, formatVersion = Match.FORMAT_VERSION, createdAt = Instant.EPOCH, deadlineHours = 24,
        challenger = challenger, invitedUid = null, opponent = opponent, legs = emptyList(), includeFriends = false,
        cancelled = false, declined = false, rematchOf = null, progress = emptyMap(),
    )

    @Test
    fun aSuccessfulCreateStoresTheDraftAndRemembersItAsParticipant() = runTest {
        val settings = settings()

        val created = create(settings) as CreateOutcome.Created

        val match = server.matches.getValue(created.code)
        assertEquals(ANN, match.challenger)
        assertEquals(settings.deadlineHours, match.deadlineHours)
        assertEquals(settings.count, created.legs.size)
        assertEquals(created.legs, match.legs)
        assertTrue(match.includeFriends)
        assertNull(match.invitedUid)
        assertNull(match.rematchOf)
        val row = rows().single()
        assertEquals(created.code, row.code)
        assertEquals(LocalRole.Participant, row.role)
        assertNull(row.opponentName)
    }

    @Test
    fun theSettingsShapeTheDraw() = runTest {
        val pasted = ShareCode.parse("P4STED")!!
        val unpasted = ShareCode.parse("FR1END")!!
        friendPuzzle(pasted)
        friendPuzzle(unpasted)

        val created = create(settings(count = 5, todayOnly = true, includeFriends = false, pasted = listOf(pasted)))
            as CreateOutcome.Created

        assertEquals(LegRef.Custom(alpha, pasted), created.legs.first().ref)
        val dailies = created.legs.drop(1).map { it.ref }
        assertEquals(setOf(alpha, beta), dailies.filterIsInstance<LegRef.Daily>().map { it.gameId }.toSet())
        assertTrue(dailies.filterIsInstance<LegRef.Daily>().all { it.day == today })
        assertEquals(3, created.legs.size)
    }

    @Test
    fun aTakenPuzzleCodeIsSkippedBeforeCreate() = runTest {
        val (first, second) = codes(2)
        server.occupyPuzzleCode(first)

        val created = create(settings()) as CreateOutcome.Created

        assertEquals(second, created.code)
        assertEquals(listOf(first, second), repo.checked)
        assertEquals(listOf(second), repo.drafts.map { it.code })
    }

    @Test
    fun aTakenMatchCodeIsRetriedWithTheSameLegs() = runTest {
        repo.createAnswer = { draft -> if (repo.drafts.size == 1) CreateResult.CodeTaken else server.create(draft) }
        val reference = drawer()
        val request = DrawRequest(3, setOf(alpha, beta), todayOnly = false, includeFriends = true, emptyList(), me = ANN.uid)
        val firstDraw = reference(request)
        assertNotEquals("a second draw must differ for this test to see one", firstDraw, reference(request))

        val created = create(settings()) as CreateOutcome.Created

        assertEquals(codes(2), repo.drafts.map { it.code })
        assertEquals(listOf(firstDraw, firstDraw), repo.drafts.map { it.legs })
        assertEquals(firstDraw, created.legs)
        assertEquals(codes(2)[1], created.code)
    }

    @Test
    fun retriesAreBoundedAndEndInFailed() = runTest {
        repo.createAnswer = { CreateResult.CodeTaken }

        assertEquals(CreateOutcome.Failed(null), create(settings()))
        assertTrue(repo.drafts.size > 1)

        val crowded = ScriptedMatchRepository(server).apply { codeCheck = { true } }
        assertEquals(CreateOutcome.Failed(null), createMatch(crowded)(settings()))
        assertTrue(crowded.checked.size > 1)
        assertTrue(crowded.drafts.isEmpty())
        assertTrue(server.matches.isEmpty())
        assertTrue(rows().isEmpty())
    }

    @Test
    fun anOwnExistingDocCountsAsCreated() = runTest {
        repo.codeCheck = { false } // a check that ran before the first write was visible

        val first = createMatch()(settings()) as CreateOutcome.Created
        val again = createMatch()(settings()) as CreateOutcome.Created

        assertEquals(first, again)
        assertEquals(setOf(first.code), server.matches.keys)
        assertEquals(listOf(first.code), rows().map { it.code })
    }

    @Test
    fun offlineAtTheCodeCheckAndAtCreateAreOffline() = runTest {
        server.online = false
        assertEquals(CreateOutcome.Offline, create(settings()))
        assertTrue(repo.drafts.isEmpty())

        server.online = true
        repo.createAnswer = { CreateResult.Offline }
        assertEquals(CreateOutcome.Offline, createMatch()(settings()))
        assertEquals(1, repo.drafts.size)
        assertTrue(rows().isEmpty())
    }

    @Test
    fun theDailyCapIsItsOwnOutcome() = runTest {
        repeat(DAILY_MATCH_CAP) { assertTrue(create(settings()) is CreateOutcome.Created) }

        assertEquals(CreateOutcome.DailyCapReached, create(settings()))
        assertEquals(DAILY_MATCH_CAP, server.matches.size)
        assertEquals(DAILY_MATCH_CAP, rows().size)
    }

    @Test
    fun signedOutWritesNothing() = runTest {
        identity.currentIdentity = null
        assertEquals(CreateOutcome.SignedOut, create(settings()))
        assertTrue(repo.checked.isEmpty())
        assertTrue(repo.drafts.isEmpty())

        identity.currentIdentity = ANN_IDENTITY
        server.signedInUid = null
        assertEquals(CreateOutcome.SignedOut, createMatch()(settings()))
        assertTrue(server.matches.isEmpty())
        assertTrue(rows().isEmpty())
    }

    @Test
    fun anEmptyDrawIsNoPuzzles() = runTest {
        friendPuzzle(ShareCode.parse("FR1END")!!)

        assertEquals(CreateOutcome.NoPuzzles, create(settings(games = setOf(gamma))))
        assertTrue(repo.checked.isEmpty())
        assertTrue(rows().isEmpty())
    }

    @Test
    fun aRejectedCreateCarriesItsMessage() = runTest {
        repo.createAnswer = { CreateResult.Rejected("permission denied") }

        assertEquals(CreateOutcome.Failed("permission denied"), create(settings()))
        assertTrue(rows().isEmpty())
    }

    @Test
    fun aRematchInvitesTheOtherPlayerAndLinksTheOldCode() = runTest {
        for (old in listOf(oldMatch(challenger = ANN, opponent = BO), oldMatch(challenger = BO, opponent = ANN))) {
            val created = createMatch()(settings(), rematchOf = old) as CreateOutcome.Created

            val match = server.matches.getValue(created.code)
            assertEquals(ANN, match.challenger)
            assertEquals(BO.uid, match.invitedUid)
            assertEquals(OLD_CODE, match.rematchOf)
            assertEquals(BO.name, rows().single { it.code == created.code }.opponentName)
            server.signedInUid = BO.uid
            assertTrue(created.code in server.observeMine().first()!!.map { it.code })
            server.signedInUid = ANN.uid
        }
    }

    @Test
    fun theChallengerNameIsTrimmedCutAndNeverBlank() = runTest {
        val long = "x".repeat(80)
        identity.currentIdentity = ANN_IDENTITY.copy(displayName = "  $long  ")
        val cut = createMatch()(settings()) as CreateOutcome.Created
        assertEquals(long.take(64), server.matches.getValue(cut.code).challenger.name)

        identity.currentIdentity = ANN_IDENTITY.copy(displayName = " \t ")
        val blank = create(settings()) as CreateOutcome.Created
        assertEquals("Player", server.matches.getValue(blank.code).challenger.name)
    }

    /** The real fake behind a recorder; [codeCheck] and [createAnswer] script the answers. */
    private class ScriptedMatchRepository(private val server: FakeMatchRepository) : MatchRepository by server {
        val checked = mutableListOf<ShareCode>()
        val drafts = mutableListOf<MatchDraft>()
        var codeCheck: suspend (ShareCode) -> Boolean? = { server.puzzleCodeTaken(it) }
        var createAnswer: suspend (MatchDraft) -> CreateResult = { server.create(it) }

        override suspend fun puzzleCodeTaken(code: ShareCode): Boolean? {
            checked += code
            return codeCheck(code)
        }

        override suspend fun create(draft: MatchDraft): CreateResult {
            drafts += draft
            return createAnswer(draft)
        }
    }

    private companion object {
        const val CODE_SEED = 7
        const val DRAW_SEED = 3
        val ANN_IDENTITY = Identity(uid = "ann", displayName = "Ann", photoUrl = "https://example.com/ann.png")
        val ANN = Player(ANN_IDENTITY.uid, ANN_IDENTITY.displayName, ANN_IDENTITY.photoUrl)
        val BO = Player("bo", "Bo", null)
        val OLD_CODE = ShareCode.parse("0LDC0D")!!
    }
}
