package com.ivandsky.puzzlebox.play.community.shelves

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.LocalSelectedRowCode
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SHIMMER_TAG
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.GameChoice
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPlan
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPlan.Card
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPlan.Section
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The shelves home for each [ShelvesUiState]: what it says, what a tap does, and that the Community header is drawn once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommunityShelvesScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf<ShelvesUiState>(ShelvesUiState.Loading)
    private var selected: String? = null
    private val calls = mutableListOf<String>()

    private val actions = ShelvesActions(
        onEnterCode = { calls += "enter" },
        onSearch = { calls += "search" },
        onFriends = { calls += "friends" },
        onYours = { calls += "yours" },
        onOpen = { calls += "open ${it.value}" },
        onMake = { calls += "make ${it?.value}" },
        onRetry = { calls += "retry" },
    )

    @After
    fun tearDown() = animatorScale(1f)

    private fun animatorScale(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun show(initial: ShelvesUiState, phone: Boolean = true) {
        state = initial
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                // Tall, so the lazy list composes every shelf and the test reads them without scrolling.
                Box(Modifier.requiredSize(412.dp, 4000.dp)) {
                    CompositionLocalProvider(LocalSelectedRowCode provides selected) {
                        CommunityShelvesScreen(state, actions, phone)
                    }
                }
            }
        }
    }

    private fun code(n: Int) = checkNotNull(ShareCode.parse("P" + n.toString().padStart(5, '0')))

    private fun card(n: Int, game: GameId = ALPHA, solves: Int? = null, isNew: Boolean = false, origin: Card.Origin = Card.Origin.Community) =
        Card(code(n), game, solves, isNew, origin)

    private fun model(n: Int) = ShelfPuzzleModel("Puzzle $n", "Author $n", Difficulty.Level.Medium)

    private val games = mapOf(ALPHA to GameChoice(ALPHA, "Alpha"), BETA to GameChoice(BETA, "Beta"))

    private fun content(vararg sections: Section, entries: ShelvesEntries = ShelvesEntries(friendPuzzles = 3, published = 2, drafts = 1)) =
        ShelvesUiState.Content(
            plan = ShelfPlan(sections.toList()),
            puzzles = (1..40).associate { code(it) to model(it) },
            games = games,
            entries = entries,
        )

    private val newest = Section.Newest((1..6).map { card(it, isNew = true) })

    @Test
    fun `the Community header is drawn once in every state`() {
        show(ShelvesUiState.Loading)
        for (next in listOf(content(newest), ShelvesUiState.Empty(), ShelvesUiState.Offline(), ShelvesUiState.Refused())) {
            composeRule.onAllNodesWithText("Community").assertCountEquals(1)
            composeRule.onNodeWithTag("community.enterCode").assertExists()
            state = next
            composeRule.waitForIdle()
        }
        composeRule.onAllNodesWithText("Community").assertCountEquals(1)
        composeRule.onAllNodesWithText("Puzzles from everyone, friends and you").assertCountEquals(1)
    }

    @Test
    fun `Enter code and the search stub call out`() {
        show(content(newest))
        composeRule.onNodeWithTag("community.enterCode").performClick()
        composeRule.onNodeWithTag("shelves.search").performClick()
        composeRule.onNodeWithText("Search puzzles, authors, games").assertExists()
        assertEquals(listOf("enter", "search"), calls)
    }

    @Test
    fun `the entry tiles count friends puzzles and yours with drafts`() {
        show(content(newest))
        composeRule.onNodeWithText("From friends").assertExists()
        composeRule.onNodeWithText("3 puzzles").assertExists()
        composeRule.onNodeWithText("Your puzzles").assertExists()
        composeRule.onNodeWithText("2 published · 1 draft").assertExists()
        composeRule.onNodeWithTag("shelves.entry.friends").performClick()
        composeRule.onNodeWithTag("shelves.entry.yours").performClick()
        assertEquals(listOf("friends", "yours"), calls)
    }

    @Test
    fun `without a solve counter there is no Spotlight and no Most played`() {
        show(content(newest))
        composeRule.onNodeWithText("Most played").assertDoesNotExist()
        composeRule.onNodeWithText("#1 MOST PLAYED").assertDoesNotExist()
        composeRule.onNodeWithText("Newest").assertExists()
        composeRule.onNodeWithText("latest first").assertExists()
    }

    @Test
    fun `with a counter the Spotlight and Most played show above Newest`() {
        show(content(Section.Spotlight(card(7, solves = 9)), Section.MostPlayed((8..10).map { card(it, solves = 4) }), newest))
        composeRule.onNodeWithText("#1 MOST PLAYED").assertExists()
        composeRule.onNodeWithText("Most played").assertExists()
        composeRule.onNodeWithText("all time").assertExists()
    }

    @Test
    fun `a card opens its puzzle by code and the open one is ringed`() {
        selected = code(2).value
        show(content(newest))
        composeRule.onNodeWithTag("shelves.card.${code(1).value}").performClick()
        assertEquals(listOf("open ${code(1).value}"), calls)
        composeRule.onNodeWithTag("shelves.card.${code(2).value}").assertIsSelected()
        composeRule.onNodeWithTag("shelves.card.${code(1).value}").assertIsNotSelected()
    }

    @Test
    fun `a card names its puzzle, its author and its level`() {
        show(content(newest))
        composeRule.onNodeWithText("Puzzle 1").assertExists()
        composeRule.onNodeWithText("Author 1 · Medium").assertExists()
    }

    @Test
    fun `a game shelf says what it holds and ends in a Make card`() {
        show(
            content(
                newest,
                Section.GameShelf(ALPHA, (11..13).map { card(it) }, inAll = 6),
                Section.GameShelf(BETA, emptyList(), inAll = 0),
            ),
        )
        composeRule.onNodeWithText("Alpha").assertExists()
        composeRule.onNodeWithText("3 more · 6 in all").assertExists()
        composeRule.onNodeWithText("Beta").assertExists()
        composeRule.onNodeWithText("none yet · be the first").assertExists()
        // Alpha's Make card is the row's last item, past the edge until the row scrolls to it.
        composeRule.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("shelves.shelf.${ALPHA.value}")))
            .performScrollToNode(hasTestTag("shelves.make.${ALPHA.value}"))
        composeRule.onNodeWithText("Make a Alpha puzzle").assertExists()
        composeRule.onNodeWithText("Make a Beta puzzle").performClick()
        assertEquals(listOf("make ${BETA.value}"), calls)
    }

    @Test
    fun `a shelf whose puzzles are all above says so`() {
        show(content(newest, Section.GameShelf(ALPHA, emptyList(), inAll = 6)))
        composeRule.onNodeWithText("all 6 are above").assertExists()
    }

    @Test
    fun `few puzzles with make cards in place of the game shelves`() {
        show(content(Section.Newest((1..4).map { card(it) }), Section.MakeOne(listOf(ALPHA, BETA))))
        composeRule.onNodeWithText("Make a Alpha puzzle").assertExists()
        composeRule.onNodeWithText("Make a Beta puzzle").assertExists()
    }

    @Test
    fun `loading shows the skeleton with its sheen running`() {
        show(ShelvesUiState.Loading)
        composeRule.onNodeWithTag("shelves.skeleton").assertExists()
        assertTrue("no sheen", composeRule.onAllNodesWithTag(SHIMMER_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `under reduced motion the skeleton is still`() {
        animatorScale(0f)
        show(ShelvesUiState.Loading)
        composeRule.onNodeWithTag("shelves.skeleton").assertExists()
        composeRule.onAllNodesWithTag(SHIMMER_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `an empty community is a hero that sends you to make one`() {
        show(ShelvesUiState.Empty(ShelvesEntries(friendPuzzles = 1)))
        composeRule.onNodeWithText("NOTHING HERE YET").assertExists()
        composeRule.onNodeWithText("Be the first to share a puzzle").assertExists()
        composeRule.onNodeWithText("Make one, then share the code with friends.").assertExists()
        composeRule.onNodeWithTag("shelves.empty").performClick()
        assertEquals(listOf("make null"), calls)
    }

    @Test
    fun `offline says so with Retry and still shows the saved entries`() {
        show(ShelvesUiState.Offline(ShelvesEntries(friendPuzzles = 4, published = 1)))
        composeRule.onNodeWithText("You're offline").assertExists()
        composeRule.onNodeWithText("Reconnect to see community puzzles. Saved friends' puzzles and yours still play.").assertExists()
        composeRule.onNodeWithText("4 puzzles").assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `refused says so with Retry`() {
        show(ShelvesUiState.Refused())
        composeRule.onNodeWithText("Couldn't load puzzles").assertExists()
        composeRule.onNodeWithText("Something went wrong. Try again.").assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `the longest copy still draws its card`() {
        val long = "A puzzle with a title far longer than two lines of a narrow card can hold at all"
        val puzzles = mapOf(code(1) to ShelfPuzzleModel(long, "An author with a very long display name indeed", Difficulty.Level.Expert))
        show(ShelvesUiState.Content(ShelfPlan(listOf(Section.Newest(listOf(card(1))))), puzzles, games))
        composeRule.onNode(hasText(long)).assertExists()
    }
}
