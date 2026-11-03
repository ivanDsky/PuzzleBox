package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.network.LinkedInTangoApi
import com.ivandsky.puzzlebox.data.network.LinkedInTangoClient
import com.ivandsky.puzzlebox.data.network.TangoWireParser
import com.ivandsky.puzzlebox.data.network.retrofit
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The seed is the user's guaranteed-playable board while Tango auth is deferred. */
class TangoSeedBackedSourceTest {

    private val day = LocalDate.of(2026, 11, 2)

    @Test
    fun `seed payload parses to a uniquely solvable n6 puzzle`() {
        val parsed = TangoWireParser.parse(TangoSeedBackedSource.SEED_PAYLOAD, day)
        assertTrue(parsed is DecodeResult.Ok)
        val daily = (parsed as DecodeResult.Ok).value
        assertEquals("tango/n6", daily.puzzle.variant.value)
        assertEquals(6, daily.puzzle.n)
        assertEquals(12, daily.puzzle.givens.size)
        assertTrue(TangoUniqueness.isUniquelySolvable(daily.puzzle))
    }

    @Test
    fun `unavailable primary degrades to seed daily`() = runTest {
        val source = TangoSeedBackedSource(LinkedInTangoDailySource(FailingClient()))
        val result = source.fetchDaily(day)
        assertTrue(result is FetchResult.Ok)
        assertEquals("tango/n6", (result as FetchResult.Ok).value.puzzle.variant.value)
    }

    @Test
    fun `throttled primary is never masked by the seed`() = runTest {
        val source = TangoSeedBackedSource(LinkedInTangoDailySource(ThrottlingClient()))
        val result = source.fetchDaily(day)
        assertTrue((result as FetchResult.Err).error is FetchError.Throttled)
    }
}

/** Retrofit api never called — fetchDailyBody is overridden in the fakes. */
private val neverCalledApi: LinkedInTangoApi by lazy {
    retrofit(baseUrl = "http://localhost:1/", client = OkHttpClient(), json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        .create(LinkedInTangoApi::class.java)
}

private open class FailingClient : LinkedInTangoClient(neverCalledApi) {
    override suspend fun fetchDailyBody(): FetchResult<String> =
        FetchResult.Err(FetchError.Unavailable("test: no session"))
}

private class ThrottlingClient : LinkedInTangoClient(neverCalledApi) {
    override suspend fun fetchDailyBody(): FetchResult<String> =
        FetchResult.Err(FetchError.Throttled("test throttle"))
}
