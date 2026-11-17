package com.ivandsky.puzzlebox.app.nav

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared-text intake helpers: what a `SEND` share offers, and where it lands: Community's root.
 */
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
    fun `the text lands on Community's root from every tab, keeping every other stack`() {
        listOf(
            stacks(HomeKey, home = listOf(HomeKey, HistoryKey())),
            stacks(PlayKey),
            stacks(PlayKey, play = listOf(PlayKey, EditorKey("castle"))),
            stacks(CommunityKey, community = listOf(CommunityKey, EditorKey("castle"))),
            stacks(HistoryRootKey, history = listOf(HistoryRootKey, HistoryKey())),
        ).forEach { before ->
            val after = before.applied()

            assertEquals("from $before", CommunityKey, after.tab)
            assertEquals("from $before", listOf<NavKey>(CommunityKey), after.stackOf(CommunityKey))
            assertEquals(
                "other stacks kept, from $before",
                before.stacks - setOf(before.stackOf(CommunityKey)),
                after.stacks - setOf(after.stackOf(CommunityKey)),
            )
        }
    }

    @Test
    fun `the text closes the layer open over Community`() {
        val covered = stacks(CommunityKey).copy(layer = listOf(ProfileKey))

        assertEquals(stacks(CommunityKey), covered.applied())
    }

    @Test
    fun `back from where the text landed reaches Today`() {
        val navigator = Navigator(stacks(PlayKey, play = listOf(PlayKey, EditorKey("castle"))))

        navigator.applySharedText()
        navigator.pop()

        assertEquals(HomeKey, navigator.state.tab)
    }

    private fun NavigationState.applied(): NavigationState = Navigator(this).apply { applySharedText() }.state

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        community: List<NavKey> = listOf(CommunityKey),
        history: List<NavKey> = listOf(HistoryRootKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, community, history))
}
