package com.ivandsky.puzzlebox.compete.create

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.compete.assertTouchTargetsAtLeast48
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The New duel sheet with Past you chosen: the same signed in or out (no sign-in panel, Create works), and every
 * control in it a 48 dp touch target, Quick duel and the expanded Customize panel alike.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class NewDuelPastYouTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinWithBrands() {
        startKoin { modules(newDuelModule()) }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun show(state: NewDuelState, actions: NewDuelActions = NO_ACTIONS) {
        rule.setContent {
            PuzzleBoxTheme { Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { NewDuelContent(state, actions) } }
        }
        rule.waitForIdle()
    }

    @Test
    fun `signed out with Past you chosen shows no sign-in panel and Create makes the duel`() {
        var created = 0
        show(quick().copy(selfName = "You"), actionsWith(onCreate = { created++ }))

        rule.onNodeWithTag("duel.opponent.self").assertIsSelected()
        rule.onNodeWithTag("duel.signIn.google").assertDoesNotExist()
        rule.onNodeWithTag("duel.create").assertIsEnabled().performClick()

        assertEquals(1, created)
    }

    @Test
    fun `every control on the Quick duel sheet is a 48 dp touch target`() {
        show(quick())

        rule.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `every control on the open Customize panel is a 48 dp touch target`() {
        show(custom())
        rule.onNodeWithTag("duel.preset.custom").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.friends").assertExists()
        rule.assertTouchTargetsAtLeast48()
    }
}
