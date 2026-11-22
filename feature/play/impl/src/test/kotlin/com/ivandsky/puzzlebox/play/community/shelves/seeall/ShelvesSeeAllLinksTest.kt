package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.GameChoice
import com.ivandsky.puzzlebox.play.community.shelves.CommunityShelvesScreen
import com.ivandsky.puzzlebox.play.community.shelves.ShelfPuzzleModel
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesActions
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesEntries
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesUiState
import com.ivandsky.puzzlebox.play.community.shelves.codeOf
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPlan
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPlan.Card
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPlan.Section
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which game shelves offer See all: only one that has puzzles to see. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShelvesSeeAllLinksTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<SeeAllScope>()

    private fun show() {
        val cards = (1..3).map { Card(codeOf(it), ALPHA, null, false, Card.Origin.Community) }
        val state = ShelvesUiState.Content(
            plan = ShelfPlan(
                listOf(
                    Section.Newest(cards),
                    Section.GameShelf(ALPHA, cards, inAll = 6),
                    Section.GameShelf(BETA, emptyList(), inAll = 0),
                ),
            ),
            puzzles = (1..3).associate { codeOf(it) to ShelfPuzzleModel("Puzzle $it", "Author $it", Difficulty.Level.Medium) },
            games = mapOf(ALPHA to GameChoice(ALPHA, "Alpha"), BETA to GameChoice(BETA, "Beta")),
            entries = ShelvesEntries(friendPuzzles = 0, published = 0, drafts = 0),
        )
        val actions = ShelvesActions(onEnterCode = {}, onOpen = {}, onRetry = {}, onSeeAll = { opened += it })
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.requiredSize(412.dp, 4000.dp)) { CommunityShelvesScreen(state, actions, phone = true) }
            }
        }
    }

    @Test
    fun `a game shelf with puzzles links to its See all`() {
        show()
        composeRule.onNodeWithTag("shelves.seeall.${ALPHA.value}").performClick()
        assertEquals(listOf<SeeAllScope>(SeeAllScope.Game(ALPHA)), opened)
    }

    @Test
    fun `an empty game shelf has no See all link`() {
        show()
        composeRule.onNodeWithTag("shelves.seeall.${BETA.value}").assertDoesNotExist()
    }
}
