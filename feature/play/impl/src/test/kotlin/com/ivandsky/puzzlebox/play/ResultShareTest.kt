package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.gameui.replay.ReplayUnavailable
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultShareTest {
    private val day = LocalDate.parse("2026-11-18")
    private val header = "PuzzleBox Zip #608 · 3:03 · 1 hint"
    private val strip = "⬜" + "🟩".repeat(9)
    private val link = "https://puzzlebox-f3356.web.app/p/zip/2026-11-18"

    // Ten places a second apart: square 0 is untouched, squares 1-9 hold plain moves.
    private val replay = ReplaySectionState.Ready(ReplayFixtures.timeline(moves = 10))

    private fun state(
        replay: ReplaySectionState = this.replay,
        practice: Boolean = false,
        game: String = "Zip",
        number: Int? = 608,
    ) = ResultUiState(game = game, puzzleNumber = number, practice = practice, replay = replay)

    private fun share(game: String = "zip", on: Boolean = true, onDay: LocalDate = day) =
        ResultShare(on, PlaySlot.Daily(GameId(game), onDay))

    private fun text(share: ResultShare, state: ResultUiState) = share.textOf(state, 183_000, 1)

    @Test
    fun `flag off is today's header alone, whatever the replay holds`() {
        assertEquals(header, text(share(on = false), state()))
    }

    @Test
    fun `flag on with a replay shares the header, the strip and the link`() {
        assertEquals("$header\n$strip\n$link", text(share(), state()))
    }

    @Test
    fun `strands has no strip but keeps the link`() {
        assertEquals(
            "PuzzleBox Strands #608 · 3:03 · 1 hint\nhttps://puzzlebox-f3356.web.app/p/strands/2026-11-18",
            text(share("strands"), state(game = "Strands")),
        )
    }

    @Test
    fun `a replay that is hidden or unavailable gives no strip`() {
        listOf(ReplaySectionState.Hidden, ReplaySectionState.Unavailable(ReplayUnavailable.NotRecorded)).forEach {
            assertEquals("$header\n$link", text(share(), state(replay = it)))
        }
    }

    @Test
    fun `a practice puzzle shares no link`() {
        assertEquals("PuzzleBox Zip · 3:03 · 1 hint\n$strip", text(share(), state(practice = true, number = null)))
    }

    @Test
    fun `a past day's link names that day`() {
        assertEquals(
            "$header\n$strip\nhttps://puzzlebox-f3356.web.app/p/zip/2026-10-31",
            text(share(onDay = LocalDate.parse("2026-10-31")), state()),
        )
    }
}
