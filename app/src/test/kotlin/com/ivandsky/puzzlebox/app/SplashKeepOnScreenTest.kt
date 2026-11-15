package com.ivandsky.puzzlebox.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The keep-on-screen condition runs inside MainActivity.onCreate, which a JVM test cannot reach
// without the whole Koin graph and a window. So the wiring is pinned to the source, read the way
// the other app tests read theirs: Gradle runs tests in the module dir.
class SplashKeepOnScreenTest {

    private val source = File("src/main/kotlin/com/ivandsky/puzzlebox/app/MainActivity.kt").readText()

    @Test
    fun `the system splash is kept exactly while the release policy keeps it`() {
        assertTrue(Regex("""setKeepOnScreenCondition\s*\{\s*splashPolicy\.keepSplash\(\)\s*\}""").containsMatchIn(source))
    }

    @Test
    fun `the old theme-mode composition hold is gone`() {
        assertFalse(source.contains("themeMode"))
        assertFalse(source.contains("collectAsState"))
    }
}
