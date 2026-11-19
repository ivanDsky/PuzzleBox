package com.ivandsky.puzzlebox.compete.tab

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import java.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The tablet's Duels pane with no duel: the empty state and its New duel button, which opens the New duel sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class DuelsPaneTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-11-09T10:00:00Z")
    private val navigator = Navigator(NavigationState(PlayKey, listOf(listOf(PlayKey))))

    @Before
    fun startKoinWithNoDuels() {
        val identity = FakeIdentityService(Identity("bob", "Bob", null))
        val server = FakeMatchRepository().apply { signedInUid = "bob" }
        startKoin {
            modules(
                module {
                    viewModel {
                        DuelsTabViewModel(
                            observeMyDuels = ObserveMyDuels(server, FakeSelfMatchRepository(), FakeMatchResultOutbox(), FakeMatchLocalRepository(), identity) { now },
                            updateSeat = UpdateMatchSeat(server, FakeMatchLocalRepository(), identity),
                            identity = identity,
                            clock = { now },
                            releaseFlags = FakeReleaseFlags(),
                        )
                    }
                },
            )
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun show() = rule.setContent {
        PuzzleBoxTheme {
            DuelsPaneHost(navigator, PlayLayout.TwoPane, Modifier) { inviter, _ ->
                Text("sheet for $inviter", Modifier.testTag("duel.new.sheet"))
            }
        }
    }

    @Test
    fun `with no duels the pane says so and offers New duel`() {
        show()
        rule.waitForIdle()

        rule.onNodeWithTag("duels.pane.empty").assertIsDisplayed()
        rule.onNodeWithTag("duels.pane.newDuel").assertIsDisplayed()
        rule.onNodeWithTag("duel.new.sheet").assertDoesNotExist()
    }

    @Test
    fun `New duel opens the New duel sheet for the signed-in player`() {
        show()
        rule.waitForIdle()

        rule.onNodeWithTag("duels.pane.newDuel").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.new.sheet").assertTextEquals("sheet for Bob")
    }
}
