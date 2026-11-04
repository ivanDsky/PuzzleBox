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
    private val importLine = Regex("""(?m)^\s*import\s+((?:android|androidx)\.[\w.]*|org\.koin\.androidx?(?:\.[\w.]*)?)(?![\w])""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filter { isAndroidFree(it.module) }
        val violations = files.mapNotNull { file ->
            val imports = importLine.findAll(file.strippedText).map { it.groupValues[1] }.toList()
            if (imports.isEmpty()) null else Violation(file.path, null, "remove Android imports: ${imports.joinToString()}")
        }
        return RuleResult(files.size, violations)
    }

    private fun isAndroidFree(module: Module) = JVM_LIBRARY_PLUGIN in module.plugins || module.path == DOMAIN_MODULE
}
