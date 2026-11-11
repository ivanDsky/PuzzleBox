package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.designsystem.MedalBronze
import com.ivandsky.puzzlebox.designsystem.MedalGold
import com.ivandsky.puzzlebox.designsystem.MedalSilver
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.gameui.board.dailyBoardTitle
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
 * [GamePageBoard]: one game's board for a chosen day, with its top rows and the player's own row pinned, and the in-place
 * sign-in when signed out. Rows are told apart by the player names seeded; assertions ride
 * the shared `daily_board.*` tags and the block's own `leaderboard.*` ones.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GamePageBoardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = TestGames.alpha
    private val today = LocalDate.of(2026, 11, 9)
    private val yesterday = today.minusDays(1)
    private val me = Identity(uid = "me", displayName = "Me", photoUrl = null)

    @After
    fun tearDown() {
        stopKoin()
    }

    /** [count] players named `<prefix>1`…, faster with rank; the one at [meRank] is the signed-in player. */
    private fun entries(prefix: String, count: Int, meRank: Int? = null, day: LocalDate = today) = (1..count).map { rank ->
        LeaderboardEntry(
            uid = if (rank == meRank) me.uid else "$prefix$rank",
            displayName = if (rank == meRank) "Me" else "$prefix$rank",
            puzzleRef = "${game.value}/$day/1",
            timeMs = rank * 10_000L,
            hints = 0,
            completedAtMs = 0L,
        )
    }

    private fun repository(vararg boards: Pair<LocalDate, List<LeaderboardEntry>>) =
        FakeLeaderboardRepository(boards.associate { (day, rows) -> LeaderboardRef(game.value, day) to rows })

    private fun show(
        identity: FakeIdentityService,
        repo: FakeLeaderboardRepository,
        google: GoogleIdTokenProvider = dismissedGoogleSignIn,
        picked: LocalDate? = null,
        cardDay: LocalDate? = null,
        linkedIn: Boolean = false,
    ) {
        stopKoin() // tests share the JVM-global Koin context
        startKoin {
            modules(
                module {
                    single<GameRegistry> { TestGames.registry(listOf(game)) }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    single<IdentityService> { identity }
                    single<LeaderboardRepository> { repo }
                    single { google }
                    factory { ObserveLeaderboard(get(), get(), get(), get()) }
                    single { FakeLinkedInDays() }
                    single<LinkedInLink> { FakeLinkedInLink() }
                    factory { ObserveLinkedInDay(get<FakeLinkedInDays>(), get()) }
                    factory { SyncLinkedInDay(get<FakeLinkedInDays>(), 500) }
                    factory { ObserveLinkedInDayView(get(), get(), get()) }
                },
                playModule,
            )
        }
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { GamePageBoard(game, linkedIn = linkedIn, picked = picked, cardDay = cardDay) } }
    }

    /** Whether a board row names [name]. */
    private fun shows(name: String) =
        composeRule.onAllNodes(hasTestTag("daily_board.row.name") and hasText(name)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `top shows the first five and pins the player's lower row`() {
        show(FakeIdentityService(me), repository(today to entries("P", 10, meRank = 8)))
        composeRule.waitForIdle()

        (1..5).forEach { assertTrue("rank $it should show", shows("P$it")) }
        assertFalse("rank 6 is outside Top", shows("P6"))
        composeRule.onNodeWithTag("leaderboard.you").assertExists()
    }

    @Test
    fun `the player's pinned row is the only row of the player on the board`() {
        show(FakeIdentityService(me), repository(today to entries("P", 10, meRank = 8)))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("leaderboard.you").assertExists()
        composeRule.onAllNodes(hasTestTag("daily_board.row.name") and hasText("Me")).assertCountEquals(1)
    }

    @Test
    fun `the player's row inside the top is not pinned again`() {
        show(FakeIdentityService(me), repository(today to entries("P", 10, meRank = 3)))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("leaderboard.you").assertDoesNotExist()
        composeRule.onNodeWithTag("daily_board.row.self").assertExists()
    }

    @Test
    fun `without a row of the player's nothing is pinned`() {
        show(FakeIdentityService(me), repository(today to entries("P", 10)))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("leaderboard.you").assertDoesNotExist()
        assertTrue(shows("P1"))
    }

    @Test
    fun `with no pick the board is the day card's day, titled as yesterday's`() {
        show(
            FakeIdentityService(me),
            repository(today to entries("Today", 3), yesterday to entries("Yest", 3, day = yesterday)),
            cardDay = yesterday,
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Alpha · Sun, Nov 8", substring = true).assertExists()
        assertTrue(shows("Yest1"))
        assertFalse(shows("Today1"))
    }

    @Test
    fun `a day card on today's day titles the board as today's`() {
        show(FakeIdentityService(me), repository(today to entries("Today", 3)), cardDay = today)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Alpha · Sun, Nov 8", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("daily_board.title").assertTextEquals(dailyBoardTitle("Alpha"))
        assertTrue(shows("Today1"))
    }

    @Test
    fun `a picked day shows that day's board under its date, with no day switch`() {
        val picked = LocalDate.of(2026, 11, 6)
        show(
            FakeIdentityService(me),
            repository(today to entries("Today", 3), picked to entries("Old", 3, day = picked)),
            picked = picked,
            cardDay = today,
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Alpha · Fri, Nov 6", substring = true).assertExists()
        assertTrue(shows("Old1"))
        assertFalse(shows("Today1"))
    }

    @Test
    fun `a board nobody is on shows the empty state`() {
        show(FakeIdentityService(me), repository(today to emptyList()))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("daily_board.empty").assertExists()
    }

    @Test
    fun `an empty board of another day is worded for that day`() {
        val picked = LocalDate.of(2026, 11, 6)
        show(FakeIdentityService(me), repository(today to entries("Today", 3), picked to emptyList()), picked = picked, cardDay = today)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("daily_board.empty").assertTextEquals("No one is on this day's board yet.")
    }

    @Test
    fun `an empty board of today keeps the today wording`() {
        show(FakeIdentityService(me), repository(today to emptyList()), cardDay = today)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("daily_board.empty").assertTextEquals("No one is on today's board yet.")
    }

    private fun placeCell(row: Int) = composeRule.onAllNodesWithTag("daily_board.row.rank")[row]

    @Test
    fun `with LinkedIn on the podium places are medals`() {
        show(FakeIdentityService(me), repository(today to entries("P", 4)), linkedIn = true)
        composeRule.waitForIdle()

        assertTrue(placeCell(0).showsMedal(MedalGold))
        assertTrue(placeCell(1).showsMedal(MedalSilver))
        assertTrue(placeCell(2).showsMedal(MedalBronze))
    }

    @Test
    fun `with LinkedIn off no row draws a medal`() {
        show(FakeIdentityService(me), repository(today to entries("P", 4)), linkedIn = false)
        composeRule.waitForIdle()

        placeCell(0).assertTextEquals("1")
        listOf(MedalGold, MedalSilver, MedalBronze).forEach { medal ->
            (0..2).forEach { row -> assertFalse("row $row has a $medal medal", placeCell(row).showsMedal(medal)) }
        }
    }

    @Test
    fun `signed out offers sign-in, and signing in fills the board in place`() {
        val identity = FakeIdentityService()
        show(identity, repository(today to entries("P", 3)), google = object : GoogleIdTokenProvider {
            override suspend fun idToken(activityContext: Context) = GoogleIdToken.Available("token")
        })
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("custom_board.sign_in").assertExists()
        composeRule.onNodeWithTag("daily_board.row").assertDoesNotExist()

        composeRule.onNodeWithTag("custom_board.sign_in").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("custom_board.sign_in").assertDoesNotExist()
        assertTrue(shows("P1"))
    }

    @Test
    fun `a failed sign-in says so`() {
        show(FakeIdentityService(), repository(), google = object : GoogleIdTokenProvider {
            override suspend fun idToken(activityContext: Context) =
                GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL)
        })
        composeRule.onNodeWithTag("custom_board.sign_in").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("custom_board.sign_in.failed").assertExists()
    }
}
