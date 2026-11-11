package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.NotationHeader
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.game.editor.NotationText
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text import (spec A23): Parsed becomes one stored draft row with the parsed blob and
 * `imported = true`; every other NotationParse outcome maps 1:1 and writes nothing. Queens
 * stands in through a test double (:core:domain has no games:* edge per ADR-0008).
 */
class ImportDraftFromTextTest {

    private val drafts = FakeDraftRepository()
    private var ids = 0
    private val import = ImportDraftFromText(
        registry = GameRegistry(listOf(queensPlugin())),
        createDraft = CreateDraft(
            registry = GameRegistry(listOf(queensPlugin())),
            drafts = drafts,
            newId = { "draft-${++ids}" },
            clock = { Instant.parse("2026-11-08T10:00:00Z") },
        ),
        drafts = drafts,
        dispatchers = TestInlineDispatcherProvider,
    )

    @Test
    fun `a queens fixture imports to a stored imported row`() = runTest {
        val result = import(queensFixture())

        assertEquals(ImportResult.Imported("draft-1"), result)
        val row = drafts.rows.getValue("draft-1")
        assertTrue(row.imported)
        assertTrue(Regex("[A-Z][a-z]+ [A-Z][a-z]+").matches(row.title))
        val opened = EditorDraft.open(Queens, row.blob)
        assertTrue(opened is DecodeResult.Ok)
        val draft = (opened as DecodeResult.Ok).value
        assertFalse(draft.canUndo)
        assertEquals(listOf(0, 0, 1, 1, 1, 0, 2, 2, 1, 1, 0, 2, 3, 3, 3, 4, 4, 4, 3, 3, 4, 4, 4, 4, 3), draft.state.draft)
    }

    @Test
    fun `text over the cap maps to TooLarge and writes nothing`() = runTest {
        val header = "puzzlebox queens v1\n"
        val text = header + "0".repeat(NotationText.MAX_BYTES - header.length + 1)

        val result = import(text)

        assertEquals(ImportResult.TooLarge(NotationText.MAX_BYTES + 1, 2, NotationText.MAX_BYTES - 19), result)
        assertTrue(drafts.rows.isEmpty())
    }

    @Test
    fun `an unknown game maps to UnknownGame and writes nothing`() = runTest {
        val result = import("puzzlebox zip v1\n")

        assertEquals(ImportResult.UnknownGame("zip"), result)
        assertTrue(drafts.rows.isEmpty())
    }

    @Test
    fun `an unknown version maps to UnsupportedVersion and writes nothing`() = runTest {
        val result = import("puzzlebox queens v9\n" + queensBody())

        assertEquals(ImportResult.UnsupportedVersion(9, 1), result)
        assertTrue(drafts.rows.isEmpty())
    }

    @Test
    fun `a malformed body maps to Malformed and writes nothing`() = runTest {
        val result = import("puzzlebox queens v1\n0000\n0011\n2233\n4455\n")

        assertTrue(result is ImportResult.Malformed)
        assertTrue(drafts.rows.isEmpty())
    }

    @Test
    fun `a cancel between the two writes never leaves a blank draft`() = runTest {
        val gated = GatedDraftRepository(drafts)
        val gatedImport = ImportDraftFromText(
            registry = GameRegistry(listOf(queensPlugin())),
            createDraft = CreateDraft(
                registry = GameRegistry(listOf(queensPlugin())),
                drafts = gated,
                newId = { "draft-${++ids}" },
                clock = { Instant.parse("2026-11-08T10:00:00Z") },
            ),
            drafts = gated,
            dispatchers = TestInlineDispatcherProvider,
        )
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { gatedImport(queensFixture()) }
        assertTrue(gated.secondWriteWaiting)

        job.cancel()
        gated.release.complete(Unit)
        job.join()

        assertTrue(drafts.rows.values.all { it.imported })
    }

    /** Lets the first write through and holds the second until [release], as a slow disk would. */
    private class GatedDraftRepository(private val inner: FakeDraftRepository) : DraftRepository by inner {
        val release = CompletableDeferred<Unit>()
        var secondWriteWaiting = false
            private set
        private var writes = 0

        override suspend fun upsert(draft: Draft) {
            if (++writes == 2) {
                secondWriteWaiting = true
                release.await()
            }
            inner.upsert(draft)
        }
    }

    /** A painted 5x5 regions board with a final newline, as the notation prints it. */
    private fun queensFixture(): String = "puzzlebox queens v1\n" + queensBody()

    private fun queensBody(): String =
        listOf("00111", "02211", "02333", "44433", "44443").joinToString("") { "$it\n" }

    /** Runs work inline on the calling thread so runTest needs no idling. */
    private object TestInlineDispatcherProvider : DispatcherProvider {
        private val inline = object : CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext) = false
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        }
        override val main: CoroutineDispatcher = inline
        override val io: CoroutineDispatcher = inline
        override val default: CoroutineDispatcher = inline
    }

    /** Queens stand-in: a regions board of 5 rows of 5 base-36 ids, notation v1, no solution. */
    private object Queens : GameEditor<List<Int>>, EditorNotation<List<Int>> {
        private val size = 5
        private val variant = VariantId("queens/n5")
        override val gameId get() = GameId("queens")
        override val variants = listOf(variant)
        override val draftFormatVersion = 1
        override val notationVersion = 1

        override fun newDraft(v: VariantId): List<Int> = List(size * size) { 0 }
        override fun variantOf(draft: List<Int>): VariantId = variant
        override fun encodeDraft(state: DraftState<List<Int>>): ByteArray =
            state.draft.map { it.toByte() }.toByteArray()
        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> =
            DecodeResult.Ok(DraftState(bytes.map { it.toInt() }))
        override fun analyze(draft: List<Int>, budget: Budget): Verdict =
            Verdict.Incomplete(IncompleteReason("fake", 0))
        override fun columns(draft: List<Int>): Int = size

        override fun parse(text: String): NotationResult<List<Int>> {
            val rows = text.lines().filter { it.isNotBlank() }
            if (rows.size != size) return NotationResult.Err(1, 1, "expected $size rows")
            val regions = ArrayList<Int>(size * size)
            for ((index, row) in rows.withIndex()) {
                if (row.length != size) return NotationResult.Err(index + 2, 1, "row ${index + 1} wrong width")
                for ((col, ch) in row.withIndex()) {
                    if (ch !in '0'..'4') return NotationResult.Err(index + 2, col + 1, "unexpected '$ch'")
                    regions += ch - '0'
                }
            }
            return NotationResult.Ok(regions)
        }

        override fun print(draft: List<Int>): String =
            NotationHeader(gameId.value, notationVersion).line + "\n" +
                draft.chunked(size).joinToString("") { row -> row.joinToString("") { it.toString() } + "\n" }
    }

    /** TestGames' queens plugin with the notation editor attached. */
    private fun queensPlugin(): GamePlugin<*, *, *> {
        val base = TestGames.plugin(GameId("queens"))
        return GamePlugin(
            definition = base.definition,
            rules = base.rules,
            session = base.session,
            score = base.score,
            stateOf = base.stateOf,
            initialSession = base.initialSession,
            refOf = base.refOf,
            replaySession = base.replaySession,
            rendererModel = base.rendererModel,
            editor = Queens,
        )
    }
}
