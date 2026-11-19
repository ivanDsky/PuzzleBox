package com.ivandsky.puzzlebox.compete.tab

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.compete.api.NewDuelKey
import com.ivandsky.puzzlebox.compete.create.DuelChallenge
import com.ivandsky.puzzlebox.compete.create.NewDuelHarness
import com.ivandsky.puzzlebox.compete.create.newDuelModule
import com.ivandsky.puzzlebox.compete.di.competeModule
import com.ivandsky.puzzlebox.compete.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.compete.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Duels tab on the app's compete Koin module: with SheetScreens on, on the phone, New duel is a screen the host
 * pushes, and in the tablet's pane it is the pane's form (see DuelsPaneNewDuelTest); with the flag off, or with no screen to push, it stays the sheet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DuelsTabNewDuelTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val pushed = mutableListOf<NewDuelKey>()
    private var taken = 0
    private val harness = NewDuelHarness()
    private val sam = DuelChallenge(friendUid = "sam", friendName = "Sam", game = GameId("beta"))

    @After
    fun stopKoinAgain() = stopKoin()

    private fun show(
        flags: ReleaseFlags,
        inPane: Boolean = false,
        screens: Boolean = true,
        challenge: DuelChallenge? = null,
    ) {
        val now = Instant.now()
        startKoin {
            modules(
                competeModule,
                newDuelModule(),
                module {
                    single { ObserveMyDuels(harness.server, FakeSelfMatchRepository(), FakeMatchResultOutbox(), FakeMatchLocalRepository(), harness.identity) { now } }
                    single { UpdateMatchSeat(harness.server, FakeMatchLocalRepository(), harness.identity) }
                    single<IdentityService> { harness.identity }
                    single<ReleaseFlags> { flags }
                    viewModel { harness.viewModel }
                },
            )
        }
        rule.setContent {
            PuzzleBoxTheme {
                DuelsTab(
                    onOpenDuel = {},
                    onOpenReveal = {},
                    challenge = challenge,
                    onChallengeTaken = { taken++ },
                    inPane = inPane,
                    onNewDuel = if (screens) ({ pushed += it }) else null,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun assertSheet() = rule.onNodeWithTag("duel.new.sheet").assertIsDisplayed()

    @Test
    fun `with the flag on the card pushes the New duel screen and opens no sheet`() {
        show(FakeReleaseFlags(ReleaseFlag.SheetScreens))

        rule.onNodeWithTag("duels.challenge").performClick()
        rule.waitForIdle()

        assertEquals(listOf(NewDuelKey()), pushed)
        rule.onAllNodesWithTag("duel.new.sheet").assertCountEquals(0)
    }

    @Test
    fun `a challenge pushes the screen with its friend and game and is taken once`() {
        show(FakeReleaseFlags(ReleaseFlag.SheetScreens), challenge = sam)

        assertEquals(listOf(NewDuelKey("sam", "Sam", "beta")), pushed)
        assertEquals(1, taken)
        rule.onAllNodesWithTag("duel.new.sheet").assertCountEquals(0)
    }

    @Test
    fun `with the flag off the card opens the sheet and pushes nothing`() {
        show(FakeReleaseFlags())

        rule.onNodeWithTag("duels.challenge").performClick()
        rule.waitForIdle()

        assertSheet()
        assertEquals(emptyList<NewDuelKey>(), pushed)
    }

    @Test
    fun `in the tablet's pane the flag asks the pane for the form, so no screen is pushed and no sheet opens`() {
        show(FakeReleaseFlags(ReleaseFlag.SheetScreens), inPane = true)

        rule.onNodeWithTag("duels.challenge").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithTag("duel.new.sheet").assertCountEquals(0)
        assertEquals(emptyList<NewDuelKey>(), pushed)
    }

    @Test
    fun `in the pane with the flag off the card still opens the sheet`() {
        show(FakeReleaseFlags(), inPane = true)

        rule.onNodeWithTag("duels.challenge").performClick()
        rule.waitForIdle()

        assertSheet()
        assertEquals(emptyList<NewDuelKey>(), pushed)
    }

    @Test
    fun `with no screen to push the sheet opens even with the flag on`() {
        show(FakeReleaseFlags(ReleaseFlag.SheetScreens), screens = false)

        rule.onNodeWithTag("duels.challenge").performClick()
        rule.waitForIdle()

        assertSheet()
    }
}
