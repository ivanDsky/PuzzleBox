package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.DAILY_MATCH_CAP
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.PuzzleCodeCheck
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.DuelSettings
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
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
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The New duel sheet's draft and its Create outcomes (ticket 31), over the core:testing fakes and the real use cases. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewDuelViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 11, 9)
    private val gamma = GameId("gamma")
    private val registry = TestGames.registry(listOf(alpha, beta, gamma))
    private val calendar = FakeGameCalendar(today)
    private val daily = FakeDailyRepository().apply {
        listOf(alpha, beta).forEach { game ->
            setArchiveRange(game, today.minusDays(29)..today)
            setPuzzleNumbers(game) { day -> (day.toEpochDay() % 10_000).toInt() + 1 }
        }
    }
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val identity = FakeIdentityService(ANN)
    private val server = FakeMatchRepository().apply { signedInUid = ANN.uid }
    private val local = FakeMatchLocalRepository()
    private val history = ObserveGameHistory(registry, daily, completions)
    private val createMatch = CreateMatch(
        server, local, ComputeMatchLegs(registry, daily, calendar, history, received, Random(DRAW_SEED)), identity, Random(CODE_SEED),
    )
    private var token: GoogleIdToken = GoogleIdToken.Available("t")
    private val idTokens = object : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = token
    }

    /** The fake server, with Create held until [gate] opens. */
    private class GatedMatchRepository(private val server: FakeMatchRepository, private val gate: CompletableDeferred<Unit>) :
        MatchRepository by server {
        override suspend fun create(draft: MatchDraft): CreateResult {
            gate.await()
            return server.create(draft)
        }
    }

    /** The fake server whose code read is refused by the rules, which a retry cannot fix. */
    private class DeniedCodeReadMatchRepository(server: FakeMatchRepository) : MatchRepository by server {
        override suspend fun puzzleCodeTaken(code: ShareCode): PuzzleCodeCheck = PuzzleCodeCheck.Unavailable(offline = false, message = "denied")
    }

    private fun viewModel(
        createMatch: CreateMatch = this.createMatch,
        zone: ZoneId = ZoneOffset.UTC,
        locale: Locale = Locale.US,
    ) = NewDuelViewModel(
        createMatch = createMatch,
        identity = identity,
        idTokens = idTokens,
        todaySet = ObserveTodaySet(registry, FakeSavedPlayRepository(), completions, calendar, daily, SyncTodayPuzzles(registry, daily, calendar)),
        calendar = calendar,
        daily = daily,
        registry = registry,
        clock = { NOW },
        zone = { zone },
        locale = { locale },
    )

    private fun kotlinx.coroutines.test.TestScope.started(createMatch: CreateMatch = this@NewDuelViewModelTest.createMatch): NewDuelViewModel =
        viewModel(createMatch).also { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.state.collect {} } }

    private fun game(id: GameId, solvedToday: Boolean = false) =
        DuelGame(id, id.value.replaceFirstChar(Char::uppercase), todayNumber = 7, solvedToday = solvedToday, hintsFirst = false)

    private fun solveToday(game: GameId) = completions.setRecords(
        listOf(
            CompletionRecord(
                puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(today, null)),
                time = 1_000L, hints = 0, stats = RecordStats(schemaVersion = 1, blob = "{}"), isRecord = true,
            ),
        ),
    )

    @Test
    fun quickDuelDraftIsBestOfThreeMixedPastTwentyFourHours() {
        val draft = DuelDraft.quick(listOf(game(alpha), game(beta)))

        assertEquals(DuelPreset.Quick, draft.preset)
        assertEquals(3, draft.count)
        assertEquals(setOf(alpha, beta), draft.games)
        assertFalse(draft.todayOnly)
        assertEquals(24, draft.deadlineHours)
        assertFalse(draft.includeFriends)
    }

    @Test
    fun dailyDuelDraftIsTodayOnlyWithEachGameSelected() {
        val draft = DuelDraft.daily(listOf(game(alpha), game(beta, solvedToday = true), game(gamma)))

        assertEquals(DuelPreset.Daily, draft.preset)
        assertTrue(draft.todayOnly)
        assertEquals(setOf(alpha, gamma), draft.games)
        assertEquals(2, draft.count)
        assertEquals(24, draft.deadlineHours)
    }

    @Test
    fun aDailyDuelKeepsTodayOnlyAndNeverJoinsASolvedGame() {
        val solved = game(beta, solvedToday = true)
        val draft = DuelDraft.daily(listOf(game(alpha), solved, game(gamma)))

        assertEquals(draft, draft.toggled(solved))
        val fewer = draft.toggled(game(gamma))
        assertTrue(fewer.todayOnly)
        assertEquals(setOf(alpha), fewer.games)
        assertEquals(1, fewer.count)
        assertEquals(fewer, fewer.toggled(game(alpha)))
    }

    @Test
    fun anEditLeavesTheQuickPresetButKeepsEveryOtherChoice() {
        val quick = DuelDraft.quick(listOf(game(alpha), game(beta)))

        val edited = quick.withDeadline(48)

        assertEquals(DuelPreset.Custom, edited.preset)
        assertEquals(quick.copy(preset = DuelPreset.Custom, deadlineHours = 48), edited)
    }

    @Test
    fun countIsClampedToOneThroughTen() {
        val draft = DuelDraft.quick(listOf(game(alpha)))

        assertEquals(1, draft.withCount(0).count)
        assertEquals(1, draft.withCount(-4).count)
        assertEquals(Match.MAX_LEGS, draft.withCount(Match.MAX_LEGS + 5).count)
        listOf(3, 5, 7).forEach { assertEquals(it, draft.withCount(it).count) }
    }

    @Test
    fun toSettingsMapsCountGamesDeadlineAndFriendsToggle() {
        val custom = DuelDraft.quick(listOf(game(alpha), game(beta))).withCount(5).withDeadline(48).withFriends(true)
        assertEquals(
            DuelSettings(count = 5, games = setOf(alpha, beta), todayOnly = false, deadlineHours = 48, includeFriends = true, pastedCodes = emptyList()),
            custom.toSettings(),
        )

        val dailyDraft = DuelDraft.daily(listOf(game(alpha), game(beta)))
        assertEquals(
            DuelSettings(count = 2, games = setOf(alpha, beta), todayOnly = true, deadlineHours = 24, includeFriends = false, pastedCodes = emptyList()),
            dailyDraft.toSettings(),
        )
    }

    @Test
    fun summaryNamesCountGamesDeadlineAndFriendsClause() {
        val available = listOf(game(alpha), game(beta), game(gamma))
        val draft = DuelDraft.quick(available).toggled(game(gamma)).withCount(5).withDeadline(48)

        val plain = draft.summary(NOW, ZoneOffset.UTC, available, Locale.US)
        val friends = draft.withFriends(true).summary(NOW, ZoneOffset.UTC, available, Locale.US)

        assertTrue(plain, "5" in plain && "Alpha" in plain && "Beta" in plain && "Gamma" !in plain)
        assertTrue(plain, "Wed 10:00" in plain)
        assertFalse(plain, "friends" in plain)
        assertTrue(friends, "friends" in friends)
        assertTrue(DuelDraft.daily(available).summary(NOW, ZoneOffset.UTC, available, Locale.US).let { "Tue 10:00" in it })
    }

    @Test
    fun theStateSummaryEndsInTheInjectedZoneAndLocale() = runTest {
        val vm = viewModel(zone = ZoneOffset.ofHours(14), locale = Locale.FRANCE)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        val summary = vm.state.value.summary

        // The deadline is Tue 10:00 UTC, which is 00:00 at +14, and the weekday is not spelled the US way.
        assertTrue(summary, "00:00" in summary)
        assertFalse(summary, "Tue" in summary || "Wed" in summary)
    }

    @Test
    fun theStateOffersEveryGameWithItsNumberAndSolvedFlag() = runTest {
        solveToday(beta)
        val vm = started()

        val games = vm.state.value.games

        assertEquals(listOf(alpha, beta, gamma), games.map { it.id })
        assertEquals(listOf(false, true, false), games.map { it.solvedToday })
        assertEquals(today.toEpochDay().toInt() % 10_000 + 1, games.first { it.id == alpha }.todayNumber)
        assertEquals(null, games.first { it.id == gamma }.todayNumber)
        assertEquals(DuelDraft.quick(games), vm.state.value.draft)
    }

    @Test
    fun aCreatedDuelEmitsItsCodeOnceAndStoresTheDraft() = runTest {
        val vm = started()
        vm.setCount(5)
        vm.setDeadline(48)

        vm.create()

        val (code, puzzles) = vm.created.first()
        val match = server.matches.getValue(code)
        assertEquals(48, match.deadlineHours)
        // One name per puzzle, in puzzle order, so the invite's "best of" and games match the duel that was drawn.
        assertEquals(5, puzzles.size)
        assertEquals(match.legs.map { it.ref.gameId.value.replaceFirstChar(Char::uppercase) }, puzzles)
        assertEquals(1, server.matches.size)
        assertTrue(vm.state.value.creating)
        assertEquals(null, vm.state.value.notice)

        vm.create()
        assertEquals(1, server.matches.size)

        vm.reset()
        assertFalse(vm.state.value.creating)
    }

    @Test
    fun theCreatedInviteListsTheDrawnPuzzlesNotTheWiderDraft() = runTest {
        val vm = started()
        vm.setCount(2)
        val draftGames = vm.state.value.draft.games

        vm.create()

        val (code, puzzles) = vm.created.first()
        val drawn = server.matches.getValue(code).legs.map { it.ref.gameId.value.replaceFirstChar(Char::uppercase) }
        assertEquals(3, draftGames.size)
        assertEquals(2, drawn.size)
        assertEquals(drawn, puzzles)
    }

    @Test
    fun dismissingMidCreateDropsTheLateOutcomeAndLeavesAFreshDraft() = runTest {
        val gate = CompletableDeferred<Unit>()
        val held = CreateMatch(
            GatedMatchRepository(server, gate), local,
            ComputeMatchLegs(registry, daily, calendar, history, received, Random(DRAW_SEED)), identity, Random(CODE_SEED),
        )
        val vm = started(held)
        vm.setCount(7)
        vm.create()
        assertTrue(vm.state.value.creating)

        vm.reset()
        gate.complete(Unit)

        assertEquals(DuelDraft.quick(vm.state.value.games), vm.state.value.draft)
        assertFalse(vm.state.value.creating)
        assertEquals(null, vm.state.value.notice)
        assertEquals(null, withTimeoutOrNull(1) { vm.created.first() })
    }

    @Test
    fun aSignedOutCreateSendsThePlayerToSignInAndKeepsTheDraft() = runTest {
        val vm = started()
        vm.setCount(7)
        server.signedInUid = null

        vm.create()

        assertEquals(DuelStep.SignIn, vm.state.value.step)
        assertEquals(7, vm.state.value.draft.count)
        assertTrue(server.matches.isEmpty())
    }

    @Test
    fun offlineKeepsTheDraftAndShowsItsOwnNotice() = runTest {
        val vm = started()
        vm.setCount(5)
        server.online = false

        vm.create()

        assertEquals(DuelNotice.Offline, vm.state.value.notice)
        assertEquals(DuelStep.Create, vm.state.value.step)
        assertEquals(5, vm.state.value.draft.count)
    }

    @Test
    fun theDailyCapIsBusyNotOffline() = runTest {
        val vm = started()
        repeat(DAILY_MATCH_CAP) { assertTrue(createMatch(vm.state.value.draft.toSettings()) is CreateOutcome.Created) }
        vm.setCount(5)

        vm.create()

        assertEquals(DuelNotice.Busy, vm.state.value.notice)
        assertEquals(5, vm.state.value.draft.count)
    }

    @Test
    fun noPuzzlesToDrawKeepsTheDraftAndShowsItsOwnNotice() = runTest {
        val vm = started()
        vm.toggle(vm.state.value.games.first { it.id == alpha })
        vm.toggle(vm.state.value.games.first { it.id == beta })
        assertEquals(setOf(gamma), vm.state.value.draft.games)

        vm.create()

        assertEquals(DuelNotice.NoPuzzles, vm.state.value.notice)
        assertEquals(setOf(gamma), vm.state.value.draft.games)
    }

    @Test
    fun aDeniedCodeReadSurfacesAsFailedInTheSheet() = runTest {
        val denied = CreateMatch(
            DeniedCodeReadMatchRepository(server), local,
            ComputeMatchLegs(registry, daily, calendar, history, received, Random(DRAW_SEED)), identity, Random(CODE_SEED),
        )
        val vm = started(denied)

        vm.create()

        assertTrue(vm.state.value.notice is DuelNotice.Failed)
        assertEquals(DuelStep.Create, vm.state.value.step)
        assertTrue(server.matches.isEmpty())
    }

    @Test
    fun aRejectedCreateIsFailedWithItsMessage() = runTest {
        val vm = started()
        server.signedInUid = "someone-else"

        vm.create()

        assertTrue(vm.state.value.notice is DuelNotice.Failed)
        assertEquals(DuelStep.Create, vm.state.value.step)
        assertTrue(server.matches.isEmpty())
    }

    @Test
    fun anEditClearsTheNoticeAndResetStartsFromTheQuickDuelAgain() = runTest {
        val vm = started()
        server.online = false
        vm.create()
        assertEquals(DuelNotice.Offline, vm.state.value.notice)

        vm.setCount(9)
        assertEquals(null, vm.state.value.notice)

        vm.reset()
        assertEquals(DuelDraft.quick(vm.state.value.games), vm.state.value.draft)
    }

    @Test
    fun signedOutUserSeesSignInFirstAndReturnsToCreateOnceSignedIn() = runTest {
        identity.currentIdentity = null
        val vm = started()
        vm.setCount(7)
        assertEquals(DuelStep.SignIn, vm.state.value.step)

        vm.signIn(context)

        assertEquals(listOf("t"), identity.signInCalls)
        assertEquals(DuelStep.Create, vm.state.value.step)
        assertEquals(7, vm.state.value.draft.count)
        assertEquals(DuelSignIn.Idle, vm.state.value.signIn)
    }

    @Test
    fun aDismissedAccountPickerStaysOnSignIn() = runTest {
        identity.currentIdentity = null
        token = GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.CANCELLED)
        val vm = started()

        vm.signIn(context)

        assertEquals(DuelStep.SignIn, vm.state.value.step)
        assertEquals(DuelSignIn.Idle, vm.state.value.signIn)
        assertTrue(identity.signInCalls.isEmpty())
    }

    @Test
    fun aSignedOutCreateReturnsToCreateAfterSignIn() = runTest {
        val vm = started()
        server.signedInUid = null
        vm.create()
        assertEquals(DuelStep.SignIn, vm.state.value.step)
        identity.currentIdentity = null

        vm.signIn(context)

        assertEquals(DuelStep.Create, vm.state.value.step)
    }

    private companion object {
        const val CODE_SEED = 7
        const val DRAW_SEED = 3
        val NOW: Instant = Instant.parse("2026-11-09T10:00:00Z")
        val ANN = Identity(uid = "ann", displayName = "Ann", photoUrl = null)
    }
}
