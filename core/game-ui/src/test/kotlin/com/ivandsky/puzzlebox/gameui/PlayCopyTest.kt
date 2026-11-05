package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.FetchError
import org.junit.Assert.assertFalse
import org.junit.Test

/** A21: play error and degrade copy never names the feed's host or its throttling. */
class PlayCopyTest {

    /** One instance per [FetchError] case; the exhaustive `when` fails to compile when a case is added. */
    private val everyError: List<FetchError> = listOf(
        FetchError.Unavailable("no net"),
        FetchError.Throttled("cooldown"),
        FetchError.Untrusted("bad payload"),
    ).onEach { error ->
        when (error) {
            is FetchError.Unavailable, is FetchError.Throttled, is FetchError.Untrusted -> Unit
        }
    }

    private val everyString: List<String> =
        everyError.flatMap { listOf(unavailableMessage(it), degradeBannerMessage(it)) } + BROKEN_PUZZLE_MESSAGE

    @Test
    fun `no player-facing copy names LinkedIn or rate limiting`() {
        everyString.forEach { copy ->
            assertFalse("names LinkedIn: $copy", copy.contains("linkedin", ignoreCase = true))
            assertFalse("mentions rate: $copy", copy.contains("rate", ignoreCase = true))
        }
    }
}
