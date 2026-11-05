package com.ivandsky.puzzlebox.games.strands.feed

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.strands.StrandsNumber
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in live sweep, not part of the gate: fetches every day from #1 to today through the
 * production source and expects zero rejects. Run with
 * `STRANDS_SWEEP=true ./gradlew :games:strands:test --tests '*StrandsArchiveSweep*'`.
 */
class StrandsArchiveSweep {

    @Test
    fun `every day from 1 to today is trusted`() = runBlocking {
        assumeTrue(System.getenv("STRANDS_SWEEP") == "true")
        val source = NytStrandsDailySource { path -> get(path) }
        val rejects = mutableListOf<String>()
        var day = StrandsNumber.EPOCH
        while (day <= LocalDate.now()) {
            (source.fetchDaily(day) as? FetchResult.Err)?.let { rejects += "$day: ${it.error}" }
            day = day.plusDays(1)
        }
        assertEquals(emptyList<String>(), rejects)
    }

    private fun get(path: String): FetchResult<String> {
        val connection = URI("https://www.nytimes.com/$path").toURL().openConnection() as HttpURLConnection
        return try {
            when (val code = connection.responseCode) {
                200 -> FetchResult.Ok(connection.inputStream.use { it.readBytes().decodeToString() })
                404 -> FetchResult.Err(FetchError.NotFound(path))
                else -> FetchResult.Err(FetchError.Unavailable("HTTP $code"))
            }
        } finally {
            connection.disconnect()
        }
    }
}
