package com.ivandsky.puzzlebox.app.e2e

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithTag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInSessionCookies
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * One LinkedIn data scenario over the real app graph: the account is the scripted [source] and
 * [cookies], Zip is the linked game, and completions go through Room. Room, not the in-memory fake,
 * because only Room decides record versus replay and refuses a second import of one puzzle, which
 * is what these scenarios are about.
 */
internal class LinkedInDataWorld(
    val source: FakeLinkedInAccountSource = FakeLinkedInAccountSource(),
    val cookies: FakeLinkedInSessionCookies = FakeLinkedInSessionCookies(
        LinkedInAccountSession("li_at=FAKE-SENTINEL-NOT-A-COOKIE", "ajax:fake-csrf", "FakeAgent/0"),
    ),
) : AutoCloseable {

    /** The nested data graph that owns Room; closed with the world. */
    private val processes = mutableListOf<KoinApplication>()

    val graph: E2eKoinGraph = shellGraph(
        ReleaseFlag.LinkedInAccount,
        overrides = module {
            single<LinkedInAccountSource> { source }
            single<LinkedInSessionCookies> { cookies }
            // Only ever asked for the completion store: the LinkedIn database and the link file
            // belong to the app graph, and a second DataStore on the same file would throw.
            single(named(ROOM)) {
                val registry = get<GameRegistry>()
                koinApplication {
                    androidContext(RuntimeEnvironment.getApplication())
                    modules(dataModule, module { single { registry } })
                }.also(processes::add).koin
            }
            single<CompletionRepository> { get<Koin>(named(ROOM)).get() }
        },
    )

    init {
        graph.daily.setLinkedInGameType(ZipDefinition.id, ZIP_TYPE)
        graph.daily.setPuzzleNumbers(ZipDefinition.id, ::zipNumber)
        graph.daily.setArchiveRange(ZipDefinition.id, E2E_TODAY.minusDays(ARCHIVE_DAYS)..E2E_TODAY)
    }

    /** Links the account as a finished login would; the login web view cannot run on the JVM. */
    suspend fun connect() {
        graph.koin.get<LinkedInLink>().connect(PROFILE_ID, CONSENT_AT_MS)
    }

    /**
     * What LinkedIn answers for [day]: the player's [own] result and a connections board of [ghosts].
     * Every day a scenario views is scripted, because an unscripted call reads as offline and ends the
     * day's sync before anything is stored.
     */
    fun scriptDay(day: LocalDate, own: LinkedInOwnResult, ghosts: List<LinkedInBoard.Row> = emptyList()) {
        val delta = ChronoUnit.DAYS.between(day, E2E_TODAY).toInt()
        source.respondOwn(PROFILE_ID, ZIP_TYPE, zipNumber(day), LinkedInRead.Ok(own))
        source.respondBoard(PROFILE_ID, ZIP_TYPE, zipNumber(E2E_TODAY), delta, LinkedInRead.Ok(LinkedInBoard(ghosts)))
    }

    override fun close() {
        processes.forEach(KoinApplication::close)
    }

    companion object {
        const val PROFILE_ID = "ACoAAFAKEprofile000"
        const val ZIP = "zip"

        /** Three connections whose names and member refs cannot be mistaken for anything else in a payload. */
        val GHOSTS = listOf(
            LinkedInBoard.Row("urn:li:fsd_profile:GHOST-ADA", "Ghost Ada", 41, hintFree = true, flawless = true),
            LinkedInBoard.Row("urn:li:fsd_profile:GHOST-BEA", "Ghost Bea", 77, hintFree = false, flawless = false),
            LinkedInBoard.Row("urn:li:fsd_profile:GHOST-CYD", "Ghost Cyd", 103, hintFree = true, flawless = false),
        )

        /** Real time allowed for a background write to land; never a wait on its own. */
        const val TIMEOUT_MS = 15_000L
        const val LOOPER_STEP_MS = 100L

        private const val ROOM = "room"
        private const val ZIP_TYPE = 6
        private const val TODAY_NUMBER = 592
        private const val ARCHIVE_DAYS = 10L
        private const val CONSENT_AT_MS = 1_700_000_000_000L

        /** LinkedIn numbers the dailies one per day; today's matches the fixture the page serves. */
        fun zipNumber(day: LocalDate): Int = TODAY_NUMBER - ChronoUnit.DAYS.between(day, E2E_TODAY).toInt()

        /** The ref an import of [day] is stored under, so a seeded play of that day shares its key. */
        fun zipRef(day: LocalDate): PuzzleRef = ZipPuzzleRef(PuzzleKey.Daily(day, zipNumber(day)))

        /** The same ref as the completion store and the leaderboard key it. */
        fun zipRefKey(day: LocalDate): String = "$ZIP/$day/${zipNumber(day)}"
    }
}

internal fun ShellRobot.completions(): CompletionRepository = graph.koin.get()

internal fun ShellRobot.linkedInDb(): LinkedInDatabase = graph.koin.get()

internal fun ShellRobot.link(): LinkedInLink = graph.koin.get()

internal fun ShellRobot.zipStats(): GameStats = runBlocking { graph.koin.get<ObserveGameStats>()(ZipDefinition.id).first() }

/** A play of [day]'s Zip in PuzzleBox itself. */
internal fun ShellRobot.playInApp(day: LocalDate, timeMs: Long) = runBlocking {
    completions().onCompleted(CompletionRecord(LinkedInDataWorld.zipRef(day), time = timeMs, hints = 0, stats = RecordStats(1, "{}")))
}

/** Opens Zip's page, which shows today: both its day card and its board ask for today's LinkedIn data. */
internal fun ShellRobot.openZipPage() {
    if (!shows("library.row.zip")) tapTab(PlayKey)
    tap("library.row.zip")
    awaitTag("library.page.zip")
}

/** Leaves Zip's page and opens it again, as a second look at today. */
internal fun ShellRobot.reopenZipPage() {
    tap("library.page.back")
    openZipPage()
}

/** Picks the past [day] in the page's calendar, so its day card is the one shown. */
internal fun ShellRobot.pickDay(day: LocalDate) {
    tap("library.calendar.day.$day")
    awaitTag("library.day_card.day.$day")
}

/**
 * Waits for [condition] while moving the main looper's clock. The day view holds its LinkedIn read
 * back on that clock, which the compose rule's own wait never advances.
 */
internal fun ShellRobot.awaitLooper(condition: () -> Boolean) {
    rule.waitUntil(LinkedInDataWorld.TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(LinkedInDataWorld.LOOPER_STEP_MS))
        condition()
    }
}

/**
 * Waits until [day]'s LinkedIn read is on disk. The log entry is written after the result it stands
 * for, so once it is there the import and the ghosts are stored too. Boards only reach back a week.
 */
internal fun ShellRobot.awaitSynced(day: LocalDate) {
    val log = linkedInDb().fetchLogDao()
    val items = if (ChronoUnit.DAYS.between(day, E2E_TODAY) <= BOARD_REACH_DAYS) listOf("Own", "Board") else listOf("Own")
    awaitLooper {
        runBlocking { items.all { log.get(LinkedInDataWorld.ZIP, day.toString(), it, LinkedInDataWorld.PROFILE_ID) != null } }
    }
}

/** Every text drawn by the nodes tagged [tag] and the nodes inside them. */
internal fun ShellRobot.textsUnder(tag: String): List<String> =
    rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.texts() }

private fun SemanticsNode.texts(): List<String> =
    config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + children.flatMap { it.texts() }

private const val BOARD_REACH_DAYS = 7L
