package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.GOLDEN_PHONE
import com.ivandsky.puzzlebox.history.RanksTuneDefaults
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Tune sheet's Defaults block: fed by [RanksTuneDefaults], and each pick writes once, sheet still open. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TuneSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ownTier = MutableStateFlow<OwnTier>(OwnTier.Chosen(VisibilityTier.Board))
    private val friendsDefault = MutableStateFlow(true)
    private val tierPicks = mutableListOf<VisibilityTier>()
    private val friendsPicks = mutableListOf<Boolean>()
    private val scopePicks = mutableListOf<RankScope>()
    private var dismissed = 0

    private val defaults = RanksTuneDefaults(
        ownTier = ownTier,
        onSelectTier = { tierPicks += it; ownTier.value = OwnTier.Chosen(it) },
        friendsDefault = friendsDefault,
        onSelectFriendsDefault = { friendsPicks += it; friendsDefault.value = it },
    )

    private fun show(slot: RanksTuneDefaults?) {
        composeRule.setContent {
            PuzzleBoxTheme {
                TuneSheet(
                    scope = RankScope.Friends,
                    window = Window.Day,
                    onScope = { scopePicks += it },
                    onWindow = {},
                    onDismiss = { dismissed++ },
                    defaults = slot,
                )
            }
        }
    }

    @Test
    fun `picking Global and another tier calls each setter once`() {
        show(defaults)

        composeRule.onNodeWithTag("ranks.tune.defaults.scope.Global").performClick()
        composeRule.onNodeWithTag("ranks.tune.defaults.tier.duel").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(false), friendsPicks)
        assertEquals(listOf(VisibilityTier.Duel), tierPicks)
    }

    @Test
    fun `a default pick keeps the sheet open and leaves the scope shown now alone`() {
        show(defaults)

        composeRule.onNodeWithTag("ranks.tune.defaults.scope.Global").performClick()
        composeRule.onNodeWithTag("ranks.tune.defaults.tier.replays").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ranks.tune.sheet").assertIsDisplayed()
        assertEquals(0, dismissed)
        assertEquals(emptyList<RankScope>(), scopePicks)
    }

    @Test
    fun `the block shows the stored default scope and tier`() {
        friendsDefault.value = false
        ownTier.value = OwnTier.Chosen(VisibilityTier.Duel)
        show(defaults)

        composeRule.onNodeWithTag("ranks.tune.defaults.scope.Global").assertIsSelected()
        composeRule.onNodeWithTag("ranks.tune.defaults.scope.Friends").assertIsNotSelected()
        composeRule.onNodeWithTag("ranks.tune.defaults.tier.duel").assertIsSelected()
        composeRule.onNodeWithTag("ranks.tune.defaults.tier.board").assertIsNotSelected()
    }

    @Test
    fun `an unset tier reads as the Board default`() {
        ownTier.value = OwnTier.Unset
        show(defaults)

        composeRule.onNodeWithTag("ranks.tune.defaults.tier.board").assertIsSelected()
    }

    @Test
    fun `the choices are at least 48dp tall`() {
        show(defaults)

        VisibilityTier.entries.forEach {
            composeRule.onNodeWithTag("ranks.tune.defaults.tier.${it.storageName}").assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun `without defaults the sheet holds Who and When only`() {
        show(null)

        composeRule.onNodeWithTag("ranks.tune.scope.Friends").assertIsSelected()
        composeRule.onNodeWithTag("ranks.tune.window.Day").assertIsSelected()
        composeRule.onNodeWithTag("ranks.tune.defaults").assertDoesNotExist()
        composeRule.onNodeWithTag("ranks.tune.defaults.scope.Global").assertDoesNotExist()
        composeRule.onNodeWithTag("ranks.tune.defaults.tier.board").assertDoesNotExist()
    }

    @Test
    fun `with defaults the sheet still has the Defaults section`() {
        show(defaults)

        composeRule.onNodeWithTag("ranks.tune.defaults").assertExists()
    }
}
