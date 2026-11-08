package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Create root's draft analysis: one verdict per (id, updatedAt), settled on
 * [DispatcherProvider.default], dropped with its draft, null for a game this build cannot open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftAnalysisTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val fake = FakeGameEditor(GameId("fake"))
    private val bare = GameId("bare")
    private val games = GameRegistry(listOf(withEditor(fake), TestGames.plugin(bare)))

    private val lanes = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainRule.testDispatcher
        override val io: CoroutineDispatcher = mainRule.testDispatcher
        override val default: CoroutineDispatcher = mainRule.testDispatcher
    }

    @Test
    fun `a stamp is analysed once and again after an edit`() = runTest(mainRule.testDispatcher) {
        val analysis = DraftAnalysis(backgroundScope, games, lanes)
        val first = draft("a", minutesAgo = 5, marks = 1)

        analysis.analyzeNew(listOf(first))
        analysis.analyzeNew(listOf(first))
        runCurrent()
        assertEquals(1, fake.analyzed.size)
        assertTrue(analysis.verdicts.value.getValue(first.stamp()) is Verdict.Unique)

        val edited = draft("a", minutesAgo = 0, marks = 3)
        analysis.analyzeNew(listOf(edited))
        runCurrent()
        assertEquals(listOf(TestDraft(1), TestDraft(3)), fake.analyzed)
        assertEquals(setOf(edited.stamp()), analysis.verdicts.value.keys)
    }

    @Test
    fun `a deleted draft's verdict is dropped and a restored one is analysed again`() =
        runTest(mainRule.testDispatcher) {
            val analysis = DraftAnalysis(backgroundScope, games, lanes)
            val kept = draft("kept", minutesAgo = 5, marks = 1)
            val gone = draft("gone", minutesAgo = 4, marks = 2)
            analysis.analyzeNew(listOf(kept, gone))
            runCurrent()
            assertEquals(setOf(kept.stamp(), gone.stamp()), analysis.verdicts.value.keys)

            analysis.analyzeNew(listOf(kept))
            runCurrent()
            assertEquals(setOf(kept.stamp()), analysis.verdicts.value.keys)

            analysis.analyzeNew(listOf(kept, gone))
            runCurrent()
            assertEquals(3, fake.analyzed.size)
            assertEquals(setOf(kept.stamp(), gone.stamp()), analysis.verdicts.value.keys)
        }

    @Test
    fun `a game without an editor settles to a null verdict`() = runTest(mainRule.testDispatcher) {
        val analysis = DraftAnalysis(backgroundScope, games, lanes)
        val orphan = draft("o", minutesAgo = 1, marks = 1, game = bare)

        analysis.analyzeNew(listOf(orphan))

        assertTrue(orphan.stamp() in analysis.verdicts.value)
        assertNull(analysis.verdicts.value.getValue(orphan.stamp()))
        assertTrue(fake.analyzed.isEmpty())
    }

    private fun Draft.stamp() = DraftStamp(id, updatedAt)

    private fun draft(id: String, minutesAgo: Long, marks: Int, game: GameId = fake.gameId) = Draft(
        id = id,
        gameId = game,
        variant = VariantId("${game.value}/v1"),
        draftFormatVersion = 1,
        blob = byteArrayOf(1, marks.toByte()),
        title = "Draft $id",
        updatedAt = NOW - Duration.ofMinutes(minutesAgo),
        testSolve = null,
        reservedCode = null,
        imported = false,
        authorName = "",
        note = "",
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")

        fun withEditor(editor: GameEditor<*>): GamePlugin<*, *, *> {
            val base = TestGames.plugin(editor.gameId)
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
                editor = editor,
            )
        }
    }
}
