package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

/**
 * ADR-0022 amendment 2026-11-18, decision 3: `:core:domain` declares and reads no release flag. A use case takes a
 * Boolean, and the Koin module of the feature that owns the flag supplies it.
 */
object P16DomainReadsNoFlag : Rule {
    override val id = "P16"

    private const val DOMAIN = ":core:domain"
    private const val FLAGS_PACKAGE = "com.ivandsky.puzzlebox.common.flags"

    /**
     * An import (plain, star or aliased), an inline qualified name, or a `package` line in or below the flags package;
     * the lookarounds keep `x.common.flags` and `flagsX` out.
     */
    private val reference = Regex("""(?<![\w.])${Regex.escape(FLAGS_PACKAGE)}(?!\w)(?:\.\w+)*""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filter { it.module.path == DOMAIN }
        val violations = files.mapNotNull { file ->
            val references = reference.findAll(file.codeText).map { it.value }.distinct().toList()
            if (references.isEmpty()) null else Violation(file.path, null, hint(references))
        }
        return RuleResult(files.size, violations)
    }

    private fun hint(found: List<String>): String =
        "remove ${found.joinToString()}: take the flag's value as a Boolean constructor parameter and read the flag " +
            "in the Koin module of the feature that owns it (ADR-0022 amendment 2026-11-18)"
}
