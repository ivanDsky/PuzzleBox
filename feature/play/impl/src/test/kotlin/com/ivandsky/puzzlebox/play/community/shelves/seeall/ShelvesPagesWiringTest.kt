package com.ivandsky.puzzlebox.play.community.shelves.seeall

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.play.CommunityShelvesRoute
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesFixture
import com.ivandsky.puzzlebox.play.community.shelves.ShelvesViewModel
import com.ivandsky.puzzlebox.play.previewYoursSlot
import kotlinx.coroutines.runBlocking
import org.junit.After
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

/**
 * The shelves route with its real wiring: the entry tiles, the search stub and a shelf's See all open their page over
 * the home, and back closes it. The phone and the tablet run the same checks (the tablet's page is in the list pane).
 */
internal abstract class ShelvesPagesWiringTest(private val layout: PlayLayout) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        stopKoin() // tests share the JVM-global Koin context
        val shelves = ShelvesFixture()
        val seeAll = SeeAllFixture()
        runBlocking {
            shelves.publishMany(30, ALPHA)
            seeAll.publish(1, title = "Zebra crossing")
        }
        startKoin {
            modules(
                module {
                    factory<ShelvesViewModel> { shelves.viewModel() }
                    factory { seeAll.viewModel() }
                },
            )
        }
        rule.setContent {
            PuzzleBoxTheme {
                CommunityShelvesRoute(layout, previewYoursSlot(count = 0), onOpen = {}, onPlay = {}, onEnterCode = {}, onNewPuzzle = null, clearance = 0.dp, modifier = Modifier.fillMaxSize())
            }
        }
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        stopKoin()
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun back() = rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun assertPageOpensAndBackCloses(tag: String, open: String) {
        rule.onNodeWithTag(open).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(tag).assertExists()
        back()
        rule.waitForIdle()
        rule.onNodeWithTag(tag).assertDoesNotExist()
        rule.onNodeWithTag("community.shelves").assertExists()
    }

    @Test
    fun `the friends tile opens the friends page and back closes it`() = assertPageOpensAndBackCloses("seeall.page", "shelves.entry.friends")

    @Test
    fun `the yours tile opens your puzzles and back closes it`() = assertPageOpensAndBackCloses("yours.page", "shelves.entry.yours")

    @Test
    fun `the search stub opens the search page`() = assertPageOpensAndBackCloses("seeall.page", "shelves.search")

    @Test
    fun `the newest shelf See all opens the newest page`() {
        rule.onNodeWithTag("shelves.seeall.newest").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("seeall.title").assertExists()
        rule.onNodeWithTag("seeall.title").assertTextEquals("Newest")
    }

    @Test
    fun `a game shelf See all opens that game's page`() {
        rule.onNodeWithTag("shelves.seeall.${ALPHA.value}").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("seeall.page").assertExists()
        rule.onNodeWithTag("seeall.filter.game").assertDoesNotExist()
    }

    @Test
    fun `the back button of a page closes it`() {
        rule.onNodeWithTag("shelves.entry.yours").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("yours.back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("yours.page").assertDoesNotExist()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
internal class ShelvesPagesWiringPhoneTest : ShelvesPagesWiringTest(PlayLayout.SinglePane)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
internal class ShelvesPagesWiringTabletTest : ShelvesPagesWiringTest(PlayLayout.TwoPane)
