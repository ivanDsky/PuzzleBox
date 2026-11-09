package com.ivandsky.puzzlebox.gameui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeEditorUi(override val gameId: GameId) : GameEditorUi {
    override fun sizeLabel(variant: VariantId): String = variant.value

    override fun createController(initial: EditorDraft<*>, onEdit: (EditorDraft<*>) -> Unit): GameEditorController =
        error("unused")

    @Composable
    override fun Board(controller: GameEditorController, verdict: Verdict, layout: PlayLayout, modifier: Modifier) = Unit
}

/** S18: one editor UI per game id; a duplicate is a wiring bug. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameEditorRegistryTest {

    @Test
    fun `look up a game's editor ui by id`() {
        val fake = FakeEditorUi(GameId("fake"))
        val registry = GameEditorRegistry(listOf(fake))

        assertSame(fake, registry[GameId("fake")])
        assertNull(registry[GameId("missing")])
        assertEquals(setOf(GameId("fake")), registry.ids)
    }

    @Test
    fun `a duplicate game id is a wiring bug and fails loudly`() {
        try {
            GameEditorRegistry(listOf(FakeEditorUi(GameId("fake")), FakeEditorUi(GameId("fake"))))
            assertTrue("expected IllegalArgumentException", false)
        } catch (expected: IllegalArgumentException) {
            // duplicates never silently overwrite
        }
    }
}
