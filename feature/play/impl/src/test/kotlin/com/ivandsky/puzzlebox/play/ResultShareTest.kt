package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.MoveMark
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.gameui.replay.ReplayUnavailable
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.testing.replay.FixtureSession
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures.GAME
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

    private fun share(game: String = "zip", onDay: LocalDate = day) =
        ResultShare(PlaySlot.Daily(GameId(game), onDay))

    private fun text(share: ResultShare, state: ResultUiState) = share.textOf(state, 183_000, 1)

    @Test
    fun `a replay shares the header, the strip and the link`() {
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

    @Test
    fun `the strip shows a hint as blue and a mistake as red`() {
        assertEquals("$header\n⬜🟩🟩🟩🟦🟩🟩🟥🟩🟩\n$link", text(share(), state(replay = ReplaySectionState.Ready(MarkedReplay.timeline()))))
    }
}

/** Ten placements a second apart, the 4th marked as a hint and the 7th as a mistake, as a real game's adapter marks them. */
private object MarkedReplay : GameReplay<FixtureSession> {
    private const val PLACE = 0
    private const val HINTED = 1
    private const val WRONG = 2

    fun timeline(): Timeline {
        val log = MoveLog.fresh(GAME)
        var session = FixtureSession(placed = 0, total = 10, hints = 0)
        for (index in 1..10) {
            val kind = when (index) {
                4 -> HINTED
                7 -> WRONG
                else -> PLACE
            }
            session = log.play(this, session, ReplayMove(kind, IntArray(0)), index * 1_000L)
        }
        val bytes = checkNotNull(log.seal(ReplayOutcome.Solved, endMs = 10_000L))
        return checkNotNull(Timeline.open(GAME, this, FixtureSession(placed = 0, total = 10, hints = 0), bytes))
    }

    override fun step(session: FixtureSession, move: ReplayMove, atMs: Long) = session.copy(placed = session.placed + 1)

    override fun factsOf(initial: FixtureSession): (FixtureSession) -> ReplayFacts =
        { ReplayFacts(hints = it.hints, correct = it.placed, total = initial.total, solved = it.placed == initial.total) }

    override fun markOf(before: FixtureSession, move: ReplayMove, after: FixtureSession, previous: ReplayMove?) = when (move.kind) {
        HINTED -> MoveMark(MoveKind.Hint, emptySet())
        WRONG -> MoveMark(MoveKind.Mistake, emptySet())
        else -> MoveMark.Plain
    }
}
