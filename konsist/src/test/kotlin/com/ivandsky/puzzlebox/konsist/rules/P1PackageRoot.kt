package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object P1PackageRoot : Rule {
    override val id = "P1"

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION)
        val violations = files.mapNotNull { file ->
            val root = file.module.packageRoot
            val pkg = file.packageName
            // The dot guards sibling roots that merely share a prefix (`zip` vs `zipx`).
            if (pkg == root || pkg.startsWith("$root.")) null
            else Violation(file.path, null, "move to package under $root")
        }
        return RuleResult(files.size, violations)
    }
}
