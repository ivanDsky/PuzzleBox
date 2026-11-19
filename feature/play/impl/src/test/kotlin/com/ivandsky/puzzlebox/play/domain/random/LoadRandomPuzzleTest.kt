package com.ivandsky.puzzlebox.play.domain.random

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The entry point sends each filter to its own pool. */
class LoadRandomPuzzleTest {
    private val day = LocalDate.of(2026, 11, 17)
    private val daily = FakeDailyRepository().apply {
        setArchiveRange(alpha, day..day.plusDays(1))
        setDay(alpha, day, DailyDay.Ready(TestGames.puzzle(alpha), 1))
    }
    private val shared = FakeSharedPuzzleRepository().apply { clock = { NOW } }
    private val registry = TestGames.registry(listOf(alpha))
    private val load = LoadRandomPuzzle(
        LoadRandomDay(daily, FakeCompletionRepository(), LoadPastDay(daily, registry), registry, Random(1)),
        LoadRandomShared(
            ListCommunityPuzzles(shared, FakeHiddenPuzzles(), FakeIdentityService(Identity("me", "Me", null))) { NOW },
            Random(1),
        ),
    )

    @Test
    fun `an archive filter picks a past day and a community filter picks a listed puzzle`() = runTest {
        val code = checkNotNull(ShareCode.parse("AAA111"))
        shared.signedInUid = "other"
        shared.publish(
            SharedPuzzleDoc(
                code = code, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = "alpha", variant = "alpha/v1",
                shareCodecVersion = 1, payload = byteArrayOf(1), title = "T", authorName = "A", note = "",
                authorUid = "other", authorTimeMs = 1_000L, authorHints = 0, createdAtMs = NOW - 1_000L, unpublished = false,
                visibility = SharedPuzzleDoc.Visibility.Public,
            ),
        )

        assertEquals(
            RandomOutcome.Picked(RandomPick.Day(alpha, day)),
            load(RandomFilter.Archive(alpha, unsolvedOnly = true), last = null),
        )
        assertEquals(
            RandomOutcome.Picked(RandomPick.Shared(code)),
            load(RandomFilter.Community(alpha, emptySet()), last = null),
        )
    }

    @Test
    fun `each pool keeps its own failure`() = runTest {
        shared.listRefused = true
        assertEquals(RandomOutcome.Refused, load(RandomFilter.Community(null, emptySet()), last = null))

        daily.setDay(alpha, day, DailyDay.Failed(FetchError.Throttled()))
        assertEquals(RandomOutcome.Throttled, load(RandomFilter.Archive(alpha, unsolvedOnly = false), last = null))
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
