package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.NotationParse
import com.ivandsky.puzzlebox.game.editor.NotationText
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Queens parity (ADR-0021 §5, M5-03-b): for every Queens fixture, `puzzle-cli validate` prints
 * one CliVerdict JSON whose `verdict` and `blocks` equal the fixture's `# expect:` line and the
 * app's persisted round trip — `NotationText.parse` → `EditorDraft.encode()` → `EditorDraft.open`
 * → `analyze` + `publishBlocks`, the path ImportDraftFromText performs — and the exit code
 * follows the §9.2 rule (0 ⇔ unique with no block, 2 malformed, 1 otherwise).
 */
class QueensParityTest {

    private val json = Json

    @Test
    fun `every queens fixture matches expect line, app round trip and the 9_2 exit rule`() {
        val fixtures = NotationFixtures.of("queens")
        assertTrue("queens fixtures expected, got ${fixtures.size}", fixtures.isNotEmpty())
        for (fixture in fixtures) {
            val path = fixture.file.absolutePath
            val out = mutableListOf<String>()
            val err = mutableListOf<String>()
            val exit = PuzzleCli.run(listOf("validate", path), { p -> File(p).readText() }, out::add, err::add)

            assertEquals("$path: one JSON line expected, got err=$err", 1, out.size)
            val cli = json.decodeFromString(CliVerdict.serializer(), out.single())
            assertEquals("$path: malformed", emptyList<String>(), cli.errors)

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
