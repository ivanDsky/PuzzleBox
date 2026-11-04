package com.ivandsky.puzzlebox.konsist.core

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File
import java.nio.file.Path

object KonsistScopes {
    /** scopeFromFiles resolves paths against Konsist's own root, so absolute paths must be relativized. */
    fun over(root: File, files: List<SourceFile>): KoScope {
        val konsistRoot = Path.of(Konsist.projectRootPath)
        val relative = files.map { konsistRoot.relativize(root.toPath().resolve(it.path)).toString() }
        return Konsist.scopeFromFiles(relative)
    }

    /** Root-relative '/'-separated path of a Konsist file, whether Konsist reports it absolute or relative. */
    fun pathUnder(root: File, ko: KoFileDeclaration): String {
        val reported = Path.of(ko.path)
        val absolute = if (reported.isAbsolute) reported else Path.of(Konsist.projectRootPath).resolve(reported)
        return root.toPath().toRealPath().relativize(absolute.toRealPath()).toString().replace(File.separatorChar, '/')
    }
}
