package com.ivandsky.puzzlebox.data.linkedin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ivandsky.puzzlebox.database.linkedin.FetchLogEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.datastore.LinkedInLinkDataSource
import com.ivandsky.puzzlebox.datastore.LinkedInLinkPrefs
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInProgress
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInResultPoster
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInResultPoster
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInSessionCookies
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.robolectric.RuntimeEnvironment

/**
 * The real post door over real Room and DataStore, with scripted LinkedIn ports. Its world is one game day:
 * [game]'s daily puzzle 712 of [today], which [postable] solves in the app in 83 s as profile `me`.
 */
internal class LinkedInPostRig(
    private val test: TestScope,
    private val root: File,
    cookies: LinkedInSessionCookies = FakeLinkedInSessionCookies(null),
    callLimit: Int = 100,
) {
    val game: GameId = ReplayFixtures.GAME
    val today: LocalDate = LocalDate.of(2026, 11, 5)
    val source = FakeLinkedInAccountSource()
    val poster = FakeLinkedInResultPoster()
    val completions = FakeCompletionRepository()
    val calendar = FakeGameCalendar(today)
    val daily = FakeDailyRepository().apply {
        setLinkedInGameType(game, 6)
        setLinkedInGamePath(game, "games/fixture/")
        setCached(game, today, TestGames.puzzle(game, tag = 12))
    }
    val budget = LinkedInCallBudget({ NOW }, limit = callLimit)
    var registry = GameRegistry(listOf(TestGames.encoderPlugin()))

    private val storeLock = LinkedInStoreLock()
    private val process = CoroutineScope(UnconfinedTestDispatcher(test.testScheduler) + SupervisorJob())
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(RoomQueryThread.executor)
        .build()
    private val data = LinkedInLinkDataSource(
        PreferenceDataStoreFactory.create(scope = process) { File(root, "linkedin_link.preferences_pb") },
    )

    val link: LinkedInLink = DataStoreLinkedInLink(data, db, cookies, testAvatars(File(root, "linkedin_avatars")), storeLock)
    val posts: LinkedInPosts by lazy { door() }

    /**
     * A door over the same stores, which is also what the next process start has. Its app scope is a
     * supervisor, as the app's is, so a post that throws fails its callers and not the scope.
     */
    fun door(
        source: LinkedInAccountSource = this.source,
        poster: LinkedInResultPoster = this.poster,
    ): LinkedInPosts = LinkedInPostFlow(
        link = link,
        source = source,
        poster = poster,
        completions = completions,
        daily = daily,
        calendar = calendar,
        registry = registry,
        log = LinkedInPostLog(data),
        fetchLog = db.fetchLogDao(),
        budget = budget,
        storeLock = storeLock,
        boardAfterPost = RoomLinkedInDays(
            link, source, completions, daily, calendar, registry, db, budget, testAvatars(File(root, "linkedin_avatars")), storeLock,
            LinkedInPostLog(data), FakeOwnerScope(),
        ) { NOW },
        scope = CoroutineScope(test.backgroundScope.coroutineContext + SupervisorJob(test.backgroundScope.coroutineContext.job)),
    )

    /** Linked with posting on, today's solve stored with its replay, and LinkedIn answering a day it has no result for, whose post lands. */
    suspend fun postable() {
        link.connect("me", 1_000L)
        link.setPosting(2_000L)
        completions.onCompleted(SOLVE, ReplayFixtures.sealed(12))
        source.respondOwn("me", 6, 712, LinkedInRead.Ok(LinkedInOwnResult.NotPlayed))
        poster.pageReply = LinkedInRead.Ok(PAGE)
        poster.sendReply = LinkedInSendResult.Posted
    }

    suspend fun submit(): LinkedInPostState = posts.submit(game, today)

    val sends: Int get() = poster.calls.count { it == "send" }

    /** What the link file holds: the link, the consent and the post log. */
    suspend fun stored(): LinkedInLinkPrefs = data.link.first()

    suspend fun logOwnRead() {
        db.fetchLogDao().put(FetchLogEntity("fixture", "2026-11-05", "Own", "me", fetchedAtMs = 5_000L, outcome = "NotPlayed"))
    }

    suspend fun ownRead(): FetchLogEntity? = db.fetchLogDao().get("fixture", "2026-11-05", "Own", "me")

    /** Today has a puzzle number, so its board can be addressed, and LinkedIn answers the board read with [board]. */
    fun boardable(board: LinkedInRead<LinkedInBoard> = LinkedInRead.Ok(BOARD)) {
        daily.setPuzzleNumbers(game) { 712 }
        source.respondBoard("me", 6, 712, 0, board)
    }

    /** The day's board as the day card's sync left it. */
    suspend fun logBoardRead(fetchedAtMs: Long) {
        db.fetchLogDao().put(FetchLogEntity("fixture", "2026-11-05", "Board", "me", fetchedAtMs = fetchedAtMs, outcome = "BoardToday"))
    }

    /** Every board read so far. */
    val boardReads: Int get() = source.calls.count { it.startsWith("board:") }

    /** The connections the stored board of today holds, by member. */
    suspend fun storedGhosts(): List<String> = db.ghostDao().board("fixture", "2026-11-05").map { it.memberRef }

    fun close() {
        process.cancel()
        db.close()
    }

    companion object {
        const val NOW = 1_800_000_000_000L
        const val REF = "fixture/2026-11-05"
        val SOLVE = CompletionRecord(
            puzzleRef = TestGames.Ref(ReplayFixtures.GAME, PuzzleKey.Daily(LocalDate.of(2026, 11, 5), 712)),
            time = 83_000L,
            hints = 0,
            stats = RecordStats(1, "{}"),
            isRecord = true,
        )
        val PAGE = LinkedInGamePage(
            puzzleId = 712,
            profileId = "me",
            progress = LinkedInProgress.Fresh,
            isTutorial = false,
            totalPlays = 40,
            wins = 38,
            requestTime = 1_762_300_800_000,
        )
        /** A board that holds the player's own row, as LinkedIn serves it once the day's result is there, and one connection. */
        val BOARD = LinkedInBoard(
            listOf(
                LinkedInBoard.Row("friend", "Friend", timeSeconds = 70, hintFree = true, flawless = true),
                LinkedInBoard.Row("me", "Me", timeSeconds = 83, hintFree = true, flawless = true),
            ),
        )
        val POST = LinkedInPost(
            gamePath = "games/fixture/",
            gameTypeId = 6,
            puzzleNumber = 712,
            timeMs = 83_000L,
            hintUsed = false,
            play = LinkedInPlay(
                states = mapOf("placed" to LinkedInPlayValue.Number(12)),
                payloadKeys = mapOf("playRecord" to "placed"),
                mistakeFree = true,
            ),
        )
    }
}
