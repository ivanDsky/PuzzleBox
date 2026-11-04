package com.ivandsky.puzzlebox.konsist.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModuleReaderTest {
    private val tree = File(javaClass.getResource("/fixtures/_tree")!!.toURI())
    private val modules = ModuleReader.read(tree).associateBy { it.path }

    @Test
    fun listsIncludesInSettingsOrderWithoutKonsist() {
        assertEquals(
            listOf(":app", ":core:model", ":games:zip-line", ":games:a", ":games:a-ui"),
            ModuleReader.read(tree).map { it.path },
        )
    }

    @Test
    fun dirTurnsColonsIntoSlashes() {
        assertEquals("app", modules.getValue(":app").dir)
        assertEquals("games/zip-line", modules.getValue(":games:zip-line").dir)
    }

    @Test
    fun namespaceIsThePackageRoot() {
        assertEquals("com.example.tree.app", modules.getValue(":app").packageRoot)
    }

    @Test
    fun jvmFallbackRoots() {
        assertEquals("com.ivandsky.puzzlebox.model", modules.getValue(":core:model").packageRoot)
        assertEquals("com.ivandsky.puzzlebox.games.zipline", modules.getValue(":games:zip-line").packageRoot)
    }

    @Test
    fun pluginIdsComeFromTheBuildFile() {
        assertEquals(setOf("android.application", "kotlin.compose"), modules.getValue(":app").plugins)
        assertEquals(setOf("kotlin.jvm"), modules.getValue(":core:model").plugins)
    }

    @Test
    fun missingBuildFileFailsNamingTheModule() {
        val root = rootWith(":core:x", buildFile = null)
        assertFailsNaming(root, ":core:x")
    }

    @Test
    fun missingNamespaceWithoutFallbackFailsNamingTheModule() {
        val root = rootWith(":tools:y", buildFile = "plugins { kotlin(\"jvm\") }")
        assertFailsNaming(root, ":tools:y")
    }

    @Test
    fun androidModuleWithoutNamespaceFailsEvenWhenAFallbackExists() {
        val root = rootWith(":core:x", buildFile = "plugins { alias(libs.plugins.puzzlebox.android.library) }")
        assertFailsNaming(root, ":core:x", "Android module :core:x declares no namespace")
    }

    private fun assertFailsNaming(root: File, path: String, message: String = path) {
        try {
            ModuleReader.read(root)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message, e.message!!.contains(path) && e.message!!.contains(message))
        }
    }

    private fun rootWith(path: String, buildFile: String?): File {
        val root = kotlin.io.path.createTempDirectory("module-reader").toFile().apply { deleteOnExit() }
        File(root, "settings.gradle.kts").writeText("include(\"$path\")")
        val dir = File(root, path.removePrefix(":").replace(':', '/')).apply { mkdirs() }
        if (buildFile != null) File(dir, "build.gradle.kts").writeText(buildFile)
        return root
    }
}
