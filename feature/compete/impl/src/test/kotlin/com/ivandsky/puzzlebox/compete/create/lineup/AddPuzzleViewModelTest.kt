package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.common.flags.FilterFlag
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.domain.lineup.Freshness
import com.ivandsky.puzzlebox.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.domain.lineup.PickerResult
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddPuzzleViewModelTest {

    private val rows = listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID)

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun titles(vm: AddPuzzleViewModel) = vm.state.value.rows.map { it.puzzle.entry.title }

    private fun TestScope.watch(vm: AddPuzzleViewModel) = vm.state.launchIn(backgroundScope)

    /** Unconfined, so what the view model collects in the background is there at the next line. */
    private fun watching(body: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = body)

    @Test
    fun `nothing is loaded before the first list, then every puzzle shows`() = watching {
        val vm = AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP)
        assertFalse(vm.state.value.loaded)

        watch(vm)

        assertTrue(vm.state.value.loaded)
        assertEquals(listOf("Crown jewels", "Crown Tango", "Old crown", "My grid"), titles(vm))
    }

    @Test
    fun `text, New and a game narrow to the one puzzle, and the chips stay while the text changes`() = watching {
        val vm = AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP)
        watch(vm)

        vm.setText("crown")
        vm.toggle(Freshness.New)
        vm.toggle(QUEENS)
        assertEquals(listOf("Crown jewels"), titles(vm))

        vm.setText("cro")
        assertEquals(setOf(Freshness.New), vm.state.value.query.fresh)
        assertEquals(setOf(QUEENS), vm.state.value.query.games)
        assertEquals(listOf("Crown jewels"), titles(vm))
    }

    @Test
    fun `a chip toggles off on the second tap and several chips of a group widen the list`() = watching {
        val vm = AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP)
        watch(vm)

        vm.toggle(PuzzleSource.Mine)
        vm.toggle(PuzzleSource.Friend)
        assertEquals(listOf("Crown jewels", "My grid"), titles(vm))

        vm.toggle(PuzzleSource.Mine)
        vm.toggle(TANGO)
        assertEquals(emptyList<String>(), titles(vm))
    }

    @Test
    fun `a puzzle the lineup holds is marked Added, and unmarked when it leaves`() = watching {
        val vm = AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP)
        watch(vm)

        vm.setAdded(setOf(CROWN_JEWELS.entry.ref))
        assertEquals(listOf(true, false, false, false), vm.state.value.rows.map { it.added })

        vm.setAdded(emptySet())
        assertEquals(listOf(false, false, false, false), vm.state.value.rows.map { it.added })
    }

    @Test
    fun `reset clears text and chips`() = watching {
        val vm = AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP)
        watch(vm)
        vm.setText("crown")
        vm.toggle(QUEENS)

        vm.reset()

        assertEquals(NO_QUERY, vm.state.value.query)
        assertEquals(4, vm.state.value.rows.size)
    }

    @Test
    fun `loadMore asks for the next archive page only while the list says one is left, and reset starts over at one`() = watching {
        val asked = mutableListOf<Int>()
        var more = true
        val vm = AddPuzzleViewModel({ _, pages -> pages.map { n -> asked += n; PickerResult(rows, offline = false, moreDays = more) } }, NO_LOOKUP)
        watch(vm)

        vm.loadMore()
        more = false
        vm.loadMore()
        vm.loadMore()
        assertEquals(listOf(1, 2, 3), asked)
        vm.reset()

        assertEquals(listOf(1, 2, 3, 1), asked)
    }

    @Test
    fun `the offline flag of the list reaches the state`() = watching {
        val vm = AddPuzzleViewModel(pickerOf(rows, offline = true), NO_LOOKUP)

        watch(vm)

        assertTrue(vm.state.value.offline)
    }

    private fun pastYou(solved: Flow<List<PickablePuzzle>>) = AddPuzzleViewModel(
        observe = { queries, _ -> solved.flatMapLatest { rows -> pickerOf(rows)(queries, flowOf(1)) } },
        lookup = null,
        pastYou = true,
    )

    @Test
    fun `a Past you search lists what was solved in the order given and narrows it by text, source and game, with no lookup`() = watching {
        val vm = pastYou(flowOf(SOLVED_ROWS))
        watch(vm)

        assertTrue(vm.state.value.pastYou)
        assertEquals(listOf("Zip #12", "Crown Tango", "Queens #9"), titles(vm))

        vm.setText("bo")
        assertEquals(listOf("Crown Tango"), titles(vm))
        vm.setText("K7Q2MX")
        assertEquals(listOf("Crown Tango"), titles(vm))
        vm.setText("")
        vm.toggle(PuzzleSource.PastDay)
        assertEquals(listOf("Zip #12", "Queens #9"), titles(vm))
        vm.toggle(PuzzleSource.PastDay)
        vm.toggle(PuzzleSource.Friend)
        assertEquals(listOf("Crown Tango"), titles(vm))
        vm.toggle(PuzzleSource.Friend)
        vm.toggle(QUEENS)
        assertEquals(listOf("Queens #9"), titles(vm))
        assertEquals(null, vm.state.value.lookup)
    }

    @Test
    fun `a pasted code that is not a solved row leaves only the empty list, no Looking up and no Retry`() = watching {
        val vm = pastYou(flowOf(SOLVED_ROWS))
        watch(vm)

        vm.setText("ZZ9-QQ2")
        vm.paste("ZZ9QQ2")
        vm.lookUp()

        assertEquals(emptyList<String>(), titles(vm))
        assertEquals(null, vm.state.value.lookup)
        assertFalse(vm.state.value.canRetry)
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun `a puzzle solved while the sheet is open shows up without reopening`() = watching {
        val solved = MutableStateFlow(listOf(FRIEND_TANGO))
        val vm = pastYou(solved)
        watch(vm)
        assertEquals(listOf("Crown Tango"), titles(vm))

        solved.value = listOf(ZIP_DAILY, FRIEND_TANGO)

        assertEquals(listOf("Zip #12", "Crown Tango"), titles(vm))
    }

    @Test
    fun `the friend search is not a Past you one, and each keeps its own text`() = watching {
        val friend = AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP)
        val past = pastYou(flowOf(SOLVED_ROWS))
        watch(friend)
        watch(past)

        past.setText("zip")

        assertFalse(friend.state.value.pastYou)
        assertEquals("", friend.state.value.query.text)
        assertEquals(listOf("Zip #12"), titles(past))
    }

    @Test
    fun `filterSheets follows the FilterSheets flag from the first emission, off when no flags are given`() = watching {
        val off = AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP)
        val on = AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP, releaseFlags = FakeReleaseFlags(FilterFlag.FilterSheets))
        val unrelated = AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP, releaseFlags = FakeReleaseFlags())
        assertFalse(off.state.value.filterSheets)
        assertTrue("before the first list", on.state.value.filterSheets)
        assertFalse(unrelated.state.value.filterSheets)

        watch(on)

        assertTrue("after the list loads", on.state.value.filterSheets)
    }

    @Test
    fun `each group clears on its own, leaving the other groups and the text`() = watching {
        val vm = AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP)
        watch(vm)
        vm.setText("crown")
        vm.toggle(Freshness.New)
        vm.toggle(PuzzleSource.Community)
        vm.toggle(QUEENS)

        vm.clearFreshness()
        assertEquals(emptySet<Freshness>(), vm.state.value.query.fresh)
        assertEquals(setOf(PuzzleSource.Community), vm.state.value.query.sources)
        assertEquals(setOf(QUEENS), vm.state.value.query.games)

        vm.clearSources()
        assertEquals(emptySet<PuzzleSource>(), vm.state.value.query.sources)
        assertEquals(setOf(QUEENS), vm.state.value.query.games)

        vm.clearGames()
        assertEquals(0, vm.state.value.activeFilters)
        assertEquals("crown", vm.state.value.query.text)
    }
}
