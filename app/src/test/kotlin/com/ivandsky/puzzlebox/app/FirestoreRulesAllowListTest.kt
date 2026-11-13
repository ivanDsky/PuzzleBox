package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.game.GameRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ADR-0024 / spec A5, A20: the rules accept a daily entry only for the ids in
 * `dailyGameIds()` of `backend/firebase/firestore.rules`, so a sixth daily game cannot
 * ship without its rules line. Reads the rules source from the repo (the test working
 * directory is the `:app` module root); the rules-matrix covers the rules' behavior.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application for DiGraphTest's reason: the real one would startKoin on its own.
@Config(sdk = [34], application = DrainTestApplication::class)
class FirestoreRulesAllowListTest {

    @Test
    fun `every listed game id is in the rules daily allow-list`() {
        val allowed = rulesAllowList()
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        try {
            // `all` is the listed games: the debug-only 9x9 sudoku is unlisted in debug and
            // absent from the release graph, and has no daily board.
            val ids = koinApp.koin.get<GameRegistry>().all.map { it.definition.id.value }
            assertTrue("the registry lists no games", ids.isNotEmpty())
            val missing = ids.filter { it !in allowed }
            assertEquals(
                "games missing from dailyGameIds() in backend/firebase/firestore.rules " +
                    "(the rules would reject their daily entries): $missing",
                emptyList<String>(),
                missing,
            )
        } finally {
            stopKoin() // close() alone leaves the JVM-global context registered for later tests
        }
    }

    private fun rulesAllowList(): List<String> {
        val rules = File("../backend/firebase/firestore.rules").readText()
        val body = Regex("""function\s+dailyGameIds\(\)\s*\{\s*return\s*\[([^\]]*)]""")
            .find(rules)
            ?.groupValues?.get(1)
            ?: error("firestore.rules has no dailyGameIds() allow-list function")
        return Regex("""'([^']+)'""").findAll(body).map { it.groupValues[1] }.toList()
    }
}
