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

/** Tab-and-stacks transitions, the layer above the tabs, invariants and the back preview, as a pure value. */
class NavigationStateTest {

    @Serializable @SerialName("Home")
    private data object Home : NavKey

    @Serializable @SerialName("Play")
    private data object Play : NavKey

    @Serializable @SerialName("Profile")
    private data object Profile : NavKey

    @Serializable @SerialName("Extra")
    private data object Extra : NavKey

    /** A layer's first page (the app's Profile). */
    @Serializable @SerialName("Me")
    private data object Me : NavKey

    /** A page opened on top of [Me] inside the layer (the app's Settings). */
    @Serializable @SerialName("Gear")
    private data object Gear : NavKey

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
                subclass(Me::class)
                subclass(Gear::class)
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
    fun `switchTo resets the target to its root plus the key, keeps the origin stack and closes the layer`() {
        val origin = fresh.selectTab(Play).push(Page(1)).push(Page(2)).openLayer(Page(9))

        val state = origin.switchTo(Profile, Page(3))

        assertEquals(Profile, state.tab)
        assertEquals(listOf<NavKey>(Profile, Page(3)), state.currentStack)
        assertEquals(listOf<NavKey>(Play, Page(1), Page(2)), state.stackOf(Play))
        assertEquals(emptyList<NavKey>(), state.layer)
        assertEquals(NavigationState.Return(Play, depth = 2), state.returnTo)
    }

    @Test
    fun `one back from a switched tab restores the origin with its whole stack`() {
        val origin = fresh.selectTab(Play).push(Page(1)).push(Page(2))

        val back = origin.switchTo(Profile, Page(3)).pop()

        assertEquals(Play, back?.tab)
        assertEquals(origin.stackOf(Play), back?.stackOf(Play))
        assertNull(back?.returnTo)
        assertEquals(Play, origin.switchTo(Profile).pop()?.tab)
    }

    @Test
    fun `back pops what was pushed after the switch before it returns`() {
        val origin = fresh.selectTab(Play).push(Page(1))
        val state = origin.switchTo(Profile, Page(3)).push(Page(4))

        assertEquals(origin.switchTo(Profile, Page(3)), state.pop())
    }

    @Test
    fun `a tab selected after a switch forgets the return`() {
        val state = fresh.selectTab(Play).switchTo(Profile, Page(3))

        assertNull(state.selectTab(Profile).returnTo)
        assertNull(state.selectTab(Home).returnTo)
        // A rail tap on the tab a link left keeps the old back: Play's root goes to the start tab.
        assertEquals(Home, state.selectTab(Play).pop()?.tab)
    }

    @Test
    fun `switchTo the selected tab records no return`() {
        val state = fresh.selectTab(Play).push(Page(1)).switchTo(Play, Page(2))

        assertEquals(listOf<NavKey>(Play, Page(2)), state.currentStack)
        assertNull(state.returnTo)
    }

    @Test
    fun `a saved return survives a round trip and reconciling drops it when its origin is gone`() {
        val state = fresh.selectTab(Play).push(Page(1)).switchTo(Profile, Page(3))

        val wire = json.encodeToString(NavigationState.serializer(), state)
        assertEquals(state, json.decodeFromString(NavigationState.serializer(), wire))
        assertNull(state.reconciledWith(navigationState(Home, Profile)).returnTo)
        assertEquals(state.returnTo, state.reconciledWith(fresh).returnTo)
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
            { NavigationState(Home, listOf(listOf(Home), listOf(Play)), layer = listOf(Me, Play)) },
            { NavigationState(Home, listOf(listOf(Home), listOf(Play, Me)), layer = listOf(Me)) },
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }

    @Test
    fun `state round-trips through serialization`() {
        val state = fresh.push(Page(1)).selectTab(Play).push(Page(2))
        val layered = state.openLayer(Me).pushLayer(Gear)

        assertEquals(state, json.decodeFromString<NavigationState>(json.encodeToString(state)))
        assertEquals(layered, json.decodeFromString<NavigationState>(json.encodeToString(layered)))
    }

    @Test
    fun `a state saved before the layer existed decodes with an empty layer`() {
        val old = """{"tab":{"type":"Play"},"stacks":[[{"type":"Home"}],[{"type":"Play"},{"type":"Page","id":1}]]}"""

        val state = json.decodeFromString<NavigationState>(old)

        assertEquals(NavigationState(Play, listOf(listOf(Home), listOf(Play, Page(1))), layer = emptyList()), state)
    }

    @Test
    fun `decode rejects broken invariants`() {
        listOf(
            """{"tab":{"type":"Home"},"stacks":[]}""",
            """{"tab":{"type":"Home"},"stacks":[[{"type":"Home"}],[]]}""",
            """{"tab":{"type":"Home"},"stacks":[[{"type":"Home"}],[{"type":"Home"}]]}""",
            """{"tab":{"type":"Extra"},"stacks":[[{"type":"Home"}]]}""",
            """{"tab":{"type":"Home"},"stacks":[[{"type":"Home"},{"type":"Play"}],[{"type":"Play"}]]}""",
            """{"tab":{"type":"Home"},"stacks":[[{"type":"Home"}],[{"type":"Play"}]],"layer":[{"type":"Play"}]}""",
            """{"tab":{"type":"Home"},"stacks":[[{"type":"Home"},{"type":"Me"}]],"layer":[{"type":"Me"}]}""",
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

    // ---- the layer above the tabs ----

    @Test
    fun `openLayer covers the tabs and touches no stack`() {
        val under = fresh.selectTab(Play).push(Page(1))

        assertEquals(NavigationState(Play, under.stacks, layer = listOf(Me)), under.openLayer(Me))
    }

    @Test
    fun `pushLayer stacks a key on the layer, once per double tap`() {
        val state = fresh.openLayer(Me).pushLayer(Gear)

        assertEquals(NavigationState(Home, fresh.stacks, layer = listOf(Me, Gear)), state)
        assertSame(state, state.pushLayer(Gear))
    }

    @Test
    fun `a tab root cannot enter the layer`() {
        assertThrows(IllegalArgumentException::class.java) { fresh.openLayer(Play) }
        assertThrows(IllegalArgumentException::class.java) { fresh.openLayer(Me).pushLayer(Play) }
    }

    @Test
    fun `a key the layer takes leaves the stack it was on`() {
        val state = fresh.push(Me).selectTab(Play).push(Gear).openLayer(Me).pushLayer(Gear)

        assertEquals(NavigationState(Play, fresh.stacks, layer = listOf(Me, Gear)), state)
    }

    @Test
    fun `selecting another tab with the layer open leaves an empty layer on that tab`() {
        val open = fresh.push(Page(1)).openLayer(Me).pushLayer(Gear)

        assertEquals(NavigationState(Play, open.stacks), open.selectTab(Play))
    }

    @Test
    fun `selecting the tab under the layer closes the layer and keeps that tab's stack`() {
        val under = fresh.push(Page(1))

        assertEquals(under, under.openLayer(Me).selectTab(Home))
    }

    @Test
    fun `push closes the layer and lands on the tab under it`() {
        val state = fresh.selectTab(Play).openLayer(Me).pushLayer(Gear).push(Page(1))

        assertEquals(fresh.selectTab(Play).push(Page(1)), state)
    }

    @Test
    fun `pushOnce of the key already under the layer only closes the layer`() {
        val under = fresh.push(Page(1))

        assertEquals(under, under.openLayer(Me).pushOnce(Page(1)))
    }

    @Test
    fun `back from Settings then Profile returns to the screen under the layer`() {
        val under = fresh.selectTab(Play).push(Page(1))

        val profile = under.openLayer(Me).pushLayer(Gear).pop()

        assertEquals(under.openLayer(Me), profile)
        assertEquals(under, profile?.pop())
    }

    @Test
    fun `replaceTop swaps the layer's top while the layer is open`() {
        val state = fresh.push(Page(1)).openLayer(Me).pushLayer(Gear).replaceTop(Page(2))

        assertEquals(listOf<NavKey>(Me, Page(2)), state.layer)
        assertEquals(listOf<NavKey>(Home, Page(1)), state.currentStack)
        assertSame(state, state.replaceTop(Page(2)))
    }

    @Test
    fun `the layer is visible on top, counted in all keys and printed last`() {
        val state = fresh.push(Page(1)).selectTab(Play).openLayer(Me).pushLayer(Gear)

        assertEquals(listOf<NavKey>(Home, Page(1), Play, Me, Gear), state.visibleKeys)
        assertEquals(setOf<NavKey>(Home, Page(1), Play, Profile, Me, Gear), state.allKeys)
        assertEquals("Home[Home, Page(1)] **Play**[Play] Profile[Profile] Layer[Me, Gear]", state.toString())
    }

    @Test
    fun `reconciling keeps the layer when the tab set is unchanged`() {
        val restored = fresh.selectTab(Play).push(Page(1)).openLayer(Me).pushLayer(Gear)

        assertEquals(restored, restored.reconciledWith(fresh))
    }

    @Test
    fun `reconciling keeps the layer when the tab set changes`() {
        val restored = fresh.selectTab(Profile).openLayer(Me).pushLayer(Gear)

        val state = restored.reconciledWith(navigationState(Home, Play, Extra))

        assertEquals(
            NavigationState(Home, listOf(listOf(Home), listOf(Play), listOf(Extra)), layer = listOf(Me, Gear)),
            state,
        )
    }

    @Test
    fun `reconciling drops the layer when one of its keys became a tab root`() {
        val restored = fresh.selectTab(Play).openLayer(Me).pushLayer(Gear)

        val state = restored.reconciledWith(navigationState(Home, Play, Gear))

        assertEquals(NavigationState(Play, listOf(listOf(Home), listOf(Play), listOf(Gear))), state)
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
            state = when (random.nextInt(7)) {
                0 -> state.selectTab(state.tabs.random(random))
                1 -> state.push(Page(random.nextInt(4)))
                2 -> state.pushOnce(Page(random.nextInt(4)))
                3 -> state.replaceTop(Page(random.nextInt(4)))
                4 -> state.openLayer(Page(random.nextInt(4)))
                5 -> state.pushLayer(Page(random.nextInt(4)))
                else -> popped ?: state
            }
        }
    }
}
