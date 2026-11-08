package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * The whole-text entry (spec A23) over a stand-in game whose board is a list of cell values
 * and whose body is one line of comma-separated ints. Its [NotationEditor.parse] counts calls
 * and records the text it received, so the cap and blanking steps are observable.
 */
class NotationTextTest {

    @Test
    fun `text over the cap is too large without reaching the editor`() {
        val editor = NotationEditor()
        val header = "puzzlebox list v1\n" // 18 bytes
        val text = header + "a".repeat(NotationText.MAX_BYTES - header.length + 5)

        val result = NotationText.parse(text, registry(editor))

        val firstPast = NotationText.MAX_BYTES - header.length + 1
        assertEquals(
            NotationParse.TooLarge(bytes = NotationText.MAX_BYTES + 5, line = 2, col = firstPast),
            result,
        )
        assertEquals(0, editor.calls)
    }

    @Test
    fun `text exactly at the cap parses`() {
        val editor = NotationEditor()
        val header = "puzzlebox list v1\n"
        val body = "1,2\n" + " ".repeat(NotationText.MAX_BYTES - header.length - 4)

        val result = NotationText.parse(header + body, registry(editor))

        assertTrue(result is NotationParse.Parsed)
        assertEquals(1, editor.calls)
    }

    @Test
    fun `blank and comment-only text reports a missing header at line 1`() {
        val editor = NotationEditor()

        val result = NotationText.parse("\n# a comment\n", registry(editor))

        assertEquals(NotationParse.Malformed(1, 1, "missing header"), result)
        assertEquals(0, editor.calls)
    }

    @Test
    fun `a first content line that is not a header is garbled at its line`() {
        val editor = NotationEditor()

        val result = NotationText.parse("\n# a comment\n1,2\n", registry(editor))

        assertEquals(NotationParse.Malformed(3, 1, "garbled header"), result)
        assertEquals(0, editor.calls)
    }

    @Test
    fun `a version below the editor's v0 is a garbled header`() {
        val editor = NotationEditor()

        val result = NotationText.parse("puzzlebox list v0\n1,2\n", registry(editor))

        assertEquals(NotationParse.Malformed(1, 1, "garbled header"), result)
        assertEquals(0, editor.calls)
    }

    @Test
    fun `a game this build does not ship is unknown`() {
        val editor = NotationEditor()

        val result = NotationText.parse("puzzlebox nosuch v1\n1,2\n", registry(editor))

        assertEquals(NotationParse.UnknownGame("nosuch"), result)
        assertEquals(0, editor.calls)
    }

    @Test
    fun `an editor without the notation capability is unknown`() {
        val editor = ListEditor()

        val result = NotationText.parse("puzzlebox list v1\n1,2\n", registry(editor))

        assertEquals(NotationParse.UnknownGame("list"), result)
    }

    @Test
    fun `a version above the editor's highest is unsupported`() {
        val editor = NotationEditor()

        val result = NotationText.parse("puzzlebox list v2\n1,2\n", registry(editor))

        assertEquals(NotationParse.UnsupportedVersion("list", found = 2, maxKnown = 1), result)
        assertEquals(0, editor.calls)
    }

    @Test
    fun `a version below the editor's highest still parses`() {
        val editor = NotationEditor(version = 2)

        val result = NotationText.parse("puzzlebox list v1\n1,2\n", registry(editor))

        assertTrue(result is NotationParse.Parsed)
        assertEquals(1, editor.calls)
    }

    @Test
    fun `a game error keeps whole-text line and column behind a comment`() {
        val editor = NotationEditor()

        val text = "puzzlebox list v1\n# a comment\n\nbad line\n"

        val result = NotationText.parse(text, registry(editor))

        assertEquals(NotationParse.Malformed(4, 2, "list.bad"), result)
        // The editor saw the header and comment lines blanked, positions unchanged.
        assertEquals(listOf("", "", "", "bad line", ""), editor.received?.split('\n'))
    }

    @Test
    fun `a parsed draft outside the variant table is an unsupported size`() {
        val editor = NotationEditor()

        val result = NotationText.parse("puzzlebox list v1\n1,2,3,4\n", registry(editor))

        assertEquals(NotationParse.Malformed(2, 1, "unsupported size"), result)
    }

    @Test
    fun `a leading comment line is skipped and blanked with the header`() {
        val editor = NotationEditor()

        val result = NotationText.parse("# lead\npuzzlebox list v1\n1,2\n", registry(editor))

        assertTrue(result is NotationParse.Parsed)
        assertEquals(listOf("", "", "1,2", ""), editor.received?.split('\n'))
    }

    @Test
    fun `valid text parses into a fresh draft of the editor's game`() {
        val editor = NotationEditor()

        val result = NotationText.parse("puzzlebox list v1\n1,2\n", registry(editor))

        val draft = (result as NotationParse.Parsed).draft
        assertEquals(GAME, draft.gameId)
        assertEquals(VariantId("list/n2"), draft.variant)
        assertEquals(listOf(1, 2), draft.state.draft)
        assertFalse(draft.canUndo)
        assertFalse(draft.canRedo)
    }

    private fun registry(editor: GameEditor<*>): GameRegistry =
        GameRegistry(listOf(plugin(GAME, editor)))

    /** The stand-in game registered under [GAME]: wiring only, never exercised here. */
    private fun plugin(
        id: GameId,
        editor: GameEditor<*>?,
    ): GamePlugin<Unit, GameSession, Model> = GamePlugin(
        definition = object : GameDefinition {
            override val id = id
            override val displayName = id.value
            override val cadence = Cadence.Daily(ZoneId.of("UTC"))
            override fun decode(bytes: ByteArray): DecodeResult<Puzzle> =
                DecodeResult.Err(DecodeError.Malformed("unused"))
            override fun encode(puzzle: Puzzle): ByteArray = ByteArray(0)
        },
        rules = object : GameRules<Unit> {
            override fun validate(move: Move, state: Unit): MoveVerdict = Denied
            override fun isComplete(state: Unit): Boolean = false
            override fun hint(state: Unit): Hint = UnusedHint
        },
        session = object : GameSessionCodec<GameSession> {
            override fun serialize(session: GameSession): ByteArray = ByteArray(0)
            override fun deserialize(bytes: ByteArray): DecodeResult<GameSession> =
                DecodeResult.Err(DecodeError.Malformed("unused"))
        },
        score = object : ScoreModel<GameSession> {
            override fun completion(session: GameSession) = null
        },
        stateOf = { Unit },
        initialSession = { DecodeResult.Err(DecodeError.Malformed("unused")) },
        refOf = { key -> KeyRef(key, id) },
        replaySession = { _, _ -> DecodeResult.Err(DecodeError.Malformed("unused")) },
        rendererModel = { _, _, _ -> Model },
        editor = editor,
    )

    /** The game without the notation capability. */
    private open class ListEditor : GameEditor<List<Int>> {
        override val gameId = GAME
        override val variants = listOf(VariantId("list/n1"), VariantId("list/n2"))
        override val draftFormatVersion = 1

        override fun newDraft(variant: VariantId): List<Int> =
            List(variant.value.removePrefix("list/n").toInt()) { 0 }

        override fun variantOf(draft: List<Int>): VariantId = VariantId("list/n${draft.size}")

        override fun encodeDraft(state: DraftState<List<Int>>): ByteArray =
            ByteArray(state.draft.size) { state.draft[it].toByte() }

        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> =
            DecodeResult.Ok(DraftState(bytes.map { it.toInt() }))

        override fun analyze(draft: List<Int>, budget: Budget): Verdict = Verdict.Undetermined
    }

    /**
     * The game with the capability: every non-blank line is one board row of ints; a line
     * containing "bad" answers [NotationResult.Err] at its whole-text position.
     */
    private class NotationEditor(version: Int = 1) : ListEditor(), EditorNotation<List<Int>> {
        override val notationVersion = version

        var calls = 0
            private set
        var received: String? = null
            private set

        override fun parse(text: String): NotationResult<List<Int>> {
            calls += 1
            received = text
            val lines = text.split('\n')
            lines.forEachIndexed { index, line ->
                if (line.contains("bad")) return NotationResult.Err(index + 1, 2, "list.bad")
            }
            val cells = lines.filter { it.isNotBlank() }
                .flatMap { line -> line.split(',').map { it.trim().toInt() } }
            return NotationResult.Ok(cells)
        }

        override fun print(draft: List<Int>): String =
            NotationHeader(gameId.value, notationVersion).line + "\n" +
                draft.joinToString(",") + "\n"

        override fun columns(draft: List<Int>): Int = draft.size
    }

    private data class KeyRef(override val key: PuzzleKey, override val gameId: GameId) : PuzzleRef
    private object Model : RendererModel
    private object UnusedHint : Hint
    private object Denied : MoveVerdict

    private companion object {
        val GAME = GameId("list")
    }
}
