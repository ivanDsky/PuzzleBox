package com.ivandsky.puzzlebox.konsist.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModuleParsingTest {
    @Test
    fun sourceSetKind_testNames() {
        listOf("test", "androidTest", "testDebug", "testFixtures", "androidTestDebug", "commonTest", "jvmTest")
            .forEach { assertEquals(it, SourceSetKind.TEST, SourceSetKind.of(it)) }
    }

    @Test
    fun sourceSetKind_everythingElseIsProduction() {
        listOf("main", "debug", "release", "free", "latest", "common")
            .forEach { assertEquals(it, SourceSetKind.PRODUCTION, SourceSetKind.of(it)) }
    }

    @Test
    fun settings_singleAndMultiInclude() {
        val text = """
            include(":a")
            include(":b", ":c:d")
            include(
                ":e",
                ":f",
            )
        """.trimIndent()
        assertEquals(listOf(":a", ":b", ":c:d", ":e", ":f"), SettingsReader.includes(text))
    }

    @Test
    fun settings_includeBuildIsNotAModule() {
        val text = """
            pluginManagement { includeBuild("build-logic") }
            includeBuild("../other")
            include(":x")
        """.trimIndent()
        assertEquals(listOf(":x"), SettingsReader.includes(text))
    }

    @Test
    fun settings_realRepoStyle() {
        val root = File(System.getProperty("puzzlebox.repoRoot"))
        val paths = SettingsReader.includes(File(root, "settings.gradle.kts").readText())
        assertEquals(true, ":app" in paths && ":games:zip-ui" in paths && ":feature:play:impl" in paths)
        assertEquals(true, paths.none { it.contains("build-logic") })
        assertEquals(":konsist", paths.last())
    }

    @Test
    fun buildFile_namespaceAndPlugins() {
        val text = """
            plugins {
                alias(libs.plugins.puzzlebox.jvm.library)
                id("x.y")
                kotlin("jvm")
            }
            android { namespace = "com.ivandsky.puzzlebox.app" }
        """.trimIndent()
        val facts = BuildFileReader.read(text)
        assertEquals("com.ivandsky.puzzlebox.app", facts.namespace)
        assertEquals(setOf("puzzlebox.jvm.library", "x.y", "kotlin.jvm"), facts.plugins)
    }

    @Test
    fun buildFile_noNamespaceNoPlugins() {
        val facts = BuildFileReader.read("dependencies { }")
        assertNull(facts.namespace)
        assertEquals(emptySet<String>(), facts.plugins)
    }

    @Test
    fun packageRoot_coreAndGames() {
        assertEquals("com.ivandsky.puzzlebox.model", PackageRootFallback.forJvmModule(":core:model"))
        assertEquals("com.ivandsky.puzzlebox.foobar", PackageRootFallback.forJvmModule(":core:foo-bar"))
        assertEquals("com.ivandsky.puzzlebox.games.zip", PackageRootFallback.forJvmModule(":games:zip"))
        assertEquals("com.ivandsky.puzzlebox.games.zipui", PackageRootFallback.forJvmModule(":games:zip-ui"))
    }

    @Test
    fun packageRoot_unknownFamilyIsNull() {
        assertNull(PackageRootFallback.forJvmModule(":feature:home:api"))
        assertNull(PackageRootFallback.forJvmModule(":app"))
        assertNull(PackageRootFallback.forJvmModule(":core"))
    }
}
