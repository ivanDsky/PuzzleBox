package com.ivandsky.puzzlebox.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.ivandsky.puzzlebox.app.screens.ProfileContent
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.profile.settings.GamesSectionState
import com.ivandsky.puzzlebox.profile.settings.SettingsScreen
import com.ivandsky.puzzlebox.profile.settings.SettingsUiState
import com.ivandsky.puzzlebox.profile.whatsnew.WhatsNewCard
import com.ivandsky.puzzlebox.profile.whatsnew.WhatsNewScreen
import com.ivandsky.puzzlebox.profile.whatsnew.WhatsNewUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ticket 1509: Profile, Settings and What's new keep their place on a short screen while their state arrives again: the
 * sync line's clock ticking, a switch that was just flipped, a release that joined at the top.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h260dp")
class ProfileScrollKeepsPositionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var profile by mutableStateOf(ProfileUiState(playingSince = LocalDate.of(2026, 1, 5)))
    private var nowMs by mutableLongStateOf(0L)
    private var settings by mutableStateOf(SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0", games = GamesSectionState(queensAutoCross = false)))
    private var releases by mutableStateOf(releases(range = 0 until RELEASES))

    private val vertical = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun SemanticsNodeInteraction.scrolled(): Float =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun `Profile keeps its scroll while its sync clock and its state arrive again`() {
        composeRule.setContent {
            PuzzleBoxTheme {
                ProfileContent(
                    state = profile,
                    onSignIn = {},
                    onSignOut = {},
                    onBack = {},
                    onOpenSettings = {},
                    nowMs = nowMs,
                    onSyncNow = {},
                    onDeleteCloudData = {},
                    onDeleteSavedBoards = {},
                )
            }
        }
        composeRule.onNode(vertical).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val before = composeRule.onNode(vertical).scrolled()
        assertTrue("Profile scrolled", before > 0f)

        nowMs += 60_000L
        profile = profile.copy(playingSince = LocalDate.of(2026, 1, 6))
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(before, composeRule.onNode(vertical).scrolled(), 0.5f)
    }

    @Test
    fun `Settings keeps its scroll when its state arrives again with a switch flipped`() {
        composeRule.setContent {
            PuzzleBoxTheme {
                SettingsScreen(
                    state = settings,
                    onSetThemeMode = {},
                    onSetQueensAutoCross = {},
                    onSetSudokuPeerHighlight = {},
                    onSetSudokuSameDigitHighlight = {},
                    onBack = {},
                    onOpenCatalog = null,
                )
            }
        }
        composeRule.onNodeWithTag("settings.about").performScrollTo()
        composeRule.waitForIdle()
        val before = composeRule.onNode(vertical).scrolled()
        assertTrue("Settings scrolled", before > 0f)

        settings = settings.copy(vibration = false, games = settings.games.copy(queensAutoCross = true))
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(before, composeRule.onNode(vertical).scrolled(), 0.5f)
    }

    @Test
    fun `What's new keeps its release under the thumb when a newer release joins at the top`() {
        composeRule.setContent { PuzzleBoxTheme { WhatsNewScreen(state = WhatsNewUiState(releases), onBack = {}) } }
        composeRule.onNodeWithTag("whatsNew.list").performScrollToIndex(15)
        composeRule.waitForIdle()
        val tag = "whatsNew.release.${version(15)}"
        val before = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

        releases = releases(range = -2 until RELEASES)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(before.value, composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    private companion object {
        const val RELEASES = 30

        /** Newest first: a lower index is a newer version; a negative one a release that came in above the rest. */
        fun version(index: Int) = "0.%02d.0".format(60 - index)

        fun releases(range: IntRange) = range.map { WhatsNewCard(version(it), "18 Nov 2026", listOf("Note $it")) }
    }
}
