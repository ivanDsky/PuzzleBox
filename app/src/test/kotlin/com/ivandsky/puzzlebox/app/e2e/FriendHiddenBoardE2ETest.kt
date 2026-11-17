package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.FriendWorld.Companion.AUTHOR
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.game.editor.encodeShare
import com.ivandsky.puzzlebox.game.editor.shareFormatVersion
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.PlayKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The friend page's hidden board (nav-rethink M12-8) over the real shell and each of the five real games:
 * a friend opens a published puzzle by its code and finds its board blurred under "Hidden until
 * you play", with only that one line to TalkBack and nothing of the puzzle's solution on the page; after the friend
 * wins it, the page's board is sharp and is still the same givens, not the solved board. The author publishes through
 * the shared fake (the Studio's own publish page is not under test here).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class FriendHiddenBoardE2ETest(name: String) {

    private val game: FriendGame = FRIEND_GAMES.single { it.toString() == name }
    private val world = FriendWorld(game.flags)

    @After
    fun close() = world.close()

    @Test
    fun `an unsolved puzzle's page shows its board hidden, with nothing of the solution, and sharp once solved`() =
        shellRobot(graph = world.graph) {
            val code = world.publishFriendPuzzle(game, "From a friend")
            world.signInFriend()

            tapTab(PlayKey)
            openByCode(code)
            awaitTag("friend.card.board.hidden")
            assertFalse("no sharp board before it is solved", shows("friend.card.board.sharp"))
            assertEquals(
                "TalkBack hears one line for the board",
                "Board hidden until you play",
                rule.onNodeWithTag("friend.card.board.hidden", useUnmergedTree = true).fetchSemanticsNode().config
                    .getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
            )
            val hiddenBoard = boardWords()

            tap("friend.card.play")
            awaitTag("route.game")
            with(game) { solve(PlaySlot.Custom(game.id, code.value)) }
            awaitCustomResult()
            tap("result.done")
            awaitTag("friend.card.solved")

            awaitTag("friend.card.board.sharp")
            assertFalse("no hidden board once solved", shows("friend.card.board.hidden"))
            assertEquals("the sharp board is the same givens, not a solved board", hiddenBoard, boardWords())
        }

    /**
     * Everything the page's board says in any tag, text or description, in the unmerged tree (what a solution would
     * leak through), less the veil's pill, which only the hidden board wears.
     */
    private fun ShellRobot.boardWords(): List<String> =
        rule.onAllNodes(hasAnyAncestor(hasTestTag("friend.card.board.hidden") or hasTestTag("friend.card.board.sharp")), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node ->
                listOfNotNull(
                    node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text },
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
                    node.config.getOrNull(SemanticsProperties.TestTag),
                )
            }
            .filter { it != "Hidden until you play" }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun games(): List<Array<Any>> = FRIEND_GAMES.map { arrayOf<Any>(it.toString()) }
    }
}

/** The five games the friend page is proved over. */
internal val FRIEND_GAMES: List<FriendGame> = listOf(QueensCastle, ZipCorridor, TangoSunMoon, MiniSudokuCorner, StrandsWoods)

/** [game]'s board, with its solution stripped as it travels, published under a fixed code (a fixed code keeps goldens still): the author signed out again after. */
internal fun FriendWorld.publishFriendPuzzle(game: FriendGame, title: String): ShareCode {
    val code = requireNotNull(ShareCode.parse("K7Q2MX"))
    val board = game.board
    shared.signedInUid = AUTHOR
    runBlocking {
        shared.publish(
            SharedPuzzleDoc(
                code = code,
                formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
                gameId = game.id.value,
                variant = board.variant.value,
                shareCodecVersion = checkNotNull(board.shareFormatVersion),
                payload = checkNotNull(board.encodeShare()),
                title = title,
                authorName = "Ivan",
                note = "The corner is the trick",
                authorUid = AUTHOR,
                authorTimeMs = 102_000L,
                authorHints = 0,
                createdAtMs = 0L,
                unpublished = false,
            ),
        )
    }
    shared.signedInUid = null
    return code
}
