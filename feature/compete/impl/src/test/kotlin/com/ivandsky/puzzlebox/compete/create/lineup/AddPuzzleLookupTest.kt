package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.domain.lineup.CodeLookup
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The add sheet's code lookup (A4, A5, A20): what a typed or pasted code or link shows, over a scripted [CodeLookup]. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddPuzzleLookupTest {

    private val rows = listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID)

    /** A code on no list: the spec's own example. */
    private val unloaded = pickable(QUEENS, "K7QX2M", "Rooks", "Ed", PuzzleSource.Friend)

    private val asked = mutableListOf<String>()
    private var answer: suspend (String) -> CodeLookup = { CodeLookup.NotFound }

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun vm() = AddPuzzleViewModel(pickerOf(rows)) { code -> asked += code; answer(code) }

    private fun titles(vm: AddPuzzleViewModel) = vm.state.value.rows.map { it.puzzle.entry.title }

    private fun TestScope.watch(vm: AddPuzzleViewModel) = vm.state.launchIn(backgroundScope)

    private fun watching(body: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = body)

    @Test
    fun `a code in any case without the dash shows the loaded row and asks nobody`() = watching {
        val vm = vm()
        watch(vm)

        vm.setText("k7q2mx")

        assertEquals(listOf("Crown jewels"), titles(vm))
        assertNull(vm.state.value.lookup)
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `a puzzle link shows the loaded row of its code and asks nobody`() = watching {
        val vm = vm()
        watch(vm)

        vm.setText(CROWN_JEWELS.code!!.link)

        assertEquals(listOf("Crown jewels"), titles(vm))
        assertNull(vm.state.value.lookup)
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `a name that happens to read as a code is searched, not looked up`() = watching {
        val vm = vm()
        watch(vm)

        vm.setText("jewels")

        assertEquals(listOf("Crown jewels"), titles(vm))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `an unknown code says Looking up, then shows the puzzle it found with Add`() = watching {
        val gate = CompletableDeferred<CodeLookup>()
        answer = { gate.await() }
        val vm = vm()
        watch(vm)

        vm.setText("k7qx2m")

        assertEquals("Looking up K7Q-X2M…", vm.state.value.lookup)
        assertEquals(emptyList<String>(), titles(vm))
        assertEquals(listOf("K7QX2M"), asked)

        gate.complete(CodeLookup.Found(unloaded))

        assertEquals(listOf("Rooks"), titles(vm))
        assertNull(vm.state.value.lookup)
        assertEquals(unloaded.entry, vm.state.value.rows.single().puzzle.entry)
    }

    @Test
    fun `an unknown puzzle link is looked up by its code`() = watching {
        answer = { CodeLookup.Found(unloaded) }
        val vm = vm()
        watch(vm)

        vm.setText(requireNotNull(ShareCode.parse("K7QX2M")).link)

        assertEquals(listOf("K7QX2M"), asked)
        assertEquals(listOf("Rooks"), titles(vm))
    }

    @Test
    fun `a found puzzle the lineup holds reads as added`() = watching {
        answer = { CodeLookup.Found(unloaded) }
        val vm = vm()
        watch(vm)
        vm.setText("k7qx2m")

        vm.setAdded(setOf(unloaded.entry.ref))

        assertEquals(true, vm.state.value.rows.single().added)
    }

    @Test
    fun `a code nobody shared says so`() = watching {
        answer = { CodeLookup.NotFound }
        val vm = vm()
        watch(vm)

        vm.setText("k7q-x2m")

        assertEquals("No puzzle has the code K7Q-X2M. Check it with the person who shared it.", vm.state.value.lookup)
        assertEquals(emptyList<String>(), titles(vm))
    }

    @Test
    fun `offline says the lookup needs a connection, never that the code is unknown`() = watching {
        answer = { CodeLookup.Offline }
        val vm = vm()
        watch(vm)

        vm.setText("k7qx2m")

        assertEquals("You're offline. Code lookup needs a connection.", vm.state.value.lookup)
    }

    @Test
    fun `a withdrawn puzzle and one this version cannot open each say why`() = watching {
        val vm = vm()
        watch(vm)

        answer = { CodeLookup.NoLongerAvailable }
        vm.setText("k7qx2m")
        assertEquals("The person who shared K7Q-X2M took that puzzle down.", vm.state.value.lookup)

        answer = { CodeLookup.CannotOpen }
        vm.setText("")
        vm.setText("k7qx2m")
        assertEquals("K7Q-X2M can't be opened in this version of the app. Update PuzzleBox to try it.", vm.state.value.lookup)
    }

    @Test
    fun `text that is no code is never looked up`() = watching {
        val vm = vm()
        watch(vm)

        vm.setText("crown")
        vm.setText("K7Q-X2")
        vm.setText("https://example.com/p/K7Q-X2M")

        assertEquals(emptyList<String>(), asked)
        assertNull(vm.state.value.lookup)
    }

    @Test
    fun `changing the text drops the running lookup, and its late answer shows nowhere`() = watching {
        val first = CompletableDeferred<CodeLookup>()
        val second = CompletableDeferred<CodeLookup>()
        answer = { code -> if (code == "K7QX2M") first.await() else second.await() }
        val vm = vm()
        watch(vm)
        vm.setText("k7qx2m")

        vm.setText("b8c3df")
        first.complete(CodeLookup.Found(unloaded))

        assertEquals("Looking up B8C-3DF…", vm.state.value.lookup)
        assertEquals(emptyList<String>(), titles(vm))
        second.complete(CodeLookup.NotFound)
        assertEquals("No puzzle has the code B8C-3DF. Check it with the person who shared it.", vm.state.value.lookup)
    }

    @Test
    fun `reset leaves no lookup behind, so the next opening starts clean and asks again`() = watching {
        answer = { CodeLookup.Found(unloaded) }
        val vm = vm()
        watch(vm)
        vm.setText("k7qx2m")

        vm.reset()

        assertNull(vm.state.value.lookup)
        assertEquals(4, vm.state.value.rows.size)
        vm.setText("k7qx2m")
        assertEquals(listOf("K7QX2M", "K7QX2M"), asked)
        assertEquals(listOf("Rooks"), titles(vm))
    }
}
