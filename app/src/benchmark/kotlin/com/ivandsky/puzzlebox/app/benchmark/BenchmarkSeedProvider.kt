package com.ivandsky.puzzlebox.app.benchmark

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

/**
 * Benchmark build only (this source set ships in no other build type): `content call --uri content://<package>.seed
 * --method seed` writes [BenchmarkHistory] into the install's own solve history, so `:benchmark`'s Insights journeys
 * have two years of months to scroll. Idempotent: a day already stored is not written again. It never reads or
 * writes anything but the benchmark package's own database, which holds no real account.
 */
internal class BenchmarkSeedProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != SEED) return null
        val koin = awaitKoin()
        val registry = koin.get<GameRegistry>()
        val completions = koin.get<CompletionRepository>()
        val written = runBlocking {
            val stored = completions.observeRecords().first().mapTo(HashSet()) { it.puzzleRef.gameId to it.puzzleRef.key }
            BenchmarkHistory.records(registry, LocalDate.now())
                .filter { (it.puzzleRef.gameId to it.puzzleRef.key) !in stored }
                .onEach { completions.onCompleted(it) }
                .size
        }
        return Bundle().apply { putInt(WRITTEN, written) }
    }

    /** The app's Koin starts in `Application.onCreate`, which a provider call can overtake on a cold process. */
    private fun awaitKoin(): org.koin.core.Koin {
        val deadline = SystemClock.elapsedRealtime() + KOIN_WAIT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            GlobalContext.getOrNull()?.let { return it }
            SystemClock.sleep(POLL_MS)
        }
        error("Koin did not start within ${KOIN_WAIT_MS}ms")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val SEED = "seed"
        const val WRITTEN = "written"
        const val KOIN_WAIT_MS = 10_000L
        const val POLL_MS = 50L
    }
}
