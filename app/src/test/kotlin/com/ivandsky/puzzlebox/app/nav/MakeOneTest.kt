package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [MakeOne] over the real [CreateDraft] and a fake drafts store: which size the blank draft gets,
 * and that its Studio lands on top of the screen the door was on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MakeOneTest {

    private val zip = ZipDefinition.id
    private val drafts = FakeDraftRepository()
    private val games = GameRegistry(listOf(zipPlugin))
    private val result = ResultKey(gameId = zip.value, day = "2026-11-04", timeMs = 61_000, hintsUsed = 0)
    private val navigator = Navigator(NavigationState(PlayKey, listOf(listOf(PlayKey, result))))

    private fun TestScope.makeOne(): MakeOne {
        var next = 0
        return MakeOne(CreateDraft(games, drafts, newId = { "draft-${++next}" }, clock = { NOW }), games, this, navigator)
    }

    @Test
    fun `a size the Studio offers starts a blank draft of that size, opened on top of the door's screen`() = runTest {
        val smallest = ZipEditor.variants.first()
        assertNotEquals("the size under test is not the default", ZipEditor.defaultVariant, smallest)

        makeOne()(zip, smallest)
        advanceUntilIdle()

        assertEquals(smallest, drafts.rows.getValue("draft-1").variant)
        assertEquals(listOf(PlayKey, result, EditorKey("draft-1")), navigator.state.currentStack)
    }

    @Test
    fun `no size starts the game's default size`() = runTest {
        makeOne()(zip, null)
        advanceUntilIdle()

        assertEquals(ZipEditor.defaultVariant, drafts.rows.values.single().variant)
    }

    @Test
    fun `a size the Studio does not offer starts the default size`() = runTest {
        val tutorialOnly = VariantId("zip/n4")
        assertEquals("the Studio's sizes leave it out", false, tutorialOnly in ZipEditor.variants)

        makeOne()(zip, tutorialOnly)
        advanceUntilIdle()

        assertEquals(ZipEditor.defaultVariant, drafts.rows.values.single().variant)
        assertEquals(EditorKey("draft-1"), navigator.state.currentStack.last())
    }

    @Test
    fun `a second tap while the draft is being stored makes one draft`() = runTest {
        val makeOne = makeOne()

        makeOne(zip, null)
        makeOne(zip, null)
        advanceUntilIdle()

        assertEquals(setOf("draft-1"), drafts.rows.keys)
        assertEquals(listOf(PlayKey, result, EditorKey("draft-1")), navigator.state.currentStack)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-04T12:00:00Z")
    }
}
