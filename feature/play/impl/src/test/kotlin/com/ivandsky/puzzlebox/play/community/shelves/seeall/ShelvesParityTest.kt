package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.CommunityShelvesRoute
import com.ivandsky.puzzlebox.play.CreateFabClearance
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesFixture
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesViewModel
import com.ivandsky.puzzlebox.play.community.shelves.codeOf
import com.ivandsky.puzzlebox.play.previewYoursSlot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val REPORTED = "Reported. It's hidden on this device."

/**
 * What the old Community tabs did, kept by the shelves route with the flag on: Report and Remove friend, the share
 * import, scroll positions, the tab reselect, Make a game's puzzle and the create button. The phone and the tablet run
 * the same checks.
 */
internal abstract class ShelvesParityTest(private val layout: PlayLayout) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val shelves = ShelvesFixture()
    private val seeAll = SeeAllFixture()
    private val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val restoration by lazy { StateRestorationTester(rule) }
    private val removed = mutableListOf<ShareCode>()
    private val made = mutableListOf<GameId?>()
    private var sharePending by mutableStateOf(false)

    @Before
    fun setUp() {
        stopKoin() // tests share the JVM-global Koin context
        runBlocking {
            shelves.publishMany(30, ALPHA)
            shelves.publishMany(2, BETA, from = 31)
            shelves.receive(1)
            repeat(30) { seeAll.publish(it + 1, title = "Row ${it + 1}") }
        }
        startKoin {
            modules(
                module {
                    factory<ShelvesViewModel> { shelves.viewModel() }
                    factory { seeAll.viewModel() }
                },
            )
        }
    }

    @After
    fun tearDown() = stopKoin()

    private fun show(fab: Boolean = false, clearance: Dp = 0.dp) {
        restoration.setContent {
            CompositionLocalProvider(LocalTabReselect provides reselect) {
                PuzzleBoxTheme {
                    CommunityShelvesRoute(
                        layout, previewYoursSlot(count = 0), onOpen = {}, onPlay = {}, onEnterCode = {},
                        onNewPuzzle = if (fab) ({ game -> made += game }) else null,
                        clearance = clearance,
                        modifier = Modifier.fillMaxSize(),
                        onRemoveFriend = { removed += it },
                        sharePending = sharePending,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun longPress(tag: String) {
        rule.onNodeWithTag(tag).performTouchInput { longClick() }
        rule.waitForIdle()
    }

    private fun reselectTab() {
        rule.runOnIdle { reselect.tryEmit(Unit) }
        rule.waitForIdle()
    }

    private fun shown(tag: String) = runCatching { rule.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess

    private fun card(n: Int) = "shelves.card.${codeOf(n).value}"

    @Test
    fun `a long press on a shelf card opens Report, which hides the puzzle and says so`() {
        show()
        longPress(card(2))
        rule.onNodeWithTag("${card(2)}.removeFriend").assertDoesNotExist()
        click("${card(2)}.report")

        rule.onNodeWithTag(card(2)).assertDoesNotExist()
        rule.onNodeWithText(REPORTED).assertIsDisplayed()
    }

    @Test
    fun `a long press on a friend's card also offers Remove friend, which asks the host to remove it`() {
        show()
        longPress(card(1))
        rule.onNodeWithTag("${card(1)}.report").assertIsDisplayed()
        click("${card(1)}.removeFriend")

        assertEquals(listOf(codeOf(1)), removed)
    }

    @Test
    fun `the row menu of a See all page reports the puzzle`() {
        show()
        click("shelves.seeall.newest")
        click("community.row.${codeOf(3).value}.more")
        click("community.row.${codeOf(3).value}.report")

        rule.onNodeWithTag("community.row.${codeOf(3).value}").assertDoesNotExist()
        rule.onNodeWithTag("seeall.notice").assertIsDisplayed()
    }

    @Test
    fun `a shared text waiting opens Your puzzles`() {
        show()
        rule.onNodeWithTag("yours.page").assertDoesNotExist()

        sharePending = true
        rule.waitForIdle()

        rule.onNodeWithTag("yours.page").assertIsDisplayed()
    }

    @Test
    fun `the home keeps its scroll through a page opening and closing and through a rotation`() {
        show()
        rule.onNodeWithTag("shelves.list").performScrollToIndex(2)
        rule.waitForIdle()
        assertTrue(!shown("shelves.search"))

        click("shelves.seeall.newest")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertTrue("the home is where it was after a page", !shown("shelves.search"))

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertTrue("the home is where it was after a rotation", !shown("shelves.search"))
    }

    @Test
    fun `a See all page keeps its scroll through a rotation`() {
        show()
        click("shelves.seeall.newest")
        rule.onNodeWithTag("seeall.list").performScrollToIndex(20)
        rule.waitForIdle()
        assertTrue(!shown("seeall.count"))

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("seeall.page").assertIsDisplayed()
        assertTrue("the page is where it was after a rotation", !shown("seeall.count"))
    }

    @Test
    fun `the first reselect closes an open page and the second scrolls the home to the top`() {
        show()
        rule.onNodeWithTag("shelves.list").performScrollToIndex(2)
        rule.waitForIdle()
        click("shelves.seeall.newest")

        reselectTab()
        rule.onNodeWithTag("seeall.page").assertDoesNotExist()
        assertTrue("the first reselect left the home where it was", !shown("shelves.search"))

        reselectTab()
        rule.onNodeWithTag("shelves.search").assertIsDisplayed()
    }

    @Test
    fun `a Make card starts a puzzle of its game`() {
        show(fab = true, clearance = CreateFabClearance)
        rule.onNodeWithTag("shelves.list").performScrollToNode(hasTestTag("shelves.make.${BETA.value}"))
        click("shelves.make.${BETA.value}")

        assertEquals(listOf<GameId?>(BETA), made)
    }

    @Test
    fun `the create button is on the home and on a See all page, which end above it`() {
        show(fab = true, clearance = CreateFabClearance)
        rule.onNodeWithTag("community.fab").assertIsDisplayed()

        click("shelves.seeall.newest")
        rule.onNodeWithTag("community.fab").assertIsDisplayed()
        val last = "community.row.${codeOf(30).value}"
        rule.onNodeWithTag("seeall.list").performScrollToIndex(30)
        rule.waitForIdle()
        val fabTop = rule.onNodeWithTag("community.fab").getUnclippedBoundsInRoot().top
        val rowBottom = rule.onNodeWithTag(last).getUnclippedBoundsInRoot().bottom
        assertTrue("the last row ($rowBottom) ends above the button ($fabTop)", rowBottom <= fabTop)

        click("community.fab")
        assertEquals(listOf<GameId?>(null), made)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
internal class ShelvesParityPhoneTest : ShelvesParityTest(PlayLayout.SinglePane)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
internal class ShelvesParityTabletTest : ShelvesParityTest(PlayLayout.TwoPane)
