package com.ivandsky.puzzlebox.play.random

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomShared
import com.ivandsky.puzzlebox.play.domain.random.RandomPick
import com.ivandsky.puzzlebox.play.testDispatchers
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The sheet's view model over the real pick use cases and the domain fakes: alpha has 8 past days to 11-18, New today starts empty. */
class RandomPuzzleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val alphaDays = (0L until 8).map { LocalDate.of(2026, 11, 10).plusDays(it) }
    private val daily = FakeDailyRepository().apply {
        setArchiveRange(alpha, alphaDays.first()..today)
        alphaDays.forEach { setDay(alpha, it, DailyDay.Ready(TestGames.puzzle(alpha), 100)) }
    }
    private val shared = FakeSharedPuzzleRepository().apply { clock = { NOW } }
    private val memory = RandomPickMemory()
    private val gate = CompletableDeferred<Unit>()

    /** New today's list waits at [gate] when [held]; every other call is the fake's own. */
    private fun viewModel(seed: Int = 1, held: Boolean = false): RandomPuzzleViewModel {
        val registry = TestGames.registry(listOf(alpha, beta))
        val repository = if (held) {
            object : SharedPuzzleRepository by shared {
                override suspend fun listPublic(filter: CommunityFilter): CommunityListResult {
                    gate.await()
                    return shared.listPublic(filter)
                }
            }
        } else {
            shared
        }
        val list = ListCommunityPuzzles(repository, FakeHiddenPuzzles(), FakeIdentityService(Identity(uid = ME, displayName = "Me", photoUrl = null))) { NOW }
        val random = Random(seed)
        val load = LoadRandomPuzzle(
            LoadRandomDay(daily, FakeCompletionRepository(), LoadPastDay(daily, registry), registry, random),
            LoadRandomShared(list, random),
        )
        return RandomPuzzleViewModel(alpha, load, memory, testDispatchers(main.testDispatcher))
    }

    private suspend fun publish(code: String) {
        shared.signedInUid = OTHER
        shared.publishesToday = 0
        shared.publish(
            SharedPuzzleDoc(
                code = checkNotNull(ShareCode.parse(code)), formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = alpha.value,
                variant = "alpha/v", shareCodecVersion = 1, payload = byteArrayOf(1), title = "T", authorName = "A", note = "",
                authorUid = OTHER, authorTimeMs = 1_000L, authorHints = 0, createdAtMs = NOW - HOUR, unpublished = false,
                visibility = SharedPuzzleDoc.Visibility.Public, difficulty = null,
            ),
        )
    }

    @Test
    fun `it starts on the archive with the preset game, unsolved only and no difficulty`() {
        assertEquals(
            RandomPuzzleState(RandomSource.Past, alpha, unsolvedOnly = true, difficulty = null, phase = RandomPhase.Idle),
            viewModel().state.value,
        )
    }

    @Test
    fun `a pick on the archive preset opens a past day of that game and is remembered`() {
        val vm = viewModel()
        vm.pick()
        val phase = vm.state.value.phase as RandomPhase.Opened
        val day = phase.pick as RandomPick.Day
        assertEquals(alpha, day.game)
        assertTrue(day.day in alphaDays)
        assertEquals(day, memory.last)
    }

    @Test
    fun `a second pick differs from the first`() {
        for (seed in 1..50) {
            memory.last = null
            val vm = viewModel(seed)
            vm.pick()
            val first = (vm.state.value.phase as RandomPhase.Opened).pick
            vm.reset()
            vm.pick()
            assertNotEquals(first, (vm.state.value.phase as RandomPhase.Opened).pick)
        }
    }

    @Test
    fun `offline says so and leaves the filters as they were`() {
        alphaDays.forEach { daily.setDay(alpha, it, DailyDay.Failed(FetchError.Unavailable())) }
        val vm = viewModel()
        val before = vm.state.value
        vm.pick()
        assertEquals(before.copy(phase = RandomPhase.Said(RandomMessage.Offline)), vm.state.value)
        assertNull(memory.last)
    }

    @Test
    fun `an empty New today list says nothing is left in Community`() {
        val vm = viewModel()
        vm.chooseSource(RandomSource.Community)
        vm.pick()
        assertEquals(RandomPhase.Said(RandomMessage.NothingLeftCommunity), vm.state.value.phase)
    }

    @Test
    fun `an archive with nothing unsolved says nothing is left in the past`() {
        daily.setArchiveRange(alpha, null)
        val vm = viewModel()
        vm.pick()
        assertEquals(RandomPhase.Said(RandomMessage.NothingLeftPast), vm.state.value.phase)
    }

    @Test
    fun `a filter change clears a message`() {
        val vm = viewModel()
        vm.chooseSource(RandomSource.Community)
        vm.pick()
        vm.chooseDifficulty(DifficultyChoice.Easy)
        assertEquals(RandomPhase.Idle, vm.state.value.phase)
        assertEquals(DifficultyChoice.Easy, vm.state.value.difficulty)
    }

    @Test
    fun `reset during a pick leaves Idle and the memory unchanged`() = runPublished {
        val earlier = RandomPick.Day(alpha, alphaDays[0])
        memory.last = earlier
        val vm = viewModel(held = true)
        vm.chooseSource(RandomSource.Community)
        vm.pick()
        assertEquals(RandomPhase.Picking, vm.state.value.phase)

        vm.reset()
        gate.complete(Unit)

        assertEquals(RandomPhase.Idle, vm.state.value.phase)
        assertEquals(earlier, memory.last)
    }

    @Test
    fun `filter changes and a second pick are ignored while picking`() = runPublished {
        val vm = viewModel(held = true)
        vm.chooseSource(RandomSource.Community)
        vm.pick()
        val picking = vm.state.value

        vm.chooseSource(RandomSource.Past)
        vm.chooseGame(null)
        vm.setUnsolvedOnly(false)
        vm.chooseDifficulty(DifficultyChoice.Hard)
        vm.pick()

        assertEquals(picking, vm.state.value)
        gate.complete(Unit)
        assertTrue(vm.state.value.phase is RandomPhase.Opened)
    }

    @Test
    fun `filter changes and a pick are ignored once a puzzle is open`() {
        val vm = viewModel()
        vm.pick()
        val opened = vm.state.value
        val remembered = memory.last

        vm.chooseGame(null)
        vm.setUnsolvedOnly(false)
        vm.pick()

        assertEquals(opened, vm.state.value)
        assertEquals(remembered, memory.last)
    }

    @Test
    fun `switching back to Past after a Community difficulty still picks a Past day`() {
        val vm = viewModel()
        vm.chooseSource(RandomSource.Community)
        vm.chooseDifficulty(DifficultyChoice.Hard)
        assertEquals(RandomSource.Community, vm.state.value.source)
        assertEquals(DifficultyChoice.Hard, vm.state.value.difficulty)

        vm.chooseSource(RandomSource.Past)
        vm.pick()

        assertEquals(alpha, ((vm.state.value.phase as RandomPhase.Opened).pick as RandomPick.Day).game)
    }

    private fun runPublished(body: suspend () -> Unit) = kotlinx.coroutines.test.runTest {
        publish("AAA111")
        body()
    }

    private companion object {
        const val ME = "me"
        const val OTHER = "other"
        const val NOW = 1_800_000_000_000L
        const val HOUR = 60L * 60 * 1000
    }
}
