package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The add sheet's code lookup over a scripted [LoadSharedPuzzle]: what it parses, what it asks, how it names each outcome. */
class LoadPuzzleByCodeTest {

    private val code = requireNotNull(ShareCode.parse("K7QX2M"))
    private val loaded = mutableListOf<ShareCode>()
    private var outcome: OpenOutcome = OpenOutcome.NotFound

    private val lookup = LoadPuzzleByCode(
        load = { loaded += it; outcome },
        dispatchers = object : DispatcherProvider {
            val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val main = unconfined
            override val io = unconfined
            override val default = unconfined
        },
    )

    private fun received() = ReceivedPuzzle(
        code = code, gameId = alpha, variant = VariantId("alpha/n8"), sharedFormatVersion = 1, puzzleBytes = byteArrayOf(1),
        title = "Rooks", authorName = "Ed", note = "", authorTimeMs = 1_000L, authorHints = 0, authorUid = "ed",
        openedAt = Instant.EPOCH, unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
    )

    @Test
    fun `a code in any case, with or without the dash, and its puzzle link all open the same code`() = runTest {
        for (text in listOf("K7QX2M", "k7qx2m", "k7q-x2m", " K7Q X2M ", code.link, code.link + "/")) {
            lookup(text)
        }

        assertEquals(List(6) { code }, loaded)
    }

    @Test
    fun `text that is neither a code nor a puzzle link is invalid and opens nothing`() = runTest {
        for (text in listOf("", "crown", "K7Q-X2", "K7QX2MZ", "https://example.com/p/K7QX2M", code.matchLink)) {
            assertEquals(text, CodeLookup.Invalid, lookup(text))
        }

        assertTrue(loaded.isEmpty())
        assertNull(LoadPuzzleByCode.codeIn("crown"))
    }

    @Test
    fun `an opened puzzle is a friend's custom row of its code`() = runTest {
        outcome = OpenOutcome.Opened(received())

        val found = (lookup("k7qx2m") as CodeLookup.Found).puzzle

        assertEquals(ExactPuzzle(LegRef.Custom(alpha, code), "ed", "Rooks", "Ed", PuzzleSource.Friend), found.entry)
        assertEquals(code, found.code)
        assertEquals("n8", found.meta)
    }

    @Test
    fun `each outcome that is no puzzle has its own answer, and offline is not not-found`() = runTest {
        val expected = mapOf(
            OpenOutcome.NotFound to CodeLookup.NotFound,
            OpenOutcome.NoLongerAvailable to CodeLookup.NoLongerAvailable,
            OpenOutcome.Offline to CodeLookup.Offline,
            OpenOutcome.NeedsUpdate(9) to CodeLookup.CannotOpen,
            OpenOutcome.UnknownGame("gamma") to CodeLookup.CannotOpen,
            OpenOutcome.FailedCheck to CodeLookup.CannotOpen,
        )

        for ((given, answer) in expected) {
            outcome = given
            assertEquals(given.toString(), answer, lookup("k7qx2m"))
        }
    }
}
