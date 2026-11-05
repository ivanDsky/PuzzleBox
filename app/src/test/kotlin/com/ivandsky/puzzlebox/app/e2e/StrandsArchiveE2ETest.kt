package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation3.runtime.NavKey
import androidx.room.Room
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.NytTransport
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.strandsModule
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.network.FakeNytTransport
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Strands archive over the REAL data path: production `DailyRepository` and
 * `SavedPlayRepository` over in-memory Room, with the real NYT source and trust gate reading
 * committed fixtures through [FakeNytTransport]. A past day opened from "Past puzzles" costs
 * one request, reopens from the cache with the network gone and leaves the streak alone
 * (SC6, SC7); nine archive days later the Zip save is still there, because the prune caps
 * each game on its own (SC16).
 */
@RunWith(RobolectricTestRunner::class)
// A phone-sized window, so the picker dialog and the 8-row board both fit.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class StrandsArchiveE2ETest {

    private val transport = FakeNytTransport()

    /** The "disk": one data graph per test, so the in-memory database outlives a relaunch. */
    private var data: KoinApplication? = null

    @After
    fun closeData() {
        data?.close()
    }

    @Test
    fun `SC6 SC7 a past day costs one request, reopens offline from the cache, and leaves the streak alone`() =
        shellRobot(playHub, today = TODAY, graph = dataGraph()) {
            serve(TODAY, PAST)
            // Today's board captures today, so the streak has a day to protect.
            openGame(STRANDS)
            awaitStrands(TODAY)
            back()
            val before = history()
            assertEquals("today alone makes the streak", "1", before.streak)
            assertEquals("today's row only", setOf(rowTag(TODAY)), before.rows)

            // Browsing the picker and backing out fetches and captures nothing.
            tap(PAST_BUTTON)
            pickDay(BROWSED)
            rule.onNodeWithText("Cancel").performClick()
            rule.waitForIdle()
            assertEquals("a browsed day is never fetched", 0, requestsFor(BROWSED))
            assertEquals("browsing adds no History rows", before, history())

            openPast(PAST)
            awaitStrands(PAST)
            assertEquals("the past day is fetched once", 1, requestsFor(PAST))
            val opened = history()
            assertEquals("…and adjacent to today, still outside the streak", before.streak, opened.streak)
            assertEquals("the opened day is captured", before.rows + rowTag(PAST), opened.rows)

            relaunch()
            offline(TODAY, PAST)
            openPast(PAST)
            awaitStrands(PAST)
            assertEquals("the reopen reads the cache, not the network", 1, requestsFor(PAST))
            assertEquals("the streak and rows are unchanged after the reopen", opened, history())
        }

    @Test
    fun `SC16 nine archive days evict the oldest Strands save and keep the Zip one`() =
        shellRobot(playHub, today = TODAY, graph = dataGraph()) {
            val zipSlot = PlaySlot(ZipDefinition.id, TODAY)
            runBlocking { saves.save(zipSlot, byteArrayOf(1), elapsedMs = 0L) }
            serve(*ARCHIVE.toTypedArray())

            ARCHIVE.forEach { day ->
                openPast(day)
                awaitStrands(day)
                // An untouched board is never written (only a committed change saves): find one word.
                tapWord(firstThemeWord(day))
                back()
                // Saves are ordered by their wall-clock stamp; keep each one strictly newer.
                Thread.sleep(SAVE_GAP_MS)
            }
            background()

            val kept = runBlocking { saves.observeInProgress().first() }.keys
            val strandsSlots = ARCHIVE.map { PlaySlot(StrandsDefinition.id, it) }
            assertEquals(
                "Zip's save plus the newest 8 Strands saves",
                setOf(zipSlot) + strandsSlots.drop(1),
                kept,
            )
            foreground()
        }

    private val ShellRobot.saves: SavedPlayRepository get() = graph.koin.get()

    private fun ShellRobot.awaitStrands(day: LocalDate) {
        val slot = PlaySlot(StrandsDefinition.id, day)
        rule.waitUntil(TIMEOUT_MS) {
            rule.runOnIdle { graph.koin.get<PlaySessionStore>().peek(slot) }
                ?.let { (it.controller as StrandsPlayController).session } != null
        }
        awaitTag(BOARD)
    }

    /** Taps each letter, then the last one again: the double-tap submit. */
    private fun ShellRobot.tapWord(path: List<Pair<Int, Int>>) {
        (path + path.last()).forEach { (row, col) ->
            rule.onNodeWithTag("strands.cell.$row.$col").performScrollTo().performClick()
        }
        rule.waitForIdle()
    }

    /** The fixture's first theme word, as the `[row, col]` cells NYT lists for it. */
    private fun firstThemeWord(day: LocalDate): List<Pair<Int, Int>> {
        val wire = JSONObject(fixture(day))
        val word = wire.getJSONArray("themeWords").getString(0)
        val cells = wire.getJSONObject("themeCoords").getJSONArray(word)
        return (0 until cells.length()).map { i -> cells.getJSONArray(i).let { it.getInt(0) to it.getInt(1) } }
    }

    /** "Past puzzles" on the hub, then the day in the picker and Open. */
    private fun ShellRobot.openPast(day: LocalDate) {
        tap(PAST_BUTTON)
        pickDay(day)
        rule.onNodeWithText("Open").performClick()
        rule.waitForIdle()
    }

    /** Selects [day] in the open picker without confirming it. */
    private fun ShellRobot.pickDay(day: LocalDate) {
        if (day.year == TODAY.year && day.month == TODAY.month) {
            // A semantic tap (TalkBack's): under Robolectric an injected touch does not select a day cell.
            rule.onNode(hasText(day.format(CELL_DATE), substring = true) and isButton)
                .performSemanticsAction(SemanticsActions.OnClick)
        } else {
            // Another month: type the date rather than page the calendar back month by month.
            rule.onNode(hasContentDescription("Switch to text input mode")).performClick()
            rule.onNode(hasSetTextAction()).performTextReplacement(day.format(TYPED_DATE))
        }
        rule.waitForIdle()
    }

    private data class HistoryView(val streak: String, val rows: Set<String>)

    /** The History list from Home's "See all": streak count and row tags; returns to the Play tab after. */
    private fun ShellRobot.history(): HistoryView {
        tapTab(HomeKey)
        tapText("See all")
        awaitTag(STREAK)
        val count = rule.onNodeWithTag(STREAK, useUnmergedTree = true).performScrollTo().fetchSemanticsNode()
            .children.flatMap { generateSequence(listOf(it)) { level -> level.flatMap { c -> c.children }.takeIf(List<*>::isNotEmpty) }.flatten() }
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }
            .first()
        val rows = rule.onAllNodes(SemanticsMatcher("a History row") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ROW_PREFIX) == true
        }).fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }.toSet()
        back()
        tapTab(PlayKey)
        return HistoryView(count, rows)
    }

    private fun rowTag(day: LocalDate) = "$ROW_PREFIX$STRANDS/$day"

    private fun serve(vararg days: LocalDate) = days.forEach { day ->
        transport.respond(path(day), FetchResult.Ok(fixture(day)))
    }

    private fun offline(vararg days: LocalDate) = days.forEach { day ->
        transport.respond(path(day), FetchResult.Err(FetchError.Unavailable("offline")))
    }

    private fun requestsFor(day: LocalDate) = transport.requests.count { it == path(day) }

    private fun path(day: LocalDate) = "svc/strands/v2/$day.json"

    private fun fixture(day: LocalDate): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/strands/$day.json")) { "no fixture for $day" }.readText()

    /**
     * The E2E graph with the production daily and saves repositories, both from [dataKoin]:
     * in-memory Room, the real Strands source over [transport], and the shell's registry.
     */
    private fun dataGraph() = E2eKoinGraph(
        TODAY,
        overrides = module {
            single<DailyRepository> { dataKoin(get(), get()).get() }
            single<SavedPlayRepository> { dataKoin(get(), get()).get() }
        },
    )

    private fun dataKoin(registry: GameRegistry, calendar: GameCalendar): Koin = (
        data ?: koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                commonModule,
                dataModule,
                strandsModule,
                module {
                    single<NytTransport> { transport }
                    single { Room.inMemoryDatabaseBuilder(get(), PuzzleBoxDatabase::class.java).build() }
                    single { registry }
                    single<GameCalendar> { CapturedToday(calendar) }
                },
            )
        }.also { data = it }
        ).koin

    /**
     * The repository stamps a capture with the wall clock, while the shell's calendar is pinned
     * to [TODAY]: without this, a day after the real date could never read as fetched after
     * itself. Every capture in a run happens on the pinned today.
     */
    private class CapturedToday(private val calendar: GameCalendar) : GameCalendar by calendar {
        override fun dayAt(game: GameId, epochMs: Long): LocalDate? = calendar.today(game)
    }

    private val playHub = NavigationState(
        tab = PlayKey,
        stacks = listOf<List<NavKey>>(listOf(HomeKey), listOf(PlayKey), listOf(ProfileKey)),
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 6)
        val PAST: LocalDate = TODAY.minusDays(1)

        /** Selected in the picker, then cancelled: a fetch would show up in the request log. */
        val BROWSED: LocalDate = TODAY.minusDays(2)

        /** Every committed archive fixture before [PAST], oldest first: nine saves, one past the cap. */
        val ARCHIVE: List<LocalDate> = listOf(
            "2024-03-04", "2024-03-05", "2024-10-07", "2024-11-08", "2024-12-31",
            "2025-01-01", "2025-05-20", "2025-09-13", "2026-02-14",
        ).map(LocalDate::parse)

        const val STRANDS = "strands"
        const val TIMEOUT_MS = 5_000L
        const val BOARD = "strands.board"
        const val PAST_BUTTON = "hub.past.$STRANDS"
        const val STREAK = "history.streak"
        const val ROW_PREFIX = "history.row."
        const val SAVE_GAP_MS = 5L
        val CELL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
        val TYPED_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMddyyyy", Locale.US)
        val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    }
}
