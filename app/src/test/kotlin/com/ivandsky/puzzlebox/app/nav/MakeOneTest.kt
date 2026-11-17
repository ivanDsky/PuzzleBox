package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GameRegistry
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
import org.junit.Test

/**
 * [MakeOne] over the real [CreateDraft] and a fake drafts store: that the blank draft gets the default size,
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
    fun `starts a blank draft of the game's default size, opened on top of the door's screen`() = runTest {
        makeOne()(zip)
        advanceUntilIdle()

        assertEquals(ZipEditor.defaultVariant, drafts.rows.getValue("draft-1").variant)
        assertEquals(listOf(PlayKey, result, EditorKey("draft-1")), navigator.state.currentStack)
    }

    @Test
    fun `a second tap while the draft is being stored makes one draft`() = runTest {
        val makeOne = makeOne()

        makeOne(zip)
        makeOne(zip)
        advanceUntilIdle()

        assertEquals(setOf("draft-1"), drafts.rows.keys)
        assertEquals(listOf(PlayKey, result, EditorKey("draft-1")), navigator.state.currentStack)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-04T12:00:00Z")
    }
}
