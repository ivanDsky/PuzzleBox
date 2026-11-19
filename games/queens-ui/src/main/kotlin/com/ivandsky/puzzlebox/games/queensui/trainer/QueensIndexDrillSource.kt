package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueIndex
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.games.queens.QueensDefinition

/**
 * Archive dailies that need a technique, read from the game's technique index: the index of every Queens daily's
 * technique counts, shipped as the classpath resource at [RESOURCE_PATH] (the path the index ticket writes to).
 * No candidates while the resource is missing, unreadable, another game's, or has no entry for the technique, so a
 * build without the index simply offers none. Candidates come most target steps first, ties by ascending puzzle
 * number, so the same index always gives the same order.
 *
 * [indexText] is the index file's text, null when there is none; it is read once, on the first ask.
 */
internal class QueensIndexDrillSource(
    private val indexText: () -> String? = ::readShippedIndex,
) : DrillSource {

    private val entries: Map<Int, Map<TechniqueId, Int>> by lazy {
        val text = indexText() ?: return@lazy emptyMap()
        val index = (TechniqueIndex.read(text) as? DecodeResult.Ok)?.value
        if (index != null && index.gameId == QueensDefinition.id) index.entries else emptyMap()
    }

    override fun candidates(technique: TechniqueId): List<DrillCandidate> = entries
        .mapNotNull { (number, counts) -> counts[technique]?.let { DrillCandidate.Archive(number, it) } }
        .sortedWith(compareByDescending<DrillCandidate.Archive> { it.targetSteps }.thenBy { it.number })

    companion object {
        /** Where the technique index of the Queens dailies is shipped on the classpath. */
        const val RESOURCE_PATH = "/trainer/queens-index.txt"
    }
}

private fun readShippedIndex(): String? =
    QueensIndexDrillSource::class.java.getResourceAsStream(QueensIndexDrillSource.RESOURCE_PATH)
        ?.use { it.readBytes().toString(Charsets.UTF_8) }
