package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.domain.lineup.Freshness
import com.ivandsky.puzzlebox.domain.lineup.PickerResult
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
