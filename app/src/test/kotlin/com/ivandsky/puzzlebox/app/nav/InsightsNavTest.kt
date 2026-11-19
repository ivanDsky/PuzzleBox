package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.insights.api.InsightsKey
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.settings.VisibilitySlot
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Insights way in: Profile's row behind the MonthlyRecap flag, the hub, and a month's recap above it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class InsightsNavTest {

    private val navigator = Navigator(puzzleBoxNavigationState())

    private fun opener(flags: FakeReleaseFlags) = insightsOpener(flags.enabled.value, navigator)

    @Test
    fun `with the flag off Profile has no way into Insights`() {
        assertFalse(FakeReleaseFlags().isEnabled(InsightsFlag.MonthlyRecap))

        assertNull(opener(FakeReleaseFlags()))
    }

    @Test
    fun `with the flag on Profile opens the hub over itself`() {
        navigator.pushLayer(ProfileKey)

        val open = opener(FakeReleaseFlags(InsightsFlag.MonthlyRecap))
        assertNotNull(open)
        open!!()

        assertEquals(listOf(ProfileKey, InsightsKey), navigator.state.layer)
    }

    @Test
    fun `a month opens its recap over the hub and back returns to the hub`() {
        navigator.pushLayer(ProfileKey)
        navigator.pushLayer(InsightsKey)

        navigator.pushLayer(InsightsRecapKey("2026-11"))
        assertEquals(listOf(ProfileKey, InsightsKey, InsightsRecapKey("2026-11")), navigator.state.layer)

        assertTrue(navigator.pop())
        assertEquals(listOf(ProfileKey, InsightsKey), navigator.state.layer)
    }

    @Test
    fun `a recap's replay opens on the Games tab by its placement and closes the layer`() {
        val origin = navigator.state.tab
        navigator.pushLayer(ProfileKey)
        navigator.pushLayer(InsightsKey)
        navigator.pushLayer(InsightsRecapKey("2026-11"))
        val replay = ReplayKey.daily("zip", "2026-11-03", puzzleNumber = 12, latest = false)

        openRecapReplay(navigator)(replay)

        assertEquals(Placement(Home.PushedOn(Tab.Games), Chrome.Immersive), placementOf(replay))
        assertEquals(PlayKey, navigator.state.tab)
        assertEquals(listOf<NavKey>(PlayKey, replay), navigator.state.currentStack)
        assertEquals(emptyList<NavKey>(), navigator.state.layer)
        assertEquals(replay, navigator.state.visibleKeys.last())

        assertTrue(navigator.pop())
        assertEquals(origin, navigator.state.tab)
        assertEquals(emptyList<NavKey>(), navigator.state.layer)
        assertEquals(listOf<NavKey>(PlayKey, replay), navigator.state.stackOf(PlayKey))
    }

    @Test
    fun `opening the same replay twice leaves one replay on the stack`() {
        navigator.pushLayer(InsightsRecapKey("2026-11"))
        val replay = ReplayKey.daily("zip", "2026-11-03", puzzleNumber = 12, latest = false)

        openRecapReplay(navigator)(replay)
        openRecapReplay(navigator)(replay)

        assertEquals(listOf<NavKey>(PlayKey, replay), navigator.state.currentStack)
    }

    @Test
    fun `the hub and the recap keys resolve`() {
        val entries = puzzleBoxEntryProvider(
            navigator = navigator,
            homeLayout = HomeLayout.SinglePane,
            playLayout = PlayLayout.SinglePane,
            historyLayout = HistoryLayout.SinglePane,
            custom = testCustomSlot(),
            makeOne = testMakeOne(navigator),
            visibility = VisibilitySlot(flowOf(OwnTier.Unset)) {},
        )

        listOf(InsightsKey, InsightsRecapKey("2026-11")).forEach { key ->
            val entry = entries(key)
            assertTrue("got ${entry.contentKey}", entry.contentKey.toString().contains(key.toString()))
        }
    }
}
