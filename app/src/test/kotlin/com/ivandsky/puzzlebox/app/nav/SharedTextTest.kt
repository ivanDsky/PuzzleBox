package com.ivandsky.puzzlebox.app.nav

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** The shared-text intake helpers: what a `SEND` share offers, and where it lands (Create's root). */
class SharedTextTest {

    @Test
    fun `send with plain text returns the text`() {
        val text = SharedText.of(Intent.ACTION_SEND, "text/plain", "a puzzle seed")

        assertEquals("a puzzle seed", text)
    }

    @Test
    fun `another action is ignored`() {
        listOf(Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW, null).forEach { action ->
            assertEquals("action $action", null, SharedText.of(action, "text/plain", "text"))
        }
    }

    @Test
    fun `a non plain type is ignored`() {
        listOf("image/*", "video/mp4", null).forEach { type ->
            assertEquals("type $type", null, SharedText.of(Intent.ACTION_SEND, type, "text"))
        }
    }

    @Test
    fun `a missing or blank extra is ignored`() {
        listOf(null, "", "   ", "\n\t").forEach { extra ->
            assertEquals("extra ${extra?.length}", null, SharedText.of(Intent.ACTION_SEND, "text/plain", extra))
        }
    }

    @Test
    fun `the text lands on Create's root from every tab`() {
        listOf(
            stacks(HomeKey, home = listOf(HomeKey, HistoryKey())),
            stacks(PlayKey),
            stacks(PlayKey, play = listOf(PlayKey, EditorKey("castle"))),
            stacks(ProfileKey, profile = listOf(ProfileKey, HistoryKey())),
            stacks(PlayKey, create = listOf(CreateKey, EditorKey("castle"))),
            stacks(HomeKey, create = listOf(CreateKey, EditorKey("castle"), EditorKey("other"))),
        ).forEach { before ->
            val after = before.applied()

            assertEquals("from $before", CreateKey, after.tab)
            assertEquals("from $before", listOf<NavKey>(CreateKey), after.stackOf(CreateKey))
            assertEquals(
                "other stacks kept, from $before",
                before.stacks - setOf(before.stackOf(CreateKey)),
                after.stacks - setOf(after.stackOf(CreateKey)),
            )
        }
    }

    @Test
    fun `a deep Create stack pops to its root`() {
        val before = stacks(CreateKey, create = listOf(CreateKey, EditorKey("castle")))

        assertEquals(stacks(CreateKey), before.applied())
    }

    private fun NavigationState.applied(): NavigationState =
        Navigator(this).apply { applySharedText() }.state

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        create: List<NavKey> = listOf(CreateKey),
        profile: List<NavKey> = listOf(ProfileKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, create, profile))
}
