package com.ivandsky.puzzlebox.play.friend

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The Enter code sheet's lookup order over the real [LoadSharedPuzzle]: a puzzle check first,
 * then (with duels on) one read of the match, each outcome mapped to the sheet's state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnterCodeViewModelTest {

    private val code = requireNotNull(ShareCode.parse("K7Q2MX"))
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }
    private val received = FakeReceivedPuzzleRepository()
    private val server = FakeMatchRepository().apply { signedInUid = AUTHOR }
    private val matches = CountingMatchRepository(server)
    private val inline = object : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
    }
    private val load = LoadSharedPuzzle(
        registry = FakeGames(listOf(TestGames.alpha)).games,
        shared = shared,
        received = received,
        budget = { Budget.within(1_000L, System::nanoTime) },
        clock = { Instant.EPOCH },
    )
    private val viewModel by lazy { EnterCodeViewModel(load, matches, inline) }

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun aPuzzleCodeOpensWithoutAskingForADuel() {
        runBlocking { received.upsert(storedPuzzle()) }

        viewModel.open(code, tryMatch = true)

        assertEquals(CodeCheck.Opened(code), viewModel.state.value)
        assertEquals(0, matches.observed)
    }

    @Test
    fun anUnknownPuzzleCodeThatIsADuelOpensTheDuel() {
        runBlocking { createDuel() }

        viewModel.open(code, tryMatch = true)

        assertEquals(CodeCheck.MatchOpened(code), viewModel.state.value)
    }

    @Test
    fun aCodeThatIsNeitherShowsNotFound() {
        viewModel.open(code, tryMatch = true)

        assertEquals(CodeCheck.Failed(code, OpenOutcome.NotFound), viewModel.state.value)
    }

    @Test
    fun aMissingDuelLookupWhenOfflineFails() {
        matches.fetch = MatchFetch.Offline

        viewModel.open(code, tryMatch = true)

        assertEquals(CodeCheck.Failed(code, OpenOutcome.Offline), viewModel.state.value)
    }

    @Test
    fun aDuelFromANewerAppAsksForAnUpdate() {
        matches.fetch = MatchFetch.UnsupportedVersion(found = 7)

        viewModel.open(code, tryMatch = true)

        assertEquals(CodeCheck.Failed(code, OpenOutcome.NeedsUpdate(7)), viewModel.state.value)
    }

    @Test
    fun withDuelsOffTheMatchRepositoryIsNeverAsked() {
        runBlocking { createDuel() }

        viewModel.open(code, tryMatch = false)

        assertEquals(CodeCheck.Failed(code, OpenOutcome.NotFound), viewModel.state.value)
        assertEquals(0, matches.observed)
    }

    @Test
    fun aPuzzleOutcomeOtherThanNotFoundDoesNotTryADuel() {
        runBlocking {
            shared.publish(sharedDoc())
            shared.unpublish(code)
            createDuel()
        }

        viewModel.open(code, tryMatch = true)

        assertEquals(CodeCheck.Failed(code, OpenOutcome.NoLongerAvailable), viewModel.state.value)
        assertEquals(0, matches.observed)
    }

    private suspend fun createDuel() {
        val ann = Player(AUTHOR, "Ann", null)
        val legs = listOf(MatchLeg(LegRef.Daily(GameId("zip"), LocalDate.of(2026, 11, 1), 1), authorUid = null))
        val draft = MatchDraft(code, 24, ann, invitedUid = null, legs, includeFriends = false, rematchOf = null)
        assertEquals(CreateResult.Created(code), server.create(draft))
    }

    private fun sharedDoc() = SharedPuzzleDoc(
        code = code, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = TestGames.alpha.value, variant = "alpha/n8",
        shareCodecVersion = 1, payload = byteArrayOf(1, 2, 3), title = "Crown the castle",
        authorName = "Jonas", note = "", authorUid = AUTHOR, authorTimeMs = 102_000L, authorHints = 0,
        createdAtMs = 0L, unpublished = false,
    )

    private fun storedPuzzle() = ReceivedPuzzle(
        code = code, gameId = TestGames.alpha, variant = VariantId("alpha/n8"), sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(1, 2, 3), title = "Crown the castle", authorName = "Jonas", note = "",
        authorTimeMs = 102_000L, authorHints = 0, authorUid = AUTHOR, openedAt = Instant.EPOCH, unpublished = false,
        postState = PostState.Pending, postAttempts = 0, difficulty = null,
    )

    /** The in-memory repository, counting [observe] calls and optionally answering them with [fetch]. */
    private class CountingMatchRepository(private val real: FakeMatchRepository) : MatchRepository by real {
        var observed = 0
        var fetch: MatchFetch? = null

        override fun observe(code: ShareCode): Flow<MatchFetch> {
            observed++
            return fetch?.let { flowOf(it) } ?: real.observe(code)
        }
    }

    private companion object {
        const val AUTHOR = "u1"
    }
}
