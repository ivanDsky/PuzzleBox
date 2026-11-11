package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A new draft is a blank board of the asked variant, stored under a random codename no other draft has. */
class CreateDraftTest {

    private val drafts = FakeDraftRepository()
    private var ids = 0
    private val now = Instant.parse("2026-11-06T10:00:00Z")
    private val createDraft = CreateDraft(
        registry = GameRegistry(listOf(withEditor(alpha), withEditor(beta))),
        drafts = drafts,
        newId = { "draft-${++ids}" },
        clock = { now },
        random = Random(5),
    )

    @Test
    fun `the first draft is a blank board stored with every field set`() = runTest {
        val created = createDraft(alpha, SMALL)

        assertEquals(created, drafts.rows["draft-1"])
        assertEquals("draft-1", created.id)
        assertEquals(alpha, created.gameId)
        assertEquals(SMALL, created.variant)
        assertEquals(FakeEditor.draftFormatVersion, created.draftFormatVersion)
        assertArrayEquals(byteArrayOf(3), created.blob)
        assertEquals(ComputeCodename()(Random(5), emptySet()), created.title)
        assertEquals(now, created.updatedAt)
        assertNull(created.testSolve)
        assertNull(created.reservedCode)
        assertFalse(created.imported)
        assertEquals("", created.authorName)
        assertEquals("", created.note)
    }

    @Test
    fun `a codename is Adjective Noun`() = runTest {
        assertTrue(Regex("[A-Z][a-z]+ [A-Z][a-z]+").matches(createDraft(alpha, SMALL).title))
    }

    @Test
    fun `a new draft never takes an existing draft's title`() = runTest {
        val titles = List(30) { createDraft(alpha, SMALL).title }

        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun `the title it would pick is skipped when another draft has it`() = runTest {
        val wanted = ComputeCodename()(Random(5), emptySet())
        createDraft(alpha, SMALL)

        val second = CreateDraft(
            registry = GameRegistry(listOf(withEditor(alpha))),
            drafts = drafts,
            newId = { "other" },
            clock = { now },
            random = Random(5),
        )(alpha, SMALL)

        assertNotEquals(wanted, second.title)
    }

    /** A board of `size` blank cells, encoded as its one size byte. */
    private object FakeEditor : GameEditor<Int> {
        override val gameId: GameId get() = alpha
        override val variants: List<VariantId> = listOf(SMALL)
        override val draftFormatVersion: Int = 7
        override fun newDraft(variant: VariantId): Int = 3
        override fun variantOf(draft: Int): VariantId = SMALL
        override fun encodeDraft(state: DraftState<Int>): ByteArray = byteArrayOf(state.draft.toByte())
        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<Int>> = DecodeResult.Ok(DraftState(bytes[0].toInt()))
        override fun analyze(draft: Int, budget: Budget): Verdict = Verdict.Incomplete(IncompleteReason("fake", draft))
    }

    private companion object {
        val SMALL = VariantId("fake/small")

        fun withEditor(game: GameId): GamePlugin<*, *, *> {
            val base = TestGames.plugin(game)
            return GamePlugin(
                definition = base.definition,
                rules = base.rules,
                session = base.session,
                score = base.score,
                stateOf = base.stateOf,
                initialSession = base.initialSession,
                refOf = base.refOf,
                playAgainSession = base.playAgainSession,
                rendererModel = base.rendererModel,
                editor = FakeEditor,
            )
        }
    }
}
