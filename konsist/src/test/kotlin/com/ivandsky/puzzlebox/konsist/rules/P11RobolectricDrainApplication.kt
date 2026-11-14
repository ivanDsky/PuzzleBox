package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.CommentStripper
import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

/**
 * Every app Robolectric test class must run under `DrainTestApplication` (class-level `@Config(application = …)`).
 * Why: Robolectric's post-test looper reset drops the task a Compose rule's teardown posts on `AndroidUiDispatcher.Main`,
 * which jams Compose for every later test in the JVM; only `DrainTestApplication.afterTest` drains it (#850).
 */
object P11RobolectricDrainApplication : Rule {
    override val id = "P11"
    override val kinds = setOf(SourceSetKind.TEST)

    private const val MODULE = ":app"
    private val SOURCE_SETS = setOf("test", "testDebug", "testRelease")
    private const val HINT = "add application = DrainTestApplication::class to the class-level @Config " +
        "(DrainTestApplication drains the main looper; see #850)"

    private val robolectricRunner =
        Regex("""^@(?:\w+\s*\.\s*)*RunWith\s*\(\s*(?:value\s*=\s*)?(?:\w+\s*\.\s*)*(?:RobolectricTestRunner|AndroidJUnit4)\s*::\s*class\s*\)$""")
    private val drainApplication =
        Regex("""\bapplication\s*=\s*(?:\w+\s*\.\s*)*DrainTestApplication\s*::\s*class\b""")

    override fun check(tree: SourceTree): RuleResult {
        val subjects = tree.scope(SourceSetKind.TEST).classes().filter { it.inScope(tree) && it.runsRobolectric() }
        val violations = subjects.filterNot { it.drains() }.groupBy { tree.fileFor(it.containingFile).path }
            .map { (path, classes) -> Violation(path, null, "${classes.joinToString { it.name }}: $HINT") }
        return RuleResult(subjects.size, violations)
    }

    private fun KoClassDeclaration.inScope(tree: SourceTree): Boolean {
        val file = tree.fileFor(containingFile)
        return file.module.path == MODULE && file.sourceSet in SOURCE_SETS
    }

    private fun KoClassDeclaration.runsRobolectric(): Boolean =
        annotations.any { robolectricRunner.matches(CommentStripper.strip(it.text).trim()) }

    /** Class-level only: a method's `@Config` is not in [annotations]. Matched on the stripped text, so a comment is no `@Config`. */
    private fun KoClassDeclaration.drains(): Boolean = annotations
        .filter { it.name.substringAfterLast('.') == "Config" }
        .any { drainApplication.containsMatchIn(CommentStripper.strip(it.text)) }
}
