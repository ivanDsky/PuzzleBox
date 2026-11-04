package com.ivandsky.puzzlebox.konsist.core

import com.lemonappdev.konsist.api.Konsist
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeTest {
    private val repoRoot: Path = Path.of(requireNotNull(System.getProperty("puzzlebox.repoRoot")))

    @Test
    fun konsistRootIsTheRepoRoot() {
        assertEquals(repoRoot.toRealPath(), Path.of(Konsist.projectRootPath).toRealPath())
    }

    @Test
    fun parsesAFileFromAnotherModule() {
        val file = repoRoot.resolve("core/model/src/main/kotlin/com/ivandsky/puzzlebox/model/GameId.kt")
        // scopeFromFiles resolves against Konsist's own root, so absolute paths must be relativized.
        val relative = Path.of(Konsist.projectRootPath).relativize(file).toString()

        val scope = Konsist.scopeFromFiles(listOf(relative))

        assertTrue("parsed: ${scope.files.map { it.path }}", "GameId" in scope.classes().map { it.name })
    }
}
