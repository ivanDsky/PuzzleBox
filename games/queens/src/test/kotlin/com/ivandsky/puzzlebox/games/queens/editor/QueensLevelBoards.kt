package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Finished boards the scored grader's tests and measurement read: the label fixtures, the daily corpus and boards written as digits. */
object QueensLevelBoards {

    /** A board written row by row, one digit per cell's region. */
    fun board(size: Int, regions: String): QueensDraft = QueensDraft(size, regions.map { it.digitToInt() })

    /** `queens-difficulty-<label>.json`, the 5×5 board of one legacy label. */
    fun fixture(label: String): QueensDraft = resource("queens-difficulty-$label.json").draft()

    /** The 806 daily boards of the hint corpus, sizes 5 and 7 to 11. */
    fun dailies(): List<QueensPuzzle> =
        resource("queens-hint-corpus.json").jsonArray.map { board -> board.draft().let { QueensPuzzle(it.size, it.regions) } }

    private fun resource(name: String): JsonElement =
        Json.parseToJsonElement(QueensLevelBoards::class.java.getResourceAsStream("/fixtures/$name")!!.readBytes().decodeToString())

    private fun JsonElement.draft(): QueensDraft = QueensDraft(
        size = jsonObject.getValue("size").jsonPrimitive.content.toInt(),
        regions = jsonObject.getValue("regions").jsonArray.map { it.jsonPrimitive.content.toInt() },
    )
}
