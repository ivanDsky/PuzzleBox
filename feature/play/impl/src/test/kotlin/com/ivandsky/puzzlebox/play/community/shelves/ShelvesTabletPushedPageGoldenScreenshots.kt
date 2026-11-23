package com.ivandsky.puzzlebox.play.community.shelves

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.play.CommunityShelvesRoute
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllFixture
import com.ivandsky.puzzlebox.play.previewYoursSlot
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
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
 * The real shelves route on the tablet with a page pushed in the list pane: Your puzzles and From friends each
 * cover the pane from the rail's edge to the divider, with no strip of the home beside them (#2081). Goldens in
 * `snapshots/community-shelves/`; untracked review captures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ShelvesTabletPushedPageGoldenScreenshots {

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
    }

    @After
    fun tearDown() = stopKoin()

    @Test fun `yours pushed light`() = capture("pushed-yours", "shelves.entry.yours", dark = false)
    @Test fun `friends pushed light`() = capture("pushed-friends", "shelves.entry.friends", dark = false)
    @Test fun `yours pushed dark`() = capture("pushed-yours", "shelves.entry.yours", dark = true)

    private fun capture(name: String, open: String, dark: Boolean) {
        rule.showGolden(dark, frozen = false) {
            CommunityShelvesRoute(
                PlayLayout.TwoPane, previewYoursSlot(count = 0), onOpen = {}, onPlay = {}, onEnterCode = {},
                onNewPuzzle = null, clearance = 0.dp, modifier = Modifier.fillMaxSize(),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag(open).performClick()
        rule.waitForIdle()
        rule.settleAndCapture("community-shelves", name, "tablet", dark)
    }
}
