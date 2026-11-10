package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.e2e.FriendWorld.Companion.FRIEND
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The friend open loop over the real shell, once per game with only its editor on, the
 * device check's friend lines (Z2, T2, S2, R2): an author publishes a Unique draft from the
 * Studio and reads its code (A); a second signed-in user, the friend, enters it in Play and the
 * card opens on that game (B); the friend wins it through the board's input and lands on the
 * result (C); the win is one custom record on that puzzle, never a daily (D); it posts once to
 * the puzzle's own board, which shows the friend's row on the result and the card, while
 * Today, History, the streak, the stats and the daily board stay as they were (E).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot and Strands' 8-row board need the room.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class FriendOpenLoopE2ETest(name: String) {

    private val game: FriendGame = GAMES.single { it.toString() == name }
    private val world = FriendWorld(game.flags)

    @After
    fun close() = world.close()

    @Test
    fun `a friend opens a published puzzle by code and wins it once to its own board, every daily as it was`() =
        shellRobot(graph = world.graph) {
            val code = world.publishAsAuthor(this, game.board, TITLE)
            world.signInFriend()
            val before = world.settleDailies(this, game.id)

            tapTab(PlayKey)
            openByCode(code)
            awaitTag("friend.card.glyph")
            val row = world.received.rows.getValue(code)
            assertEquals("B: the card's puzzle is the published game", game.id, row.gameId)
            assertEquals("B: the card names the author, not the friend", FriendWorld.AUTHOR, row.authorUid)
            tap("friend.card.play")
            awaitTag("route.game")
            assertEquals("B: Play opens the game's board", CustomGameKey(game.id.value, code.value), state.currentStack.last())

            with(game) { solve(PlaySlot.Custom(game.id, code.value)) }
            awaitCustomResult()

            // The win's write lands after the result opens; D reads the store once it has.
            rule.waitUntil(FRIEND_TIMEOUT_MS) { world.records().size > before.records.size }
            val custom = world.records().filter { it.puzzleRef.key !is PuzzleKey.Daily }
            assertEquals(
                "D: the win is one custom record, on this puzzle",
                listOf(game.id to PuzzleKey.Custom(code.value)),
                custom.map { it.puzzleRef.gameId to it.puzzleRef.key },
            )
            assertEquals("D: the daily records are as they were", before.records, world.dailies(game.id).records)
            awaitTag("result.record_badge")

            // The submitter's last step: the row is marked Posted, so it never posts this win again.
            rule.waitUntil(FRIEND_TIMEOUT_MS) { world.received.rows[code]?.postState == PostState.Posted }
            assertEquals("E: the puzzle's board got the friend's one first win", listOf(FRIEND), world.boards.entries.getValue(code).map { it.uid })
            awaitTag("custom_board.row.me")

            tap("result.done")
            awaitTag("friend.card.solved")
            assertRoute("route.friend")
            awaitTag("custom_board.row.me")

            // Checked last, after the walk back: a leaked daily write or a second post has had every chance to land.
            val after = world.dailies(game.id)
            assertEquals("E: Today, History, stats, daily records and the daily board are unchanged", before, after)
            assertTrue("E: the dailies hold a streak", after.stats.getValue(null).currentStreak >= 2)
            assertEquals("E: the puzzle's board still holds one entry", listOf(FRIEND), world.boards.entries.getValue(code).map { it.uid })
        }

    companion object {
        private const val TITLE = "From a friend"
        private val GAMES = listOf(ZipCorridor, TangoSunMoon, MiniSudokuCorner, StrandsWoods)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun games(): List<Array<Any>> = GAMES.map { arrayOf<Any>(it.toString()) }
    }
}
