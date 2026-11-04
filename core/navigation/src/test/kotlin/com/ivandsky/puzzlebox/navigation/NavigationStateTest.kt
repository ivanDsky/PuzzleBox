package com.ivandsky.puzzlebox.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

/** Tab-and-stacks transitions, invariants and the back preview, as a pure value. */
class NavigationStateTest {

    @Serializable @SerialName("Home")
    private data object Home : NavKey

    @Serializable @SerialName("Play")
    private data object Play : NavKey

    @Serializable @SerialName("Profile")
    private data object Profile : NavKey

    @Serializable @SerialName("Extra")
    private data object Extra : NavKey

    @Serializable @SerialName("Page")
    private data class Page(val id: Int) : NavKey {
        override fun toString() = "Page($id)"
    }

    private val fresh = navigationState(Home, Play, Profile)

    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(Home::class)
                subclass(Play::class)
                subclass(Profile::class)
                subclass(Extra::class)
                subclass(Page::class)
            }
        }
    }

    @Test
    fun `fresh state has every tab at its root and selects the first`() {
        assertEquals(NavigationState(Home, listOf(listOf(Home), listOf(Play), listOf(Profile))), fresh)
        assertEquals(Home, fresh.startTab)
        assertEquals(listOf<NavKey>(Home, Play, Profile), fresh.tabs)
    }

    @Test
    fun `selecting another tab keeps every stack`() {
        val state = fresh.push(Page(1)).selectTab(Play)

        assertEquals(Play, state.tab)
        assertEquals(listOf<NavKey>(Home, Page(1)), state.stackOf(Home))
        assertEquals(listOf<NavKey>(Play), state.currentStack)
    }

    @Test
    fun `reselecting the current tab pops it to its root`() {
        val state = fresh.selectTab(Play).push(Page(1)).push(Page(2)).selectTab(Play)

        assertEquals(NavigationState(Play, listOf(listOf(Home), listOf(Play), listOf(Profile))), state)
    }

    @Test
    fun `selecting an unknown tab throws`() {
        assertThrows(IllegalArgumentException::class.java) { fresh.selectTab(Extra) }
    }

    @Test
    fun `push appends to the current stack only`() {
        val state = fresh.selectTab(Play).push(Page(1)).push(Page(1))

        assertEquals(listOf<NavKey>(Play, Page(1), Page(1)), state.currentStack)
        assertEquals(listOf<NavKey>(Home), state.stackOf(Home))
    }

    @Test
    fun `pushing a tab root throws`() {
        assertThrows(IllegalArgumentException::class.java) { fresh.push(Play) }
    }

    @Test
    fun `pushOnce is idempotent`() {
        val once = fresh.pushOnce(Page(1))

        assertSame(once, once.pushOnce(Page(1)))
        assertEquals(listOf<NavKey>(Home, Page(1), Page(2)), once.pushOnce(Page(2)).currentStack)
    }

    @Test
    fun `pop removes the top key`() {
        assertEquals(fresh.push(Page(1)), fresh.push(Page(1)).push(Page(2)).pop())
    }

    @Test
    fun `pop at a non-start root selects the start tab and keeps its stack`() {
        val state = fresh.push(Page(1)).selectTab(Profile).pop()

        assertEquals(NavigationState(Home, listOf(listOf(Home, Page(1)), listOf(Play), listOf(Profile))), state)
    }

    @Test
    fun `pop at the start root returns null`() {
        assertNull(fresh.pop())
    }

    @Test
    fun `replaceTop swaps the top key`() {
        val state = fresh.push(Page(1)).push(Page(2)).replaceTop(Page(3))

        assertEquals(listOf<NavKey>(Home, Page(1), Page(3)), state.currentStack)
    }

    @Test
    fun `replaceTop at a root or with the same key returns the same state`() {
        val pushed = fresh.push(Page(1))

        assertSame(fresh, fresh.replaceTop(Page(1)))
        assertSame(pushed, pushed.replaceTop(Page(1)))
    }

    @Test
    fun `replaceTop with a tab root throws`() {
        assertThrows(IllegalArgumentException::class.java) { fresh.push(Page(1)).replaceTop(Play) }
    }

    @Test
    fun `visible keys are the start stack then the selected stack`() {
        val state = fresh.push(Page(1)).selectTab(Play).push(Page(2))

        assertEquals(listOf<NavKey>(Home, Play), state.visibleTabs)
        assertEquals(listOf<NavKey>(Home, Page(1), Play, Page(2)), state.visibleKeys)
        assertEquals(listOf<NavKey>(Home), fresh.visibleTabs)
        assertEquals(setOf<NavKey>(Home, Page(1), Play, Page(2), Profile), state.allKeys)
    }

    @Test
    fun `constructor rejects broken invariants`() {
        listOf<() -> Unit>(
            { NavigationState(Home, emptyList()) },
            { NavigationState(Home, listOf(listOf(Home), emptyList())) },
            { NavigationState(Home, listOf(listOf(Home), listOf(Home, Page(1)))) },
            { NavigationState(Extra, listOf(listOf(Home), listOf(Play))) },
            { NavigationState(Home, listOf(listOf(Home, Play), listOf(Play))) },
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }

    @Test
    fun `state round-trips through serialization`() {
        val state = fresh.push(Page(1)).selectTab(Play).push(Page(2))

        assertEquals(state, json.decodeFromString<NavigationState>(json.encodeToString(state)))
    }

    @Test
    fun `decode rejects broken invariants`() {
        listOf(
            """{"tab":{"type":"Home"},"stacks":[]}""",
            """{"tab":{"type":"Home"},"stacks":[[{"type":"Home"}],[]]}""",
            """{"tab":{"type":"Home"},"stacks":[[{"type":"Home"}],[{"type":"Home"}]]}""",
            """{"tab":{"type":"Extra"},"stacks":[[{"type":"Home"}]]}""",
            """{"tab":{"type":"Home"},"stacks":[[{"type":"Home"},{"type":"Play"}],[{"type":"Play"}]]}""",
        ).forEach { encoded ->
            assertThrows(IllegalArgumentException::class.java) { json.decodeFromString<NavigationState>(encoded) }
        }
    }

    @Test
    fun `reconciling a restored state adds missing tabs and keeps existing stacks`() {
        val restored = fresh.selectTab(Play).push(Page(1))

        val state = restored.reconciledWith(navigationState(Home, Play, Profile, Extra))

        assertEquals(
            NavigationState(Play, listOf(listOf(Home), listOf(Play, Page(1)), listOf(Profile), listOf(Extra))),
            state,
        )
    }

    @Test
    fun `reconciling drops a removed selected tab and selects the start tab`() {
        val restored = fresh.push(Page(1)).selectTab(Profile).push(Page(2))

        val state = restored.reconciledWith(navigationState(Home, Play))

        assertEquals(NavigationState(Home, listOf(listOf(Home, Page(1)), listOf(Play))), state)
    }

    @Test
    fun `toString prints spec row notation`() {
        assertEquals("Home[Home] **Play**[Play, Page(1)] Profile[Profile]", fresh.selectTab(Play).push(Page(1)).toString())
    }

    @Test
    fun `a non-null pop removes exactly the last visible key, for every state in a random walk`() {
        val random = Random(seed = 42)
        var state = fresh
        repeat(5_000) { step ->
            val popped = state.pop()
            if (popped != null) {
                assertEquals("step $step from $state", state.visibleKeys.dropLast(1), popped.visibleKeys)
            }
            state = when (random.nextInt(5)) {
                0 -> state.selectTab(state.tabs.random(random))
                1 -> state.push(Page(random.nextInt(4)))
                2 -> state.pushOnce(Page(random.nextInt(4)))
                3 -> state.replaceTop(Page(random.nextInt(4)))
                else -> popped ?: state
            }
        }
    }
}
