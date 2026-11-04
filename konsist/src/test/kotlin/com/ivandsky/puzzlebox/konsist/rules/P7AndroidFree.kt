package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Module
import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object P7AndroidFree : Rule {
    override val id = "P7"

    private const val JVM_LIBRARY_PLUGIN = "puzzlebox.jvm.library"
    private const val DOMAIN_MODULE = ":core:domain"

    /** An import or an inline qualified name; the lookbehind keeps `com.example.android.x` and `myandroid.x` out. */
    private val reference = Regex("""(?<![\w.])(?:androidx?\.\w+|org\.koin\.androidx?(?!\w))(?:\.\w+)*""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filter { isAndroidFree(it.module) }
        val violations = files.mapNotNull { file ->
            val references = reference.findAll(file.codeText).map { it.value }.distinct().toList()
            if (references.isEmpty()) null else Violation(file.path, null, "remove Android references: ${references.joinToString()}")
        }
        return RuleResult(files.size, violations)
    }

    private fun isAndroidFree(module: Module) = JVM_LIBRARY_PLUGIN in module.plugins || module.path == DOMAIN_MODULE
}
