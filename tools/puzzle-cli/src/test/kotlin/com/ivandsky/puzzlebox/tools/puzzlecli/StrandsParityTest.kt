package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.NotationParse
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.game.editor.NotationText
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Strands parity (ADR-0021 §5, M5-14-b): for every Strands fixture, `puzzle-cli validate` prints
 * one CliVerdict JSON whose `verdict` and `blocks` equal the fixture's `# expect:` line and the
 * app's persisted round trip — `NotationText.parse` → `EditorDraft.encode()` → `EditorDraft.open`
 * → `analyze` + `publishBlocks`, the path ImportDraftFromText performs — and the exit code
 * follows the §9.2 rule (0 ⇔ unique with no block, 2 malformed, 1 otherwise). Also pins that a
 * cell-less word is malformed at the same line/col as the parser.
 */
class StrandsParityTest {

    private val json = Json

    @Test
    fun `every strands fixture matches expect line, app round trip and the 9_2 exit rule`() {
        val fixtures = NotationFixtures.of("strands")
        assertTrue("strands fixtures expected, got ${fixtures.size}", fixtures.isNotEmpty())
        for (fixture in fixtures) {
            val path = fixture.file.absolutePath
            val (exit, cli) = validate(path)
            assertEquals("$path: malformed", emptyList<CliError>(), cli.errors)

            // 1. CLI JSON ≡ `# expect:` line.
            assertEquals("$path: expect verdict", fixture.expectVerdict, cli.verdict)
            assertEquals("$path: expect blocks", fixture.expectBlocks, cli.blocks)

            // 2. CLI JSON ≡ the app's persisted-round-trip verdict.
            val app = appPath(fixture.text)
            assertEquals("$path: app verdict name", cli.verdict, app.verdictName)
            assertEquals("$path: app blocks", cli.blocks, app.blockIds)

            // 3. §9.2 exit rule.
            val wantedExit = when {
                cli.verdict == "malformed" -> CliExit.INVALID_TEXT
                cli.verdict == "unique" && cli.blocks.isEmpty() -> CliExit.PUBLISHABLE
                else -> CliExit.NOT_PUBLISHABLE
            }
            assertEquals("$path: exit code", wantedExit, exit)
        }
    }

    @Test
    fun `a word with no cells is malformed with the parser's line and col`() {
        val unique = NotationFixtures.of("strands").single { it.expectVerdict == "unique" }
        val text = unique.text.trimEnd('\n') + "\nword FORK\n"
        val file = File.createTempFile("strands-cell-less", ".txt").apply { writeText(text); deleteOnExit() }

        val (exit, cli) = validate(file.absolutePath)

        val parsed = StrandsEditor.parse(blanked(text))
        assertTrue("parser must reject, got $parsed", parsed is NotationResult.Err)
        val err = parsed as NotationResult.Err
        assertEquals("malformed", cli.verdict)
        assertEquals(listOf(CliError(err.line, err.col, err.message)), cli.errors)
        assertEquals(CliExit.INVALID_TEXT, exit)
    }

    private fun validate(path: String): Pair<Int, CliVerdict> {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val exit = PuzzleCli.run(listOf("validate", path), { p -> File(p).readText() }, out::add, err::add)
        assertEquals("$path: one JSON line expected, got err=$err", 1, out.size)
        return exit to json.decodeFromString(CliVerdict.serializer(), out.single())
    }

    /** The header and `#` lines blanked, as `NotationText` hands the text to a game's parser. */
    private fun blanked(text: String): String =
        text.split('\n').joinToString("\n") { if (it.startsWith("#") || it.startsWith("puzzlebox")) "" else it }

    /** `NotationText.parse` → `encode()` → `open` → `analyze` + `publishBlocks` (ADR-0021 §5). */
    private fun appPath(text: String): AppVerdict {
        val parsed = NotationText.parse(text, PuzzleCli.registry)
        val draft = (parsed as? NotationParse.Parsed)?.draft
        assertTrue("expected Parsed, got $parsed", draft != null)
        val stored = EditorDraft.open(draft!!.editor, draft.encode()).getOrNull()
        assertTrue("stored blob did not reopen", stored != null)
        val verdict = stored!!.analyze(Budget.Unlimited)
        return AppVerdict(verdict::class.simpleName!!.lowercase(), stored.publishBlocks(verdict).map { it.id })
    }

    /** The app path's answer: the verdict name and the publish-block ids, in order. */
    private data class AppVerdict(val verdictName: String, val blockIds: List<String>)
}
