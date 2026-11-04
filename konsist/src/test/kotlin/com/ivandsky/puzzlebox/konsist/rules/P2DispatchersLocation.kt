package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object P2DispatchersLocation : Rule {
    override val id = "P2"

    private const val HINT = "inject a DispatcherProvider"
    private const val TESTING_MODULE = ":core:testing"
    private const val PROVIDER_PACKAGE_SUFFIX = ".common.dispatcher"

    // Import, member-import and inline forms all contain one of these two tokens, so one pattern covers them.
    private val reference = Regex("""\bkotlinx\.coroutines\.Dispatchers\b|\bDispatchers\.""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filterNot {
            it.module.path == TESTING_MODULE || it.packageName.endsWith(PROVIDER_PACKAGE_SUFFIX)
        }
        val violations = files.filter { reference.containsMatchIn(it.strippedText) }
            .map { Violation(it.path, null, HINT) }
        return RuleResult(files.size, violations)
    }
}
