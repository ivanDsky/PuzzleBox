package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** The frozen v1 bytes written by the ReplaceDraft-only codec must keep decoding (ADR-0005). */
class ZipDraftFixtureTest {

    private fun blank(n: Int) = ZipDraft(n, emptySet(), emptyList(), emptyList())

    private fun fixture(): ByteArray =
        ZipDraftFixtureTest::class.java.getResourceAsStream("/fixtures/zip-draft-v1.bin")!!.use { it.readBytes() }

    private fun commands(): ByteArray =
        ZipDraftFixtureTest::class.java.getResourceAsStream("/fixtures/zip-draft-v1-commands.bin")!!.use { it.readBytes() }

    /** What `zip-draft-v1.bin` holds: a 5→4 resize, a path with one wall and two waypoints, one undone shortening. */
    private fun fixtureState(): DraftState<ZipDraft> {
        val wall = setOf(Wall(Cell(1), WallDirection.DOWN))
        val drawn = ZipDraft(4, wall, listOf(0, 1, 2, 3), listOf(0, 3))
        val shorter = ZipDraft(4, wall, listOf(0, 1, 2), listOf(0, 2))
        return DraftState(
            drawn,
            CommandHistory(
                listOf(ReplaceDraft(blank(5), blank(4)), ReplaceDraft(blank(4), drawn)),
                listOf(ReplaceDraft(drawn, shorter)),
            ),
        )
    }

    @Test
    fun `frozen v1 fixture decodes to the expected draft and both stacks`() {
        assertEquals(fixtureState(), (ZipDraftCodec.decode(fixture()) as DecodeResult.Ok).value)
    }

    @Test
    fun `encoding the fixture state reproduces the frozen bytes`() {
        assertArrayEquals(fixture(), ZipDraftCodec.encode(fixtureState()))
    }

    /** What `zip-draft-v1-commands.bin` holds: one step of every command tag, on both stacks. */
    private fun commandsState(): DraftState<ZipDraft> {
        val start = ZipDraft(4, emptySet(), listOf(0, 1, 2, 3), listOf(0, 2, 3))
        return DraftState(
            start,
            CommandHistory(
                listOf(
                    ZipDraftCommand.PathEdit(emptyList(), listOf(0, 1, 2, 3), emptyList(), listOf(0, 3)),
                    ZipDraftCommand.ToggleMark(2, listOf(0, 3), listOf(0, 2, 3)),
                    ZipDraftCommand.ToggleWall(Wall(Cell(1), WallDirection.RIGHT)),
                    ZipDraftCommand.ClearWalls(setOf(Wall(Cell(1), WallDirection.DOWN))),
                ),
                listOf(
                    ZipDraftCommand.ClearWalls(setOf(Wall(Cell(2), WallDirection.RIGHT))),
                    ZipDraftCommand.ToggleWall(Wall(Cell(2), WallDirection.RIGHT)),
                    ZipDraftCommand.ToggleMark(1, listOf(0, 2), listOf(0, 1, 2)),
                    ZipDraftCommand.PathEdit(listOf(0, 1, 2, 3), listOf(0, 1, 2), listOf(0, 2, 3), listOf(0, 2)),
                ),
            ),
        )
    }

    @Test
    fun `frozen v1 commands fixture decodes to the expected draft and both stacks`() {
        assertEquals(commandsState(), (ZipDraftCodec.decode(commands()) as DecodeResult.Ok).value)
    }

    @Test
    fun `encoding the commands fixture state reproduces the frozen bytes`() {
        assertArrayEquals(commands(), ZipDraftCodec.encode(commandsState()))
    }
}
