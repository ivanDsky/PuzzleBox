package com.ivandsky.puzzlebox.compete.tab

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * The Duels tab on the app's compete Koin module: on the phone, New duel is a screen the host pushes, and in the tablet's
 * pane it is the pane's form (see DuelsPaneNewDuelTest).
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

    private fun show(inPane: Boolean = false, challenge: DuelChallenge? = null) {
        val now = Instant.now()
        startKoin {
            modules(
                competeModule,
                newDuelModule(),
                module {
                    single { ObserveMyDuels(harness.server, FakeSelfMatchRepository(), FakeMatchResultOutbox(), FakeMatchLocalRepository(), harness.identity) { now } }
                    single { UpdateMatchSeat(harness.server, FakeMatchLocalRepository(), harness.identity) }
                    single<IdentityService> { harness.identity }
                    viewModel { harness.viewModel }
                },
            )
        }
        rule.setContent {
            PuzzleBoxTheme {
                DuelsTab(
                    onOpenDuel = {},
                    onOpenReveal = {},
                    onNewDuel = { pushed += it },
                    challenge = challenge,
                    onChallengeTaken = { taken++ },
                    inPane = inPane,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the card pushes the New duel screen`() {
        show()

        rule.onNodeWithTag("duels.challenge").performClick()
        rule.waitForIdle()

        assertEquals(listOf(NewDuelKey()), pushed)
    }

    @Test
    fun `a challenge pushes the screen with its friend and game and is taken once`() {
        show(challenge = sam)

        assertEquals(listOf(NewDuelKey("sam", "Sam", "beta")), pushed)
        assertEquals(1, taken)
    }

    @Test
    fun `in the tablet's pane the card asks the pane for the form, so no screen is pushed`() {
        show(inPane = true)

        rule.onNodeWithTag("duels.challenge").performClick()
        rule.waitForIdle()

        assertEquals(emptyList<NewDuelKey>(), pushed)
    }
}
