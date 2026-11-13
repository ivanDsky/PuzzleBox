package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [GamePageRankRow]: "You #12 of 340" from the board the page already reads, "Leaderboard" without a standing, and a tap that opens the hub. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GamePageRankRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = TestGames.alpha
    private val today = LocalDate.of(2026, 11, 9)
    private val me = Identity(uid = "me", displayName = "Me", photoUrl = null)

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun entries(count: Int, meRank: Int?) = (1..count).map { rank ->
        LeaderboardEntry(
            uid = if (rank == meRank) me.uid else "p$rank",
            displayName = "P$rank",
            puzzleRef = "${game.value}/$today/1",
            timeMs = rank * 10_000L,
            hints = 0,
            completedAtMs = 0L,
        )
    }

    private fun show(identity: FakeIdentityService, rows: List<LeaderboardEntry>, onOpen: () -> Unit = {}) {
        stopKoin() // tests share the JVM-global Koin context
        startKoin {
            modules(
                module {
                    single<GameRegistry> { TestGames.registry(listOf(game)) }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    single<IdentityService> { identity }
                    single<LeaderboardRepository> { FakeLeaderboardRepository(mapOf(LeaderboardRef(game.value, today) to rows)) }
                    single<GoogleIdTokenProvider> { dismissedGoogleSignIn }
                    factory { ObserveLeaderboard(get(), get(), get(), get()) }
                },
                playModule,
            )
        }
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { GamePageRankRow(game, onOpen = onOpen) } }
        composeRule.waitForIdle()
    }

    @Test
    fun `a player inside the top rows reads place and count`() {
        show(FakeIdentityService(me), entries(340, meRank = 2))
        composeRule.onNodeWithTag("summary.rank").assertTextEquals("You #2 of 340")
    }

    @Test
    fun `a player below the top rows reads the pinned place over the whole board`() {
        show(FakeIdentityService(me), entries(340, meRank = 12))
        composeRule.onNodeWithTag("summary.rank").assertTextEquals("You #12 of 340")
    }

    @Test
    fun `signed out or off the board the row is just Leaderboard`() {
        show(FakeIdentityService(), entries(10, meRank = null))
        composeRule.onNodeWithTag("summary.rank").assertTextEquals("Leaderboard")
    }

    @Test
    fun `a tap opens the hub`() {
        var opened = 0
        show(FakeIdentityService(me), entries(10, meRank = 4), onOpen = { opened++ })
        composeRule.onNodeWithTag("summary.rank").performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }
}
