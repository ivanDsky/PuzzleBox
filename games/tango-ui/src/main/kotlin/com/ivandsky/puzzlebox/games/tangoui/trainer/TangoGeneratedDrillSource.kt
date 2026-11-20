package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.trainer.GeneratedDrillSet
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.games.tango.TangoDefinition

/**
 * Generated puzzles for the techniques the Tango archive is short on, read from the game's bundled set: the file
 * `puzzle-cli generate-drills` writes, shipped as the classpath resource at [RESOURCE_PATH]. They ship with the app, so
 * they play offline. No candidates while the resource is missing, unreadable, another game's, or holds nothing for the
 * technique, so a build without the set simply offers none. Candidates come most target steps first, ties by
 * ascending id, so the same set always gives the same order.
 *
 * [setText] is the set file's text, null when there is none; it is read once, on the first ask.
 */
internal class TangoGeneratedDrillSource(
    private val setText: () -> String? = ::readShippedSet,
) : DrillSource {

    private val drills: List<GeneratedDrillSet.Drill> by lazy {
        val text = setText() ?: return@lazy emptyList()
        val set = (GeneratedDrillSet.read(text) as? DecodeResult.Ok)?.value
        if (set != null && set.gameId == TangoDefinition.id) set.drills else emptyList()
    }

    override fun candidates(technique: TechniqueId): List<DrillCandidate> = drills
        .filter { it.technique == technique }
        .sortedWith(compareByDescending<GeneratedDrillSet.Drill> { it.targetSteps }.thenBy { it.id })
        .map { DrillCandidate.Bundled(it.id, it.puzzle, it.targetSteps) }

    companion object {
        /** Where the generated Tango drills are shipped on the classpath. */
        const val RESOURCE_PATH = "/trainer/tango-generated.txt"
    }
}

private fun readShippedSet(): String? =
    TangoGeneratedDrillSource::class.java.getResourceAsStream(TangoGeneratedDrillSource.RESOURCE_PATH)
        ?.use { it.readBytes().toString(Charsets.UTF_8) }
