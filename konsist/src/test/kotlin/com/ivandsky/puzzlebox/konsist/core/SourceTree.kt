package com.ivandsky.puzzlebox.konsist.core

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File

class SourceTree private constructor(
    val root: File,
    val modules: List<Module>,
    private val all: List<SourceFile>,
) {
    private val byPath = all.associateBy { it.path }
    private val scopes = SourceSetKind.values().associateWith { kind -> lazy { KonsistScopes.over(root, files(kind)) } }

    fun files(kind: SourceSetKind): List<SourceFile> = all.filter { it.kind == kind }

    /** Parsed once per kind: building a scope over the real tree is the expensive step. */
    fun scope(kind: SourceSetKind): KoScope = scopes.getValue(kind).value

    fun fileFor(ko: KoFileDeclaration): SourceFile {
        val path = KonsistScopes.pathUnder(root, ko)
        return byPath[path] ?: error("${ko.path} (as $path) is not in the source tree rooted at $root")
    }

    companion object {
        private const val REPO_ROOT_PROPERTY = "puzzlebox.repoRoot"
        private const val FIXTURES_DIR = "konsist/src/test/resources/fixtures"

        fun load(root: File): SourceTree {
            check(root.isDirectory) { "Source tree root does not exist: $root" }
            val canonical = root.canonicalFile
            val modules = ModuleReader.read(canonical)
            return SourceTree(canonical, modules, SourceWalker.walk(canonical, modules))
        }

        val real: SourceTree by lazy { load(repoRoot(System.getProperty(REPO_ROOT_PROPERTY))) }

        fun fixture(id: String): SourceTree = load(File(repoRoot(System.getProperty(REPO_ROOT_PROPERTY)), "$FIXTURES_DIR/$id"))

        internal fun repoRoot(property: String?): File {
            checkNotNull(property) { "System property $REPO_ROOT_PROPERTY is not set; run through the :konsist test task" }
            return File(property)
        }
    }
}
