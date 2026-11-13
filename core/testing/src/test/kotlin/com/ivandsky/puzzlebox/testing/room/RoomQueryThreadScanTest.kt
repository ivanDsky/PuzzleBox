package com.ivandsky.puzzlebox.testing.room

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps new Robolectric Room builders on [RoomQueryThread]. Without it Room queries run on its four-thread pool,
 * two `tmpfile()` calls can overlap and the macOS test JVM can hang (see [RoomQueryThread]). Scans every module's
 * `src/test` sources, so a builder that skips the shared executor fails here, not as a flaky hang in another class.
 */
class RoomQueryThreadScanTest {

    private val builderCall = Regex("""\b(?:inMemoryDatabaseBuilder|databaseBuilder)\(""")
    private val sharedExecutor = "setQueryExecutor(RoomQueryThread.executor)"

    @Test
    fun everyTestRoomBuilderUsesTheSharedQueryExecutor() {
        val offenders = testSources().flatMap { file ->
            val source = file.readText()
            builderCall.findAll(source)
                .filterNot { hasSharedExecutor(source, it.range.first) }
                .map { "${file.relativeTo(repoRoot())}:${source.take(it.range.first).count { c -> c == '\n' } + 1}" }
        }
        assertTrue(
            "Room builders without .$sharedExecutor (import the module's RoomQueryThread):\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun theScanSeesTheRoomTests() {
        val builders = testSources().sumOf { builderCall.findAll(it.readText()).count() }
        assertTrue("expected the scan to find the Room test builders, found $builders", builders >= 30)
    }

    @Test
    fun theScanFlagsABuilderWithoutTheExecutor() {
        val plain = "Room.inMemoryDatabaseBuilder(context, Db::class.java).allowMainThreadQueries().build()"
        val guarded = "Room.inMemoryDatabaseBuilder(context, Db::class.java).$sharedExecutor.build()"
        assertEquals(false, hasSharedExecutor(plain, plain.indexOf("inMemory")))
        assertEquals(true, hasSharedExecutor(guarded, guarded.indexOf("inMemory")))
    }

    @Test
    fun theDatabaseModuleCopyMatchesTheSharedExecutor() {
        fun executorLine(file: File) = file.readLines().single { it.trimStart().startsWith("val executor") }
        val root = repoRoot()
        val shared = File(root, "core/testing/src/main/kotlin/com/ivandsky/puzzlebox/testing/room/RoomQueryThread.kt")
        val copy = File(root, "core/database/src/test/kotlin/com/ivandsky/puzzlebox/database/RoomQueryThread.kt")
        assertEquals(executorLine(shared), executorLine(copy))
    }

    /** The builder chain starting at [start] runs up to its `.build()`; the shared executor must be set inside it. */
    private fun hasSharedExecutor(source: String, start: Int): Boolean {
        val end = source.indexOf(".build()", start).takeIf { it >= 0 } ?: source.length
        return source.substring(start, end).contains(sharedExecutor)
    }

    private fun testSources(): List<File> = repoRoot().walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" && it.name != ".scratch" }
        .filter { it.isFile && it.extension == "kt" && "/src/test/" in it.invariantSeparatorsPath }
        .filter { it.name != "RoomQueryThreadScanTest.kt" }
        .toList()

    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "settings.gradle.kts not found above ${File("").absolutePath}" }
    }
}
