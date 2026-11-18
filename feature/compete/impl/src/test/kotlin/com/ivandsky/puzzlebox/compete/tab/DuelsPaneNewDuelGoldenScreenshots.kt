package com.ivandsky.puzzlebox.compete.tab

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.compete.create.NO_ACTIONS
import com.ivandsky.puzzlebox.compete.create.friend
import com.ivandsky.puzzlebox.compete.create.newDuelModule
import com.ivandsky.puzzlebox.compete.create.screen.NewDuelScreen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The tablet's right pane (880 dp wide, 800 dp high) holding the New duel form, light and dark. Goldens in `snapshots/pane/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w880dp-h800dp-mdpi")
class DuelsPaneNewDuelGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinWithTheBrands() {
        startKoin { modules(newDuelModule()) }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    @Test fun `new duel light`() = form(dark = false)
    @Test fun `new duel dark`() = form(dark = true)

    private fun form(dark: Boolean) {
        rule.showGolden(dark) {
            DuelsPaneContent(selected = null, onNewDuel = {}, modifier = Modifier.fillMaxSize(), form = {
                NewDuelScreen(friend(), NO_ACTIONS, onBack = {}, pane = true)
            }) { }
        }
        rule.settleAndCapture("pane", "new-duel", "tablet", dark)
    }
}
