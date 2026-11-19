package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.compete.CompeteFlag
import com.ivandsky.puzzlebox.compete.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.lineup.ValidateExactPuzzles
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
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
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.random.Random

/**
 * A [NewDuelViewModel] over fakes, for the tests that show New duel on its real view model: Ann signed in (with [photoUrl]),
 * two games with an archive, and the [flags] on.
 */
internal class NewDuelHarness(signedIn: Boolean = true, photoUrl: String? = null, vararg flags: CompeteFlag) {
    private val today = LocalDate.of(2026, 11, 9)
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val calendar = FakeGameCalendar(today)
    private val daily = FakeDailyRepository().apply {
        listOf(alpha, beta).forEach { game ->
            setArchiveRange(game, today.minusDays(29)..today)
            setPuzzleNumbers(game) { day -> (day.toEpochDay() % 10_000).toInt() + 1 }
        }
    }
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val local = FakeMatchLocalRepository()
    private val ann = Identity(uid = "ann", displayName = "Ann", photoUrl = photoUrl)
    val identity = FakeIdentityService(ann.takeIf { signedIn })
    val server = FakeMatchRepository().apply { signedInUid = ann.uid.takeIf { signedIn } }
    val self = FakeSelfMatchRepository()
    private val pool = ComputeSolvedPool(registry, completions, received)

    /** Four solved dailies of one game: enough for a duel against Past you. */
    fun solveSome() = completions.setRecords(
        (1..4).map { day ->
            CompletionRecord(
                puzzleRef = TestGames.Ref(alpha, PuzzleKey.Daily(LocalDate.of(2026, 10, day), puzzleNumber = day)),
                time = 30_000L, hints = 0, stats = RecordStats(schemaVersion = 1, blob = "{}"), isRecord = true,
            )
        },
    )

    val viewModel: NewDuelViewModel by lazy {
        NewDuelViewModel(
            createMatch = CreateMatch(
                server, local, ComputeMatchLegs(registry, daily, calendar, ObserveGameHistory(registry, daily, completions), received, Random(3)),
                identity, Random(7), ValidateExactPuzzles(FakeSharedPuzzleRepository()),
            ),
            identity = identity,
            idTokens = object : GoogleIdTokenProvider {
                override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Available("t")
            },
            todaySet = ObserveTodaySet(registry, FakeSavedPlayRepository(), completions, calendar, daily, SyncTodayPuzzles(registry, daily, calendar)),
            calendar = calendar,
            daily = daily,
            registry = registry,
            createSelfDuel = CreateSelfDuel(pool, completions, received, self, local, { NOW }, Random(3)),
            computeSolvedPool = pool,
            clock = { NOW },
            zone = { ZoneOffset.UTC },
            locale = { Locale.US },
            legTitles = noLegTitles(),
            releaseFlags = FakeReleaseFlags(*flags),
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-09T10:00:00Z")
    }
}
