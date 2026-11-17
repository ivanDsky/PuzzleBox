package com.ivandsky.puzzlebox.network.linkedin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The opening and win bodies of the voyager save against the Mini Sudoku daily capture (research 03, Addendum 8), as parsed JSON. */
class VoyagerSaveGameBodyTest {

    private val post = MiniSudokuVoyagerFixtures.post()
    private val play = MiniSudokuVoyagerFixtures.play()
    private val page = MiniSudokuVoyagerFixtures.unplayedPage

    @Test
    fun `the opening body equals the captured first save`() {
        assertEquals(MiniSudokuVoyagerFixtures.openingRequest, parse(VoyagerSaveGameBody.opening(post, play, page)))
    }

    @Test
    fun `the win body equals the captured win save`() {
        assertEquals(MiniSudokuVoyagerFixtures.winRequest, parse(VoyagerSaveGameBody.win(post, play, page)))
    }

    @Test
    fun `only the opening asks for web metadata`() {
        assertEquals(JsonPrimitive(true), parse(VoyagerSaveGameBody.opening(post, play, page))["includeWebMetadata"])
        assertFalse(parse(VoyagerSaveGameBody.win(post, play, page)).containsKey("includeWebMetadata"))
    }

    @Test
    fun `a hint makes the win neither hint-free nor flawless and leaves it mistake-free`() {
        val hinted = MiniSudokuVoyagerFixtures.post(hintUsed = true)

        val record = record(VoyagerSaveGameBody.win(hinted, play, page))

        assertEquals(flags(flawless = false, hintFree = false, mistakeFree = true), flags(record))
    }

    @Test
    fun `a mistake makes the win neither mistake-free nor flawless and leaves it hint-free`() {
        val flawed = MiniSudokuVoyagerFixtures.post(mistakeFree = false)

        val record = record(VoyagerSaveGameBody.win(flawed, play, page))

        assertEquals(flags(flawless = false, hintFree = true, mistakeFree = false), flags(record))
    }

    @Test
    fun `an opening is never flawed by the hint or mistakes of the final play`() {
        val flawed = MiniSudokuVoyagerFixtures.post(hintUsed = true, mistakeFree = false)

        val record = record(VoyagerSaveGameBody.opening(flawed, play, page))

        assertEquals(flags(flawless = true, hintFree = true, mistakeFree = true), flags(record))
    }

    @Test
    fun `time is sent in whole seconds, rounded down and never below one`() {
        assertEquals(listOf(41L, 41L, 1L, 1L, 1L), listOf(41_000L, 41_999L, 1_999L, 999L, 0L).map { winSeconds(it) })
        assertEquals(listOf(3L, 1L), listOf(3_999L, 1L).map { openingSeconds(it) })
    }

    @Test
    fun `the record is keyed by the page's member with the post's own game type and puzzle number`() {
        val other = page.copy(profileId = "ACoAAFAKE0002", puzzleId = 999)

        val body = parse(VoyagerSaveGameBody.win(post, play, other))

        assertEquals(
            JsonPrimitive("urn:li:fsd_game:(ACoAAFAKE0002,7,463)"),
            body.getValue("variables").jsonObject.getValue("entity").jsonObject["resourceKey"],
        )
    }

    private fun winSeconds(timeMs: Long): Long =
        seconds(record(VoyagerSaveGameBody.win(MiniSudokuVoyagerFixtures.post(timeMs = timeMs), play, page)))

    private fun openingSeconds(timeMs: Long): Long =
        seconds(record(VoyagerSaveGameBody.opening(post, MiniSudokuVoyagerFixtures.play(openingTimeMs = timeMs), page)))

    private fun seconds(record: JsonObject): Long = (record.getValue("timeElapsed") as JsonPrimitive).content.toLong()

    private fun flags(flawless: Boolean, hintFree: Boolean, mistakeFree: Boolean) =
        listOf(JsonPrimitive(flawless), JsonPrimitive(hintFree), JsonPrimitive(mistakeFree))

    private fun flags(record: JsonObject) = record.getValue("completionAttributes").jsonObject.let {
        listOf(record["isFlawless"], it["isHintFree"], it["isMistakeFree"])
    }

    private fun record(json: String): JsonObject = MiniSudokuVoyagerFixtures.record(parse(json))

    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject
}
