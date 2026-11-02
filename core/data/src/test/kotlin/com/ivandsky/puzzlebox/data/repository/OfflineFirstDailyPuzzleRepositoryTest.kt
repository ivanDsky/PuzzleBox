package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.result.AppError
import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.network.AuthHeaderSource
import com.ivandsky.puzzlebox.data.network.PuzzleFeedApi
import com.ivandsky.puzzlebox.data.network.okHttpClient
import com.ivandsky.puzzlebox.data.network.retrofit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstDailyPuzzleRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var db: PuzzleBoxDatabase
    private lateinit var repository: OfflineFirstDailyPuzzleRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val json = Json { ignoreUnknownKeys = true }
        val api = retrofit(
            baseUrl = server.url("/").toString(),
            client = okHttpClient(AuthHeaderSource { mapOf("X-Test-Auth" to "fixture") }),
            json = json,
        ).create(PuzzleFeedApi::class.java)
        repository = OfflineFirstDailyPuzzleRepository(api, db.dailyPuzzleDao(), DefaultDispatcherProvider(), json)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader).getResource("fixtures/$name")!!.readText()

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun `fixture fetch parses, caches and returns domain puzzle`() = runTest {
        val body = fixture("zip-daily-2026-10-31.json")
        server.enqueue(ok(body))

        val result = repository.refresh("zip", "2026-10-31")

        assertTrue(result is AppResult.Ok)
        val puzzle = (result as AppResult.Ok).value
        assertEquals("zip", puzzle.gameId)
        assertEquals("2026-10-31", puzzle.day)
        assertTrue(puzzle.payload.contentEquals(body.encodeToByteArray()))
        assertTrue(puzzle.fetchedAtMs > 0)
        assertEquals(1, db.dailyPuzzleDao().count())
        assertEquals(body, db.dailyPuzzleDao().get("zip", "2026-10-31")?.payload?.decodeToString())

        val recorded = server.takeRequest()
        assertEquals("/v1/dailies/zip/2026-10-31", recorded.path)
        assertEquals("fixture", recorded.getHeader("X-Test-Auth"))
    }

    @Test
    fun `http error maps to Http failure and caches nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = repository.refresh("zip", "1999-01-01")

        assertTrue(result is AppResult.Err && (result as AppResult.Err).error is AppError.Http)
        assertEquals(0, db.dailyPuzzleDao().count())
    }

    @Test
    fun `malformed payload maps to Parsing failure`() = runTest {
        server.enqueue(ok("{\"gameId\": "))

        val result = repository.refresh("zip", "2026-10-31")

        assertTrue(result is AppResult.Err && (result as AppResult.Err).error is AppError.Parsing)
    }

    @Test
    fun `connection failure maps to Network failure`() = runTest {
        server.shutdown()

        val result = repository.refresh("zip", "2026-10-31")

        assertTrue(result is AppResult.Err && (result as AppResult.Err).error is AppError.Network)
    }

    @Test
    fun `observeHistory and cached read the Room cache`() = runTest {
        server.enqueue(ok(fixture("zip-daily-2026-10-31.json")))
        repository.refresh("zip", "2026-10-31")

        repository.observeHistory("zip").test {
            assertEquals(listOf("2026-10-31"), awaitItem().map { it.day })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("2026-10-31", repository.cached("zip", "2026-10-31")?.day)
        assertNull(repository.cached("zip", "1999-01-01"))
    }
}
