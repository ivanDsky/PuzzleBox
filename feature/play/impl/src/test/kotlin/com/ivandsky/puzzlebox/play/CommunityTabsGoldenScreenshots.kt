package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityState
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.play.community.GameChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The phone's Community as three tabs (M12-4), light and dark: New today filled, filtered (the chips reading the picks) and empty
 * (with Make a puzzle); Friends filled and empty; Yours filled and empty. The filter chips are in New today's list only.
 * Yours' list is a stand-in ([previewYoursSlot]): the real one is create's. Goldens in `snapshots/community-tabs/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class CommunityTabsGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `new today light`() = capture("new-today", FILLED, tab = null, dark = false)
    @Test fun `new today dark`() = capture("new-today", FILLED, tab = null, dark = true)

    @Test fun `new today filtered light`() = capture("new-today-filtered", FILLED.copy(state = FILTERED), tab = null, dark = false)
    @Test fun `new today filtered dark`() = capture("new-today-filtered", FILLED.copy(state = FILTERED), tab = null, dark = true)

    @Test fun `new today empty light`() = capture("new-today-empty", NOTHING, tab = null, dark = false)
    @Test fun `new today empty dark`() = capture("new-today-empty", NOTHING, tab = null, dark = true)

    @Test fun `friends light`() = capture("friends", FILLED, tab = "community.tab.friends", dark = false)
    @Test fun `friends dark`() = capture("friends", FILLED, tab = "community.tab.friends", dark = true)

    @Test fun `friends empty light`() = capture("friends-empty", NOTHING, tab = "community.tab.friends", dark = false)
    @Test fun `friends empty dark`() = capture("friends-empty", NOTHING, tab = "community.tab.friends", dark = true)

    @Test fun `yours light`() = capture("yours", FILLED, tab = "community.tab.yours", dark = false)
    @Test fun `yours dark`() = capture("yours", FILLED, tab = "community.tab.yours", dark = true)

    @Test fun `yours empty light`() = capture("yours-empty", NOTHING, tab = "community.tab.yours", dark = false)
    @Test fun `yours empty dark`() = capture("yours-empty", NOTHING, tab = "community.tab.yours", dark = true)

    /** Shows [shown] and, for a [tab] other than the first, taps it and waits for its page. */
    private fun capture(name: String, shown: Shown, tab: String?, dark: Boolean) {
        rule.showGolden(dark) {
            CommunityTabs(
                state = shown.state,
                actions = ACTIONS,
                friends = shown.friends,
                custom = previewYoursSlot(shown.yours),
                onOpen = {},
                onRemove = {},
                onEnterCode = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (tab != null) {
            rule.onNodeWithTag(tab).performClick()
            rule.mainClock.advanceTimeBy(1_000)
            rule.onNodeWithTag(tab).assertIsSelected()
            rule.onNodeWithTag("community.filter.game").assertDoesNotExist()
        }
        rule.settleAndCapture("community-tabs", name, "phone", dark)
    }

    private data class Shown(val state: CommunityState, val friends: List<FriendCardModel>, val yours: Int)

    private companion object {
        val ACTIONS = CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {})

        val GAMES = listOf(GameChoice(GameId("zip"), "Zip"), GameChoice(GameId("tango"), "Tango"), GameChoice(GameId("queens"), "Queens"))

        val FILLED = Shown(
            state = CommunityPanesGoldenScreenshots.LOADED.copy(games = GAMES),
            friends = CommunityPanesGoldenScreenshots.FRIENDS,
            yours = 3,
        )

        val FILTERED = FILLED.state.copy(game = GameId("zip"), difficulty = DifficultyChoice.Hard)

        val NOTHING = Shown(CommunityPanesGoldenScreenshots.EMPTY.copy(games = GAMES), friends = emptyList(), yours = 0)
    }
}
