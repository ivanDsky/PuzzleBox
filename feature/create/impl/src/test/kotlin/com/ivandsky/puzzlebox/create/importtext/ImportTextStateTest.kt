package com.ivandsky.puzzlebox.create.importtext

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.ImportDraftFromText
import com.ivandsky.puzzlebox.domain.ImportResult
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.NotationHeader
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The import holder (spec A23): submit flips `checking` first, then on [ImportResult.Imported]
 * hides the sheet and fires `onImported` once; a failure keeps the sheet open with the text
 * (TooLarge clears it) and sets `error`. A regions-board fake stands in for Queens
 * (ADR-0008: feature:create:impl has no games:* edge).
 */
class ImportTextStateTest {

    /** Queues the holder's coroutines until [runQueued], so `checking` is observable mid-flight. */
    private class GateDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun isDispatchNeeded(context: CoroutineContext) = true
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            synchronized(queue) { queue.addLast(block) }
        }

        fun runQueued() {
            while (true) {
                val block = synchronized(queue) { queue.removeFirstOrNull() } ?: return
                block.run()
            }
        }
    }

    private val drafts = FakeDraftRepository()
    private var ids = 0
    private val gate = GateDispatcher()

    private fun holder(onImported: (String) -> Unit = {}) = ImportTextState(
        importDraft = ImportDraftFromText(
            registry = GameRegistry(listOf(regionsPlugin())),
            createDraft = CreateDraft(
                registry = GameRegistry(listOf(regionsPlugin())),
                drafts = drafts,
                newId = { "draft-${++ids}" },
                clock = { Instant.parse("2026-11-08T10:00:00Z") },
            ),
            drafts = drafts,
            dispatchers = InlineDispatcherProvider,
        ),
        scope = CoroutineScope(gate),
        onImported = onImported,
    )

    @Test
    fun `submit checks first, then hides the sheet and fires onImported once`() = runTest {
        val seen = mutableListOf<String>()
        val holder = holder(onImported = seen::add)
        holder.open()
        holder.edit(fixture())
        holder.submit(fixture())

        assertTrue(holder.ui.value.checking)
        gate.runQueued()
        assertFalse(holder.ui.value.checking)
        assertFalse(holder.ui.value.visible)
        assertEquals(listOf("draft-1"), seen)
        assertTrue(drafts.rows.getValue("draft-1").imported)
    }

    @Test
    fun `a malformed import keeps the sheet, the text and sets the error`() = runTest {
        val holder = holder()
        holder.open()
        val bad = "no header here"
        holder.edit(bad)
        holder.submit(bad)
        gate.runQueued()

        assertTrue(holder.ui.value.visible)
        assertTrue(holder.ui.value.error is ImportResult.Malformed)
        assertEquals(bad, holder.ui.value.text)
    }

    @Test
    fun `a TooLarge import clears the text and keeps the sheet open`() = runTest {
        val holder = holder()
        holder.open()
        val huge = NotationHeader("regions", 1).line + "\n" + "0".repeat(64 * 1024 + 1) + "\n"
        holder.edit(huge)
        holder.submit(huge)
        gate.runQueued()

        assertTrue(holder.ui.value.visible)
        assertTrue(holder.ui.value.error is ImportResult.TooLarge)
        assertEquals("", holder.ui.value.text)
    }

    /** Runs work inline so an import finishes inside [GateDispatcher.runQueued]. */
    private object InlineDispatcherProvider : DispatcherProvider {
        private val inline = object : CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext) = false
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        }
        override val main: CoroutineDispatcher = inline
        override val io: CoroutineDispatcher = inline
        override val default: CoroutineDispatcher = inline
    }

    /** A painted 5x5 regions board with a final newline, as the notation prints it. */
    private fun fixture(): String =
        NotationHeader("regions", 1).line + "\n" +
            listOf("00111", "02211", "02333", "44433", "44443").joinToString("") { "$it\n" }

    /** Regions stand-in: a board of 5 rows of 5 base-36 ids, notation v1, no solution. */
    private object Regions : GameEditor<List<Int>>, EditorNotation<List<Int>> {
        private val size = 5
        private val variant = VariantId("regions/n5")
        override val gameId get() = GameId("regions")
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
            // NotationText blanks the header line before the game parser sees the text.
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

    /** TestGames' regions plugin with the notation editor attached. */
    private fun regionsPlugin(): GamePlugin<*, *, *> {
        val base = TestGames.plugin(GameId("regions"))
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
            editor = Regions,
        )
    }
}
