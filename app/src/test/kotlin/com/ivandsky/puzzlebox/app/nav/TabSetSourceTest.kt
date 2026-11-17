package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app graph's tab set follows the CommunityPuzzles flag: the tabs and the placement delta move together. */
class TabSetSourceTest {

    @Test
    fun `with no flags the tab set is today, games, ranks and the Studio is immersive on Games`() {
        val tabSet = TabSetSource(FakeReleaseFlags()).tabSet

        assertEquals(listOf("Today", "Games", "Ranks"), tabSet.destinations.map { it.label })
        assertEquals(listOf(HomeKey, PlayKey, HistoryRootKey), tabSet.destinations.map { it.key })
        assertTrue(isImmersive(EditorKey("d1"), tabSet.placement))
    }

    @Test
    fun `with CommunityPuzzles on the tab set is today, games, community, ranks`() {
        val tabSet = TabSetSource(FakeReleaseFlags(ReleaseFlag.CommunityPuzzles)).tabSet

        assertEquals(listOf("Today", "Games", "Community", "Ranks"), tabSet.destinations.map { it.label })
        assertEquals(listOf(HomeKey, PlayKey, CommunityKey, HistoryRootKey), tabSet.destinations.map { it.key })
        assertTrue(isImmersive(EditorKey("d1"), tabSet.placement))
    }
}
