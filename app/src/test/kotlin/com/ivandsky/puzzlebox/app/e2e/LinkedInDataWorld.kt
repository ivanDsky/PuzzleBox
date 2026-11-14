package com.ivandsky.puzzlebox.app.e2e

import android.os.Looper
import androidx.activity.ComponentDialog
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.onAllNodesWithTag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
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
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInSessionCookies
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.koin.android.ext.koin.androidContext
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPhotoSource
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * One LinkedIn data scenario over the real app graph: the account is the scripted [source] and
 * [cookies], Zip is the linked game, and completions go through Room. Room, not the in-memory fake,
 * because only Room decides record versus play-again and refuses a second import of one puzzle, which
 * is what these scenarios are about.
 */
internal class LinkedInDataWorld(
    val source: FakeLinkedInAccountSource = FakeLinkedInAccountSource(),
    val cookies: FakeLinkedInSessionCookies = FakeLinkedInSessionCookies(
        LinkedInAccountSession("li_at=FAKE-SENTINEL-NOT-A-COOKIE", "ajax:fake-csrf", "FakeAgent/0"),
    ),
    /** Answers every [GHOSTS] photo; any other address answers nothing, like a photo that could not be fetched. */
    val photos: FakeLinkedInPhotoSource = FakeLinkedInPhotoSource(),
    /** Flags on besides the LinkedIn account's: a scenario on another surface (the Ranks hub) names them. */
    also: List<ReleaseFlag> = emptyList(),
    /** Bindings that replace the graph's, for what that surface reads from a backend. */
    bindings: Module.() -> Unit = {},
) : AutoCloseable {

    /** The nested data graph that owns Room; closed with the world. */
    private val processes = mutableListOf<KoinApplication>()

    val graph: E2eKoinGraph = shellGraph(
        ReleaseFlag.LinkedInAccount,
        *also.toTypedArray(),
        overrides = module {
            bindings()
            single<LinkedInAccountSource> { source }
            single<LinkedInPhotoSource> { photos }
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
        GHOSTS.forEach { photos.respond(checkNotNull(it.pictureUrl), PHOTO) }
        graph.daily.setLinkedInGameType(ZipDefinition.id, ZIP_TYPE)
        graph.daily.setPuzzleNumbers(ZipDefinition.id, ::zipNumber)
        graph.daily.setArchiveRange(ZipDefinition.id, E2E_TODAY.minusDays(ARCHIVE_DAYS)..E2E_TODAY)
    }

    /** Links the account as a finished login would; the login web view cannot run on the JVM. */
    suspend fun connect() {
        graph.koin.get<LinkedInLink>().connect(PROFILE_ID, CONSENT_AT_MS)
    }

    /** Signs [me] in everywhere the graph asks who the player is: the identity and the duels backend. */
    fun signIn(me: Identity) {
        graph.identity.currentIdentity = me
        graph.matches.signedInUid = me.uid
    }

    /** A duel between the signed-in player and [friendUid], which is what makes [friendUid] a friend on the Friends boards. */
    fun befriend(friendUid: String) = runBlocking {
        val me = checkNotNull(graph.identity.currentIdentity)
        val code = checkNotNull(ShareCode.parse(FRIEND_DUEL))
        val leg = MatchLeg(LegRef.Daily(ZipDefinition.id, E2E_TODAY, zipNumber(E2E_TODAY)), null)
        graph.matches.create(MatchDraft(code, DUEL_HOURS, Player(me.uid, me.displayName, null), null, listOf(leg), true, null))
        graph.matches.signedInUid = friendUid
        graph.matches.claim(code, Player(friendUid, friendUid, null))
        graph.matches.signedInUid = me.uid
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
            ghost("ADA", "Ghost Ada", 41, hintFree = true, flawless = true),
            ghost("BEA", "Ghost Bea", 77, hintFree = false, flawless = false),
            ghost("CYD", "Ghost Cyd", 103, hintFree = true, flawless = false),
        )

        private fun ghost(id: String, name: String, seconds: Int, hintFree: Boolean, flawless: Boolean) = LinkedInBoard.Row(
            "urn:li:fsd_profile:GHOST-$id", name, seconds, hintFree, flawless,
            pictureUrl = "https://media.licdn.com/dms/image/v2/GHOST-$id/photo",
        )

        private val PHOTO = byteArrayOf(0x4A, 0x50, 0x47)

        /** Real time allowed for a background write to land; never a wait on its own. */
        const val TIMEOUT_MS = 15_000L
        const val LOOPER_STEP_MS = 100L

        private const val ROOM = "room"
        private const val ZIP_TYPE = 6
        private const val TODAY_NUMBER = 592
        private const val ARCHIVE_DAYS = 10L
        private const val CONSENT_AT_MS = 1_700_000_000_000L
        private const val FRIEND_DUEL = "AAAAA2"
        private const val DUEL_HOURS = 24

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

/** Waits for [read] to give [expected], then asserts it: a screen that never gets there fails with what it shows instead. */
internal fun <T> ShellRobot.awaitEquals(expected: T, read: () -> T) {
    runCatching { awaitLooper { read() == expected } }.onFailure { if (it !is ComposeTimeoutException) throw it }
    assertEquals(expected, read())
}

/** Opens the Ranks hub at Zip's board from the row that stands for the board on Zip's page while the hub is on. */
internal fun ShellRobot.openZipRanks() {
    tap("summary.rank")
    awaitTag("route.ranks")
}

/** Picks [scope] (Friends, Global) and [window] (Day, Week, Month) on the hub's tune sheet, then closes the sheet. */
internal fun ShellRobot.tuneRanks(scope: String, window: String) {
    tap("ranks.tune")
    tap("ranks.tune.scope.$scope")
    tap("ranks.tune.window.$window")
    closeSheet()
}

/** System back while a bottom sheet is up: the sheet is a window of its own, so the press is the sheet's, not the screen's under it. */
internal fun ShellRobot.closeSheet() {
    rule.runOnUiThread { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
    rule.waitForIdle()
}

/** Picks Overall, the first board of the hub's title menu. */
internal fun ShellRobot.pickOverallRanks() {
    tap("ranks.title")
    tap("ranks.menu.item.0")
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

/** What each node tagged [tag] says itself, one text a node: a place cell says its place once, whatever is drawn inside it. */
internal fun ShellRobot.textsAt(tag: String): List<String> =
    rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } }

private const val BOARD_REACH_DAYS = 7L
