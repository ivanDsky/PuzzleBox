package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.play.domain.FinishLeg
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Studio's test solve and a duel puzzle never get the tutorial gate and a game whose
 * lessons are unseen: both draw the board straight, with no How to play.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SandboxAndDuelNoTutorialTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val alpha = TestGames.alpha
    private val fakes = FakeGames(listOf(alpha)).also { it[alpha].teach(2) }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun `the Studio's test solve shows its board with no lessons and no how to play`() {
        stopKoin() // tests share the JVM-global Koin context
        startKoin { modules(module {}) }
        val ui = fakes[alpha]
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Job()), onSessionChanged = {}, onWon = {}))
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                SandboxPlay(ui, controller, title = "Castle", label = "Test solve", layout = PlayLayout.SinglePane, onBack = {}, onSolved = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithTag("play.how_to_play").assertDoesNotExist()
    }

    @Test
    fun `a duel puzzle shows its board with no lessons and no how to play`() {
        val code = checkNotNull(ShareCode.parse("M8T3VQ"))
        val day = TestGames.definition(alpha).cadence.today()
        // A resumed leg restores from its saved row, so the board needs no match document.
        val saved = FakeSavedPlayRepository()
        runBlocking {
            saved.save(PlaySlot.Leg(alpha, code.value, 0), SessionBlob.encode(alpha, "fake|0|false|$day|1|0".encodeToByteArray()), elapsedMs = 0L)
        }
        stopKoin()
        startKoin {
            modules(
                commonModule,
                module {
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { saved }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<DailyRepository> { FakeDailyRepository() }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<GameCalendar> { FakeGameCalendar(day) }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    single<MatchResultOutbox> { FakeMatchResultOutbox() }
                    single<IdentityService> { FakeIdentityService(initial = Identity("ann-uid", "Ann", photoUrl = null)) }
                    single<SelfMatchRepository> { FakeSelfMatchRepository() }
                },
                module { single<CloudSync> { FakeCloudSync() } },
                playModule,
                // After playModule, whose own FinishLeg would replace this one.
                module { factory { FinishLeg(get(), FakeSelfMatchRepository(), get()) { Instant.EPOCH } } },
                hubOffFriendDay,
                flagsOff,
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                GameRoute(
                    MatchLegKey(code.value, 0, alpha.value), PlayLayout.SinglePane,
                    onBack = {}, onSolved = { _, _ -> }, onGaveUp = {}, onTaken = {},
                )
            }
        }
        composeRule.waitUntil(BOARD_TIMEOUT_MS) {
            composeRule.onAllNodesWithText("alpha board 0").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("alpha board 0").assertExists()
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithTag("play.how_to_play").assertDoesNotExist()
    }

    private companion object {
        const val BOARD_TIMEOUT_MS = 5_000L
    }
}
