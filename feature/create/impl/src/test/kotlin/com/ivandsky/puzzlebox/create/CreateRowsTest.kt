package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Published-tab row's meta line: the author time when known, nothing for a legacy row, "Unpublished" once withdrawn. */
class CreateRowsTest {

    private val now = Instant.ofEpochSecond(1_000_000)

    private fun meta(authorTimeMs: Long, unpublished: Boolean = false) = publishedRowOf(
        PublishedPuzzle(
            code = checkNotNull(ShareCode.parse("ABCDEF")),
            gameId = GameId("zip"),
            variant = VariantId("zip/6"),
            title = "Cats",
            authorTimeMs = authorTimeMs,
            authorHints = 0,
            createdAt = now,
            unpublished = unpublished,
            solvesCount = null,
            solvesCountAt = null,
        ),
        GamePlayRegistry(emptyList()),
        now,
    ).meta

    @Test
    fun `a known author time is shown`() {
        assertEquals("Author 1:42", meta(102_000))
    }

    @Test
    fun `a legacy row with no author time shows none rather than 0 00`() {
        assertEquals("", meta(0))
    }

    @Test
    fun `an unpublished row says so whatever the author time`() {
        assertEquals("Unpublished", meta(0, unpublished = true))
        assertEquals("Unpublished", meta(102_000, unpublished = true))
    }
}
