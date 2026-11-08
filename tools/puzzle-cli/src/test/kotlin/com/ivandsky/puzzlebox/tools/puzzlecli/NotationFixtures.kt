package com.ivandsky.puzzlebox.tools.puzzlecli

import java.io.File

/** One fixture file under `games/<g>/src/test/resources/notation/`, with its `# expect:` line. */
data class NotationFixture(
    val game: String,
    val file: File,
) {
    val text: String by lazy { file.readText() }

    /** `# expect: <verdict> [blockId…]` — the first word is the verdict, the rest block ids. */
    val expectVerdict: String by lazy { expectParts().first() }

    /** The block ids after the verdict word, in the game's publish order. */
    val expectBlocks: List<String> by lazy { expectParts().drop(1) }

    private fun expectParts(): List<String> =
        text.lineSequence()
            .first { it.startsWith("# expect:") }
            .removePrefix("# expect:")
            .trim()
            .split(" ")
            .filter { it.isNotEmpty() }
}

/** The fixture loader every parity test shares (§9.3): walks `notation.fixtures` under the repo root. */
object NotationFixtures {

    /** Every fixture under `notation.fixtures`: `games/<g>/src/test/resources/notation/<name>.txt`. */
    fun all(): List<NotationFixture> {
        val root = root()
        return root.walkTopDown()
            .filter { it.isFile && it.parentFile.name == "notation" && it.extension == "txt" }
            .filter { it.readText().lineSequence().any { line -> line.startsWith("# expect:") } }
            .map { file ->
                val game = file.toRelativeString(root).split("/").getOrNull(1) ?: file.parentFile.name
                NotationFixture(game, file)
            }
            .sortedBy { it.file.path }
            .toList()
    }

    /** Every fixture of one game, e.g. `queens`. */
    fun of(game: String): List<NotationFixture> = all().filter { it.game == game }

    private fun root(): File = File(System.getProperty("notation.fixtures"))
}
