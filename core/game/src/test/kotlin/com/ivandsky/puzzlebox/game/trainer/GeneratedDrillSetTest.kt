package com.ivandsky.puzzlebox.game.trainer

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedDrillSetTest {

    private val game = GameId("minisudoku")
    private val pointing = TechniqueId("pointing")
    private val claiming = TechniqueId("claiming")

    private fun drill(technique: TechniqueId, steps: Int, vararg payload: Int, game: GameId = this.game): GeneratedDrillSet.Drill {
        val bytes = ByteArray(payload.size) { payload[it].toByte() }
        return GeneratedDrillSet.Drill(
            GeneratedDrillSet.idOf(bytes),
            technique,
            steps,
            Puzzle(game, VariantId("sudoku/6x6"), 1, bytes),
        )
    }

    private fun set(vararg drills: GeneratedDrillSet.Drill) = GeneratedDrillSet(game, 2, drills.toList())

    private fun malformed(text: String): String {
        val result = GeneratedDrillSet.read(text)
        assertTrue("expected Err, got $result", result is DecodeResult.Err)
        val error = (result as DecodeResult.Err).error
        assertTrue("expected Malformed, got $error", error is DecodeError.Malformed)
        return (error as DecodeError.Malformed).detail
    }

    private fun lineOf(drill: GeneratedDrillSet.Drill): String =
        GeneratedDrillSet.write(set(drill)).lines().drop(3).first()

    private val header = "generated-drills 1\ngame minisudoku\ntechnique-set 2\n"

    @Test
    fun `write then read gives back an equal set, payload bytes included`() {
        val original = set(
            drill(pointing, 2, 1, 2, 3, 250, 0, 255),
            drill(claiming, 1, 9, 9, 9),
            drill(pointing, 3, 4, 5, 6),
        )

        val back = (GeneratedDrillSet.read(GeneratedDrillSet.write(original)) as DecodeResult.Ok).value

        assertEquals(original.gameId, back.gameId)
        assertEquals(original.techniqueSetVersion, back.techniqueSetVersion)
        assertEquals(original.drills.toSet(), back.drills.toSet())
    }

    @Test
    fun `an empty set round-trips`() {
        val empty = GeneratedDrillSet(game, 1, emptyList())

        assertEquals(DecodeResult.Ok(empty), GeneratedDrillSet.read(GeneratedDrillSet.write(empty)))
    }

    @Test
    fun `write is byte-stable whatever order the list holds, sorted by technique then id`() {
        val a = drill(pointing, 2, 1)
        val b = drill(pointing, 3, 2)
        val c = drill(claiming, 1, 3)

        val text = GeneratedDrillSet.write(set(b, c, a))

        assertEquals(GeneratedDrillSet.write(set(a, b, c)), text)
        assertEquals(
            listOf(c.id) + listOf(a.id, b.id).sorted(),
            text.lines().drop(3).filter { it.isNotEmpty() }.map { it.substringBefore(' ') },
        )
        assertTrue(text.startsWith(header))
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun `a line has five words with the payload in base64`() {
        val one = drill(pointing, 4, 1, 2, 3)

        assertEquals(
            "${one.id} pointing=4 sudoku/6x6 1 ${Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))}",
            lineOf(one),
        )
    }

    @Test
    fun `the id is g- and twelve hex digits of the payload, equal for equal bytes`() {
        val id = GeneratedDrillSet.idOf(byteArrayOf(1, 2, 3))

        assertTrue(id, Regex("g-[0-9a-f]{12}").matches(id))
        assertEquals(id, GeneratedDrillSet.idOf(byteArrayOf(1, 2, 3)))
        assertNotEquals(id, GeneratedDrillSet.idOf(byteArrayOf(1, 2, 4)))
        // SHA-256 of the three bytes 01 02 03 starts 039058c6f2c0cb49.
        assertEquals("g-039058c6f2c0", id)
    }

    @Test
    fun `read ignores blank lines and takes any line order`() {
        val a = drill(pointing, 2, 1)
        val b = drill(claiming, 1, 2)
        val text = header + "\n" + lineOf(a) + "\n\n" + lineOf(b) + "\n"

        val back = (GeneratedDrillSet.read(text) as DecodeResult.Ok).value

        assertEquals(listOf(a, b), back.drills)
    }

    @Test
    fun `a newer schema is UnsupportedVersion, never half-read`() {
        val result = GeneratedDrillSet.read("generated-drills 2\ngame minisudoku\ntechnique-set 2\nwhatever")

        assertEquals(DecodeResult.Err(DecodeError.UnsupportedVersion(2, 1)), result)
    }

    @Test
    fun `a broken header is Malformed`() {
        assertTrue(malformed("").contains("generated-drills"))
        assertTrue(malformed("technique-index 1\ngame minisudoku\ntechnique-set 2\n").contains("generated-drills"))
        assertTrue(malformed("generated-drills x\ngame minisudoku\ntechnique-set 2\n").contains("schema version"))
        assertTrue(malformed("generated-drills 0\ngame minisudoku\ntechnique-set 2\n").contains("schema version"))
        assertTrue(malformed("generated-drills 1\n").contains("game"))
        assertTrue(malformed("generated-drills 1\ngame minisudoku\n").contains("technique-set"))
        assertTrue(malformed("generated-drills 1\ntechnique-set 2\ngame minisudoku\n").contains("game"))
        assertTrue(malformed("generated-drills 1\ngame mini sudoku\ntechnique-set 2\n").contains("game id"))
        assertTrue(malformed("generated-drills 1\ngame minisudoku\ntechnique-set -1\n").contains("technique-set"))
    }

    @Test
    fun `a line that is not five words is Malformed`() {
        val one = drill(pointing, 2, 1)

        assertTrue(malformed(header + one.id + " pointing=2 sudoku/6x6 1\n").contains("expected"))
        assertTrue(malformed(header + lineOf(one) + " extra\n").contains("expected"))
        assertTrue(malformed(header + "nonsense\n").contains("expected"))
    }

    @Test
    fun `a bad technique, target count, format or variant is Malformed`() {
        val one = drill(pointing, 2, 1)
        val line = lineOf(one)

        assertTrue(malformed(header + line.replace("pointing=2", "pointing")).contains("expected '<technique>=<steps>'"))
        assertTrue(malformed(header + line.replace("pointing=2", "Pointing=2")).contains("technique id"))
        assertTrue(malformed(header + line.replace("pointing=2", "pointing=0")).contains("at least 1"))
        assertTrue(malformed(header + line.replace("pointing=2", "pointing=x")).contains("target steps"))
        assertTrue(malformed(header + line.replace("sudoku/6x6 1", "sudoku/6x6 v1")).contains("format version"))
        assertTrue(malformed(header + line.replace("pointing=2 sudoku/6x6", "pointing=2 ")).contains("variant is empty"))
    }

    @Test
    fun `payload that is not base64 is Malformed`() {
        val one = drill(pointing, 2, 1)
        val line = lineOf(one)
        val payload = line.substringAfterLast(' ')

        assertTrue(malformed(header + line.replace(payload, "@@@@")).contains("base64"))
    }

    @Test
    fun `an id that is not the id of its payload is Malformed`() {
        val one = drill(pointing, 2, 1, 2, 3)
        val forged = lineOf(one).replace(one.id, "g-000000000000")

        assertTrue(malformed(header + forged + "\n").contains("id of its payload"))
    }

    @Test
    fun `a payload edited after the fact breaks its id`() {
        val one = drill(pointing, 2, 1, 2, 3)
        val edited = lineOf(one).replace(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)), Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 4)))

        assertTrue(malformed(header + edited + "\n").contains("id of its payload"))
    }

    @Test
    fun `a drill listed twice is Malformed`() {
        val one = drill(pointing, 2, 1)

        assertTrue(malformed(header + lineOf(one) + "\n" + lineOf(one) + "\n").contains("listed twice"))
    }

    @Test
    fun `the same drill under two techniques still repeats its id`() {
        val one = drill(pointing, 2, 1)
        val other = lineOf(one).replace("pointing=2", "claiming=1")

        assertTrue(malformed(header + lineOf(one) + "\n" + other + "\n").contains("listed twice"))
    }

    @Test
    fun `read stamps every drill with the file's game`() {
        val back = (GeneratedDrillSet.read(GeneratedDrillSet.write(set(drill(pointing, 2, 1)))) as DecodeResult.Ok).value

        assertEquals(game, back.drills.single().puzzle.gameId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `write refuses a drill of another game`() {
        GeneratedDrillSet.write(set(drill(pointing, 2, 1, game = GameId("queens"))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `write refuses an id that is not the id of the payload`() {
        val one = drill(pointing, 2, 1)

        GeneratedDrillSet.write(set(one.copy(id = "g-000000000000")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `write refuses a repeated drill`() {
        val one = drill(pointing, 2, 1)

        GeneratedDrillSet.write(set(one, one))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `write refuses a target count below 1`() {
        GeneratedDrillSet.write(set(drill(pointing, 0, 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `write refuses a technique id that is not lowercase words`() {
        GeneratedDrillSet.write(set(drill(TechniqueId("Pointing Out"), 1, 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `write refuses a variant with a space in it`() {
        val bytes = byteArrayOf(1)
        val spaced = GeneratedDrillSet.Drill(GeneratedDrillSet.idOf(bytes), pointing, 1, Puzzle(game, VariantId("a b"), 1, bytes))

        GeneratedDrillSet.write(set(spaced))
    }
}
