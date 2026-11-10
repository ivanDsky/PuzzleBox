package com.ivandsky.puzzlebox.compete.duel

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.compete.create.DuelSignIn
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
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

/** Who the invite card says the duel belongs to: the challenger sees their own duel, everyone else an invitation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuelInviteCardTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)

    @Before
    fun startKoinWithNoGames() {
        startKoin { modules(module { single { GamePlayRegistry(emptyList()) } }) }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun details(viewerIsChallenger: Boolean) = InviteDetails(
        challenger = ann,
        games = listOf(InviteGame(GameId("zip"), "Zip")),
        rounds = 3,
        deadline = Instant.parse("2026-11-10T10:00:00Z"),
        viewerIsChallenger = viewerIsChallenger,
        declined = false,
        solvedBefore = false,
        canDecline = false,
    )

    private fun show(kind: InviteKind, details: InviteDetails?) {
        val invite = DuelUiState.Invite(kind, code, details, working = false, notice = null, signIn = DuelSignIn.Idle)
        rule.setContent {
            PuzzleBoxTheme { DuelInviteCard(invite, InviteActions({}, {}, {}, {}, {})) }
        }
    }

    private fun assertShown(text: String) = rule.onAllNodesWithText(text).assertCountEquals(1)

    private fun assertAbsent(text: String) = rule.onAllNodesWithText(text).assertCountEquals(0)

    @Test
    fun theChallengerSeesOwnDuelAsYourDuel() {
        show(InviteKind.Waiting, details(viewerIsChallenger = true))

        assertShown("Your duel")
        assertShown("You challenged a friend")
        assertAbsent("Duel · from a friend")
        assertAbsent("Ann challenges you")
        rule.onNodeWithTag("duel.invite.headline").assertTextEquals("You challenged a friend")
    }

    @Test
    fun anInviteeSeesADuelFromTheChallengerByName() {
        show(InviteKind.Open, details(viewerIsChallenger = false))

        assertShown("Duel · from a friend")
        assertShown("Ann challenges you")
        assertAbsent("Your duel")
        assertAbsent("You challenged a friend")
    }

    @Test
    fun aSignedOutViewerSeesTheInviteeFormWithoutAName() {
        show(InviteKind.SignedOut, details = null)

        assertShown("Duel · from a friend")
        assertShown("A friend challenges you")
        assertAbsent("Your duel")
        assertAbsent("You challenged a friend")
    }
}
