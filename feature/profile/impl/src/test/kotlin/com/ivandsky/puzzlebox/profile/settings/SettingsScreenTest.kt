package com.ivandsky.puzzlebox.profile.settings

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric compose coverage for the Settings screen: the theme choice, About, Developer and Back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val themes = mutableListOf<ThemeMode>()
    private val queensAutoCross = mutableListOf<Boolean>()
    private val sudokuPeer = mutableListOf<Boolean>()
    private val sudokuSameDigit = mutableListOf<Boolean>()
    private val tiers = mutableListOf<VisibilityTier>()
    private val ranksFriends = mutableListOf<Boolean>()
    private val vibration = mutableListOf<Boolean>()
    private val timeLeft = mutableListOf<Boolean>()
    private var backClicks = 0
    private var catalogClicks = 0
    private var signInClicks = 0
    private var whatsNewClicks = 0

    private fun setContent(
        state: SettingsUiState = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0"),
        showCatalog: Boolean = true,
        debugSections: (@Composable () -> Unit)? = null,
    ) {
        composeRule.setContent {
            PuzzleBoxTheme {
                SettingsScreen(
                    state = state,
                    onSetThemeMode = { themes += it },
                    onSetQueensAutoCross = { queensAutoCross += it },
                    onSetSudokuPeerHighlight = { sudokuPeer += it },
                    onSetSudokuSameDigitHighlight = { sudokuSameDigit += it },
                    onSelectVisibility = { tiers += it },
                    onSetRanksFriendsDefault = { ranksFriends += it },
                    onSetVibration = { vibration += it },
                    onSetShowSelfDuelTimeLeft = { timeLeft += it },
                    onSignIn = { signInClicks++ },
                    onBack = { backClicks++ },
                    onOpenWhatsNew = { whatsNewClicks++ },
                    onOpenCatalog = if (showCatalog) ({ catalogClicks++ }) else null,
                    debugSections = debugSections,
                )
            }
        }
    }

    @Test
    fun `each games row shows its stored value and a tap reports the flipped one`() {
        setContent(
            state = SettingsUiState(
                themeMode = ThemeMode.LIGHT,
                versionName = "1.4.0",
                games = GamesSectionState(queensAutoCross = false, sudokuPeerHighlight = true, sudokuSameDigitHighlight = false),
            ),
        )

        composeRule.onNodeWithTag("settings.games.queens.autocross").performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithTag("settings.games.sudoku.peer").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("settings.games.sudoku.samedigit").performScrollTo().assertIsOff().performClick()

        assertEquals(listOf(true), queensAutoCross)
        assertEquals(listOf(false), sudokuPeer)
        assertEquals(listOf(true), sudokuSameDigit)
    }

    @Test
    fun `the vibration row shows the stored value and a tap reports the flipped one`() {
        setContent(state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", vibration = true))

        composeRule.onNodeWithTag("settings.vibration.switch").performScrollTo().assertIsOn().performClick()

        assertEquals(listOf(false), vibration)
    }

    @Test
    fun `the play row shows the stored value and a tap reports the flipped one`() {
        setContent(state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", showSelfDuelTimeLeft = true))

        composeRule.onNodeWithTag("settings.play.timeleft").performScrollTo().assertIsOn().performClick()

        assertEquals(listOf(false), timeLeft)
    }

    @Test
    fun `vibration sits in its own section outside Games`() {
        setContent(state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", vibration = false))

        composeRule.onNodeWithTag("settings.vibration.switch").performScrollTo().assertIsOff()
        composeRule.onNode(hasTestTag("settings.vibration.switch") and hasAnyAncestor(hasTestTag("settings.games"))).assertDoesNotExist()
    }

    @Test
    fun `the visibility ladder marks the effective tier and a tap reports the tapped one`() {
        setContent(
            state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", visibility = OwnTier.Unset),
        )

        composeRule.onNodeWithTag("settings.visibility.board").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("settings.visibility.hidden").assertIsNotSelected()
        composeRule.onNodeWithText("Past entries stay.", substring = true).assertExists()

        composeRule.onNodeWithTag("settings.visibility.replays").performScrollTo().performClick()

        assertEquals(listOf(VisibilityTier.Replays), tiers)
    }

    @Test
    fun `an unloaded tier selects nothing`() {
        setContent(state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", visibility = OwnTier.Unknown))

        VisibilityTier.entries.forEach {
            composeRule.onNodeWithTag("settings.visibility.${it.storageName}").performScrollTo().assertIsNotSelected()
        }
    }

    @Test
    fun `an unloaded tier ignores taps`() {
        setContent(state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", visibility = OwnTier.Unknown))

        VisibilityTier.entries.forEach {
            composeRule.onNodeWithTag("settings.visibility.${it.storageName}").performScrollTo().performClick().assertIsNotEnabled()
        }

        assertEquals(emptyList<VisibilityTier>(), tiers)
    }

    @Test
    fun `signed out the visibility section says why and a tap on sign-in starts it`() {
        setContent(state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", visibility = OwnTier.Unknown))

        composeRule.onNodeWithTag("settings.visibility.reason").performScrollTo().assertTextEquals("Sign in to choose")
        composeRule.onNodeWithTag("settings.visibility.signin").performScrollTo().performClick()

        assertEquals(1, signInClicks)
    }

    @Test
    fun `signed in with the tier not read yet shows no sign-in prompt but keeps the rows off`() {
        setContent(state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", visibility = OwnTier.Unknown, signedIn = true))

        composeRule.onNodeWithTag("settings.visibility.reason").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.visibility.signin").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.visibility.board").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a chosen tier shows no sign-in prompt`() {
        setContent(state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", visibility = OwnTier.Unset))

        composeRule.onNodeWithTag("settings.visibility.reason").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.visibility.signin").assertDoesNotExist()
    }

    @Test
    fun `no visibility section without a state`() {
        setContent()

        composeRule.onNodeWithTag("settings.visibility").assertDoesNotExist()
    }

    @Test
    fun `the ranks default marks the stored scope and a tap reports the tapped one`() {
        setContent(state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", visibility = OwnTier.Unset, ranksFriendsDefault = true))

        composeRule.onNodeWithTag("settings.ranks.friends").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("settings.ranks.global").assertIsNotSelected()

        composeRule.onNodeWithTag("settings.ranks.global").performScrollTo().performClick()

        assertEquals(listOf(false), ranksFriends)
    }

    @Test
    fun `no ranks section without the visibility state`() {
        setContent()

        composeRule.onNodeWithTag("settings.ranks").assertDoesNotExist()
    }

    @Test
    fun `the stored mode is selected and tapping another reports it`() {
        setContent()

        composeRule.onNodeWithTag("profile.appearance.light").assertIsSelected()

        composeRule.onNodeWithTag("profile.appearance.dark").performClick()

        assertEquals(listOf(ThemeMode.DARK), themes)
    }

    @Test
    fun `each Appearance row says its line, marks the stored mode and a tap reports its mode`() {
        setContent(state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0"))

        composeRule.onNodeWithText("Appearance").assertExists()
        composeRule.onNodeWithText("Follows your phone's setting").assertExists()
        composeRule.onNodeWithText("Always light").assertExists()
        composeRule.onNodeWithText("Always dark").assertExists()
        composeRule.onNodeWithTag("profile.appearance").assertExists()
        composeRule.onNodeWithTag("profile.appearance.light").assertIsSelected()
        composeRule.onNodeWithTag("profile.appearance.system").assertIsNotSelected()

        composeRule.onNodeWithTag("profile.appearance.system").performClick()
        composeRule.onNodeWithTag("profile.appearance.light").performClick()
        composeRule.onNodeWithTag("profile.appearance.dark").performClick()

        assertEquals(listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK), themes)
    }

    @Test
    fun `developer section and catalog button exist only with an open-catalog callback`() {
        setContent(showCatalog = true)

        composeRule.onNodeWithTag("settings.developer").assertExists()
        composeRule.onNodeWithTag("settings.catalog").performScrollTo().performClick()
        assertEquals(1, catalogClicks)
    }

    @Test
    fun `debug block shows its heading and the sections inside settings debug`() {
        setContent(debugSections = { Text("debug tools", modifier = Modifier.testTag("settings.debug.probe")) })

        composeRule.onNodeWithTag("settings.debug").performScrollTo().assertExists()
        composeRule.onNodeWithText("Debug build only").assertExists().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNode(hasTestTag("settings.debug.probe") and hasAnyAncestor(hasTestTag("settings.debug"))).assertExists()
        composeRule.onNodeWithTag("settings.catalog").assertExists()
    }

    @Test
    fun `release settings has no debug block`() {
        setContent(showCatalog = false)

        composeRule.onNodeWithTag("settings.debug").assertDoesNotExist()
        composeRule.onNodeWithText("Debug build only").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.debug.probe").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.developer").assertDoesNotExist()
    }

    @Test
    fun `debug sections without a catalog callback still render, with no catalog row`() {
        setContent(showCatalog = false, debugSections = { Text("debug tools", modifier = Modifier.testTag("settings.debug.probe")) })

        composeRule.onNodeWithText("Debug build only").performScrollTo().assertExists()
        composeRule.onNode(hasTestTag("settings.debug.probe") and hasAnyAncestor(hasTestTag("settings.debug"))).assertExists()
        composeRule.onNodeWithTag("settings.developer").assertDoesNotExist()
    }

    @Test
    fun `a catalog callback without debug sections shows the block with only the catalog row`() {
        setContent(showCatalog = true)

        composeRule.onNodeWithText("Debug build only").performScrollTo().assertExists()
        composeRule.onNodeWithTag("settings.catalog").assertExists()
        composeRule.onNodeWithTag("settings.debug.probe").assertDoesNotExist()
    }

    @Test
    fun `without an open-catalog callback there is no developer section`() {
        setContent(showCatalog = false)

        composeRule.onNodeWithTag("settings.developer").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.catalog").assertDoesNotExist()
    }

    @Test
    fun `the version name from the state is on screen`() {
        setContent(SettingsUiState(themeMode = ThemeMode.SYSTEM, versionName = "2.7.1"))

        composeRule.onNodeWithTag("settings.about").assertExists()
        composeRule.onNodeWithText("2.7.1").assertExists()
    }

    @Test
    fun `about has a What's new row and a tap opens it`() {
        setContent(SettingsUiState(themeMode = ThemeMode.SYSTEM, versionName = "1.4.0"))

        composeRule.onNodeWithTag("settings.whatsNew").performScrollTo().assertTextContains("What's new").performClick()

        assertEquals(1, whatsNewClicks)
        composeRule.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `the back icon calls back`() {
        setContent()

        composeRule.onNodeWithTag("settings.back").performClick()

        assertEquals(1, backClicks)
    }

    @Test
    fun `settings carries no LinkedIn block`() {
        setContent()

        composeRule.onNodeWithTag("settings.linkedin.section").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.linkedin").assertDoesNotExist()
        composeRule.onNodeWithText("LinkedIn").assertDoesNotExist()
        composeRule.onNodeWithText("Connect").assertDoesNotExist()
    }
}
