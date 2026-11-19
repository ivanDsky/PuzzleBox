package com.ivandsky.puzzlebox.data.trainer

import androidx.room.Room
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.TrainerProgressEntity
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [RoomTrainerProgressRepository] over real in-memory Room: the visible-owner filter, the merge of a signed-out row
 * and the owner's, writes under the writer uid, atomic updates, and the stored words of a mark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomTrainerProgressRepositoryTest {

    private val queens = GameId("queens")
    private val tango = GameId("tango")
    private val lineLock = TechniqueId("line-lock")
    private val pairs = TechniqueId("pairs")
    private val scope = FakeOwnerScope()
    private lateinit var db: PuzzleBoxDatabase
    private lateinit var repository: RoomTrainerProgressRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        repository = RoomTrainerProgressRepository(db.trainerProgressDao(), scope)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun answerAs(
        uid: String?,
        technique: TechniqueId = lineLock,
        game: GameId = queens,
        progress: TechniqueProgress,
    ) {
        scope.writerUid = uid
        repository.update(game, technique) { progress }
    }

    private suspend fun seen(game: GameId = queens) = repository.observe(game).first()

    @Test
    fun `a technique nothing was recorded for is absent`() = runTest {
        assertEquals(emptyMap<TechniqueId, TechniqueProgress>(), seen())
    }

    @Test
    fun `every mark, the count and the time round trip`() = runTest {
        val all = mapOf(
            TechniqueId("a") to TechniqueProgress(Mark.New, 0, 0L),
            TechniqueId("b") to TechniqueProgress(Mark.Practicing, 3, 1_700_000_000_123L),
            TechniqueId("c") to TechniqueProgress(Mark.Mastered, 5, Long.MAX_VALUE),
        )

        for ((technique, progress) in all) repository.update(queens, technique) { progress }

        assertEquals(all, seen())
    }

    @Test
    fun `update sees NEW on an absent technique and returns what it stored`() = runTest {
        var before: TechniqueProgress? = null

        val stored = repository.update(queens, lineLock) { before = it; TechniqueProgress(Mark.Practicing, 1, 9) }

        assertEquals(TechniqueProgress.NEW, before)
        assertEquals(TechniqueProgress(Mark.Practicing, 1, 9), stored)
        assertEquals(mapOf(lineLock to stored), seen())
    }

    @Test
    fun `update sees the stored value and replaces it`() = runTest {
        repository.update(queens, lineLock) { TechniqueProgress(Mark.Practicing, 2, 10) }

        val stored = repository.update(queens, lineLock) { it.copy(inRow = it.inRow + 1, answeredAtMs = 20) }

        assertEquals(TechniqueProgress(Mark.Practicing, 3, 20), stored)
        assertEquals(mapOf(lineLock to stored), seen())
        assertEquals(1, db.trainerProgressDao().observe("queens", null).first().size)
    }

    @Test
    fun `a game sees only its own techniques`() = runTest {
        repository.update(queens, lineLock) { TechniqueProgress(Mark.Practicing, 1, 9) }
        repository.update(tango, pairs) { TechniqueProgress(Mark.Mastered, 5, 9) }

        assertEquals(setOf(lineLock), seen(queens).keys)
        assertEquals(setOf(pairs), seen(tango).keys)
    }

    @Test
    fun `an owner never sees another owner's rows`() = runTest {
        answerAs("b", pairs, progress = TechniqueProgress(Mark.Mastered, 5, 30))
        answerAs("a", lineLock, progress = TechniqueProgress(Mark.Practicing, 1, 20))

        scope.visibleOwner = "a"
        assertEquals(mapOf(lineLock to TechniqueProgress(Mark.Practicing, 1, 20)), seen())

        scope.visibleOwner = "b"
        assertEquals(mapOf(pairs to TechniqueProgress(Mark.Mastered, 5, 30)), seen())
    }

    @Test
    fun `a signed-out row is visible to every owner`() = runTest {
        answerAs(null, progress = TechniqueProgress(Mark.Practicing, 1, 20))

        scope.visibleOwner = "a"
        assertEquals(setOf(lineLock), seen().keys)
        scope.visibleOwner = "b"
        assertEquals(setOf(lineLock), seen().keys)
    }

    @Test
    fun `with no visible owner every row is seen`() = runTest {
        answerAs("a", lineLock, progress = TechniqueProgress(Mark.Practicing, 1, 20))
        answerAs("b", pairs, progress = TechniqueProgress(Mark.Practicing, 1, 20))

        scope.visibleOwner = null

        assertEquals(setOf(lineLock, pairs), seen().keys)
    }

    @Test
    fun `a signed-out row and the owner's row for one technique read as their merge`() = runTest {
        answerAs(null, progress = TechniqueProgress(Mark.Mastered, 5, 10))
        answerAs("a", progress = TechniqueProgress(Mark.Practicing, 2, 20))

        scope.visibleOwner = "a"

        // The higher mark, with the count and time of the later answer: the screen never shows the lower value.
        assertEquals(mapOf(lineLock to TechniqueProgress(Mark.Mastered, 2, 20)), seen())
        assertEquals(2, db.trainerProgressDao().observe("queens", null).first().size)
    }

    @Test
    fun `update writes under the writer and reads what the visible owner sees`() = runTest {
        answerAs(null, progress = TechniqueProgress(Mark.Practicing, 4, 10))
        scope.visibleOwner = "a"
        scope.writerUid = "a"
        var before: TechniqueProgress? = null

        val stored = repository.update(queens, lineLock) { before = it; it.copy(inRow = it.inRow + 1, answeredAtMs = 20) }

        // The transform saw the signed-out row's value, and the result went to the owner's own row.
        assertEquals(TechniqueProgress(Mark.Practicing, 4, 10), before)
        assertEquals(TechniqueProgress(Mark.Practicing, 5, 20), stored)
        val rows = db.trainerProgressDao().observe("queens", null).first()
        assertEquals(listOf<Pair<String?, Int>>(null to 4, "a" to 5), rows.sortedBy { it.id }.map { it.ownerUid to it.inRow })
    }

    @Test
    fun `update with nobody signed in writes the ownerless row`() = runTest {
        repository.update(queens, lineLock) { TechniqueProgress(Mark.Practicing, 1, 10) }

        assertEquals(listOf<String?>(null), db.trainerProgressDao().observe("queens", null).first().map { it.ownerUid })
    }

    @Test
    fun `an update marks the row not uploaded`() = runTest {
        scope.writerUid = "a"
        scope.visibleOwner = "a"
        db.trainerProgressDao().insert(row("a", "practicing", uploaded = true))

        repository.update(queens, lineLock) { it.copy(inRow = 2, answeredAtMs = 5) }

        assertFalse(db.trainerProgressDao().find("queens", "line-lock", "a")!!.uploaded)
    }

    @Test
    fun `two updates at once both land`() = runTest {
        val bump: (TechniqueProgress) -> TechniqueProgress = { it.copy(mark = Mark.Practicing, inRow = it.inRow + 1) }

        awaitAll(
            async { repository.update(queens, lineLock, bump) },
            async { repository.update(queens, lineLock, bump) },
        )

        assertEquals(2, seen().getValue(lineLock).inRow)
    }

    @Test
    fun `many updates at once all land`() = runTest {
        val bump: (TechniqueProgress) -> TechniqueProgress = { it.copy(mark = Mark.Practicing, inRow = it.inRow + 1) }

        awaitAll(*Array(10) { async { repository.update(queens, lineLock, bump) } })

        assertEquals(10, seen().getValue(lineLock).inRow)
        assertEquals(1, db.trainerProgressDao().observe("queens", null).first().size)
    }

    @Test
    fun `an open observer sees an update and an owner change`() = runTest {
        answerAs("b", pairs, progress = TechniqueProgress(Mark.Mastered, 5, 30))
        scope.visibleOwner = "a"
        scope.writerUid = "a"
        val maps = Channel<Map<TechniqueId, TechniqueProgress>>(Channel.UNLIMITED)
        backgroundScope.launch { repository.observe(queens).collect { maps.send(it) } }
        assertEquals(emptyMap<TechniqueId, TechniqueProgress>(), maps.receive())

        repository.update(queens, lineLock) { TechniqueProgress(Mark.Practicing, 1, 20) }
        assertEquals(mapOf(lineLock to TechniqueProgress(Mark.Practicing, 1, 20)), maps.receive())

        scope.visibleOwner = "b"
        assertEquals(mapOf(pairs to TechniqueProgress(Mark.Mastered, 5, 30)), maps.receive())
    }

    @Test
    fun `a stored mark this version does not know reads as New`() = runTest {
        db.trainerProgressDao().insert(row(null, "legendary"))

        assertEquals(mapOf(lineLock to TechniqueProgress(Mark.New, 1, 7)), seen())
    }

    @Test
    fun `the three marks are stored as these words`() = runTest {
        val dao = db.trainerProgressDao()

        repository.update(queens, TechniqueId("a")) { TechniqueProgress(Mark.New, 0, 0) }
        repository.update(queens, TechniqueId("b")) { TechniqueProgress(Mark.Practicing, 1, 1) }
        repository.update(queens, TechniqueId("c")) { TechniqueProgress(Mark.Mastered, 5, 1) }

        assertEquals(listOf("new", "practicing", "mastered"), listOf("a", "b", "c").map { dao.find("queens", it, null)!!.mark })
    }

    @Test
    fun `an unknown technique id is kept and read back`() = runTest {
        db.trainerProgressDao().insert(row(null, "practicing").copy(techniqueId = "from-a-newer-build"))

        assertEquals(setOf(TechniqueId("from-a-newer-build")), seen().keys)
    }

    private fun row(owner: String?, mark: String, uploaded: Boolean = false) = TrainerProgressEntity(
        ownerUid = owner,
        gameId = "queens",
        techniqueId = "line-lock",
        mark = mark,
        inRow = 1,
        answeredAtMs = 7,
        uploaded = uploaded,
    )
}
