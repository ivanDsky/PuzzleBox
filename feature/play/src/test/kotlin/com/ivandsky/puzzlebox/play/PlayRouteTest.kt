package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.TangoDailyEntry
import com.ivandsky.puzzlebox.data.repository.TangoDailyRepository
import com.ivandsky.puzzlebox.data.repository.TangoToday
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.data.repository.ZipToday
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.play.di.playModule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * tango-05 route wiring: the picker is the play tab's fresh-entry state (A14) — both
 * registered games listed, picking starts THAT game's flow in place — and a nav-carried
 * [SessionBlob] replay restores through the named game's codec (ADR-0005).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val day: LocalDate = LocalDate.parse("2026-10-31")

    /** Production [TangoPlayState.today] is the REAL cadence date — the history row it
     *  resolves must name that same day (the repository rows the day it captures). */
    private val cadenceToday: LocalDate =
        LocalDate.now((com.ivandsky.puzzlebox.games.tango.TangoDefinition.cadence as com.ivandsky.puzzlebox.game.Cadence.Daily).zone)

    private fun koinWith(
        zips: ZipDailyRepository,
        tangos: TangoDailyRepository,
        completions: CompletionRepository,
    ): KoinApplication {
        val data = module {
            single { zips }
            single { tangos }
            single { completions }
            // The picker names games from the plugin list (A14) — both games registered.
            single<List<GamePlugin<*, *, *>>> { listOf(zipPlugin, tangoPlugin) }
        }
        stopKoin() // tests share the JVM-global Koin context
        return startKoin { modules(data, playModule) }
    }

    private var handedOff: List<Pair<Long, Int>> = emptyList()

    private fun setContent(koin: KoinApplication, restoredSession: ByteArray? = null, onBack: () -> Unit = {}) {
        handedOff = emptyList()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PlayRoute(
                    layout = PlayLayout.SinglePane,
                    onBack = onBack,
                    onSolved = { timeMs, hintsUsed -> handedOff = handedOff + (timeMs to hintsUsed) },
                    restoredSession = restoredSession,
                )
            }
        }
    }

    @Test
    fun `fresh entry shows the picker with both registered games`() {
        val koin = koinWith(
            FakeZipDailyRepository(ZipToday.Fresh(PlayTestSupport.envelope())),
            FakeTangoDailyRepository(todayResult = TangoToday.Fresh(TangoPlayFixture.envelope())),
            FakeCompletionRepository(),
        )
        try {
            setContent(koin)
            composeRule.onNodeWithTag("play.picker").assertExists()
            composeRule.onNodeWithText("Pick a game").assertExists()
            composeRule.onNodeWithTag("play.picker_zip").assertExists()
            composeRule.onNodeWithTag("play.picker_tango").assertExists()
            composeRule.onNodeWithText("Zip").assertExists()
            composeRule.onNodeWithText("Tango").assertExists()
        } finally {
            koin.close()
        }
    }

    @Test
    fun `picking tango starts the tango screen in place`() {
        val tangoRepo = FakeTangoDailyRepository(
            todayResult = TangoToday.Fresh(TangoPlayFixture.envelope()),
            history = listOf(TangoDailyEntry(cadenceToday, 301, "tango/n6", 0L)),
        )
        val koin = koinWith(
            FakeZipDailyRepository(ZipToday.Fresh(PlayTestSupport.envelope())),
            tangoRepo,
            FakeCompletionRepository(),
        )
        try {
            setContent(koin)
            composeRule.onNodeWithTag("play.picker_tango").performClick()
            composeRule.waitForIdle()

            // The tango play screen replaced the picker; today's board loaded in.
            composeRule.runOnIdle {
                val playState = koin.koin.get<PlayFlow>().state as TangoPlayState
                assertTrue(playState.screen is TangoScreen.Ready)
                assertEquals(TangoPuzzleRef(cadenceToday, 301), playState.session?.ref)
            }
            composeRule.onNodeWithText("Tango").assertExists()
            composeRule.onNodeWithTag("play.side_panel").assertExists()
            composeRule.onNodeWithText("Daily · #301").assertExists()
            composeRule.onNodeWithTag("play.picker").assertDoesNotExist()
        } finally {
            koin.close()
        }
    }

    @Test
    fun `a tango replay blob starts tango and restores the archived session`() {
        val session = TangoSession(
            ref = TangoPuzzleRef(LocalDate.parse("2026-03-09"), 12),
            puzzle = TangoPlayFixture.puzzle,
            completedAt = java.time.Instant.parse("2026-03-09T12:00:00Z"),
        )
        val blob = SessionBlob.encode(
            com.ivandsky.puzzlebox.games.tango.TangoDefinition.id,
            TangoSessionCodec.serialize(session),
        )
        val koin = koinWith(
            FakeZipDailyRepository(ZipToday.Fresh(PlayTestSupport.envelope())),
            FakeTangoDailyRepository(todayResult = TangoToday.Unavailable(com.ivandsky.puzzlebox.game.FetchError.Throttled())),
            FakeCompletionRepository(),
        )
        try {
            setContent(koin, restoredSession = blob)

            // Restored COMPLETED session → the win handoff fires into navigation (the
            // app shell renders the result route; here it's a stub) and the archive
            // was NEVER re-fetched (the blob carried the puzzle, A19).
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                val playState = koin.koin.get<PlayFlow>().state as TangoPlayState
                assertTrue(
                    "screen=${playState.screen::class.simpleName} " +
                        "complete=${playState.session?.isComplete} " +
                        "ref=${playState.session?.ref} " +
                        "done=${playState.session?.completedAt != null}",
                    playState.session?.isComplete ?: false,
                )
                assertEquals(1, handedOff.size)
            }
            composeRule.onNodeWithText("Tango").assertExists()
            composeRule.onNodeWithTag("play.picker").assertDoesNotExist()
        } finally {
            koin.close()
        }
    }

    @Test
    fun `a zip replay blob still restores through the zip codec`() {
        val zipSession = com.ivandsky.puzzlebox.games.zip.ZipSession(
            ref = com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef(LocalDate.parse("2026-03-09"), 12),
            puzzle = PlayTestSupport.puzzle(),
        )
        val blob = SessionBlob.encode(
            com.ivandsky.puzzlebox.games.zip.ZipDefinition.id,
            ZipSessionCodec.serialize(zipSession),
        )
        val zipRepo = FakeZipDailyRepository(ZipToday.Fresh(PlayTestSupport.envelope()))
        val koin = koinWith(
            zipRepo,
            FakeTangoDailyRepository(todayResult = TangoToday.Fresh(TangoPlayFixture.envelope())),
            FakeCompletionRepository(),
        )
        try {
            setContent(koin, restoredSession = blob)
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Zip").assertExists()
            composeRule.onNodeWithTag("play.picker").assertDoesNotExist()
            assertEquals(0, zipRepo.todayCalls) // restored, not fetched
        } finally {
            koin.close()
        }
    }

    @Test
    fun `back from a mid-game play releases the flow to the picker`() {
        val koin = koinWith(
            FakeZipDailyRepository(ZipToday.Fresh(PlayTestSupport.envelope())),
            FakeTangoDailyRepository(todayResult = TangoToday.Fresh(TangoPlayFixture.envelope())),
            FakeCompletionRepository(),
        )
        try {
            var back = 0
            setContent(koin, onBack = { back++ })
            composeRule.onNodeWithTag("play.picker_zip").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Zip").assertExists()

            // Device/system back through PlayRoute's BackHandler: the route pops AND
            // the flow releases, so the next play entry picks again (A14).
            (composeRule as androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>).activity.onBackPressedDispatcher.onBackPressed()
            composeRule.waitForIdle()
            assertEquals(1, back)
            composeRule.onNodeWithTag("play.picker").assertExists()
        } finally {
            koin.close()
        }
    }
}
