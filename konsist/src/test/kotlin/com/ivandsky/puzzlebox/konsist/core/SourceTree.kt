package com.ivandsky.puzzlebox.konsist.core

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File

class SourceTree private constructor(
    val root: File,
    val modules: List<Module>,
    walk: SourceWalker.Walk,
) {
    private val all = walk.files
    private val byKo = all.associateBy { it.ko }

    /** Every file is parsed once, by the walk; a kind's scope is a slice of that one parse. */
    private val scopes = SourceSetKind.values().associateWith { kind ->
        val kos = files(kind).map { it.ko }.toSet()
        walk.scope.slice { it in kos }
    }

    fun files(kind: SourceSetKind): List<SourceFile> = all.filter { it.kind == kind }

    fun scope(kind: SourceSetKind): KoScope = scopes.getValue(kind)

    fun fileFor(ko: KoFileDeclaration): SourceFile =
        byKo[ko] ?: error("${ko.path} is not in the source tree rooted at $root")

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
