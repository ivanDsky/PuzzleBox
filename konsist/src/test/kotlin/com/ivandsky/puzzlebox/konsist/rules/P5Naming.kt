package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoParentProvider

object P5Naming : Rule {
    override val id = "P5"
    override val kinds = setOf(SourceSetKind.PRODUCTION, SourceSetKind.TEST)

    private val BANNED_SUFFIXES = listOf("Manager", "Helper", "Util", "Utils", "Impl")
    private val INTERFACE_SUFFIXES = listOf("Repository", "Source", "Gateway", "Store", "Service", "Provider")
    private const val FAKE = "Fake"

    /** A type to judge: its name, its supertypes' simple names, and whether it can implement an interface. */
    private class Subject(val name: String, val parents: Set<String>, val implementing: Boolean, val path: String)

    override fun check(tree: SourceTree): RuleResult {
        val scopes = kinds.map { tree.scope(it) }
        val interfaces = scopes.flatMap { scope -> scope.interfaces() }
        val projectInterfaces = interfaces.map { it.name }.filter { name -> INTERFACE_SUFFIXES.any(name::endsWith) }.toSet()

        val subjects = scopes.flatMap { scope ->
            scope.classes().map { subject(tree, it, true) } +
                scope.objects().filterNot { it.hasCompanionModifier }.map { subject(tree, it, true) } +
                scope.interfaces().map { subject(tree, it, false) }
        }
        val violations = subjects.groupBy { it.path }.mapNotNull { (path, inFile) ->
            val problems = inFile.flatMap { problems(it, projectInterfaces) }
            if (problems.isEmpty()) null else Violation(path, null, problems.joinToString("; "))
        }
        return RuleResult(subjects.size, violations)
    }

    private fun <T> subject(tree: SourceTree, declaration: T, implementing: Boolean): Subject
        where T : KoNameProvider, T : KoParentProvider, T : KoContainingFileProvider =
        Subject(
            declaration.name,
            declaration.parents().map { it.name.substringBefore('<') }.toSet(),
            implementing,
            tree.fileFor(declaration.containingFile).path,
        )

    private fun problems(subject: Subject, projectInterfaces: Set<String>): List<String> {
        val found = mutableListOf<String>()
        BANNED_SUFFIXES.firstOrNull(subject.name::endsWith)?.let { found += "${subject.name}: banned suffix $it" }
        if (!subject.implementing) return found

        val fakeOf = subject.name.removePrefix(FAKE)
        if (subject.name.startsWith(FAKE) && fakeOf.firstOrNull()?.isUpperCase() == true && subject.parents.none { it.endsWith(fakeOf) }) {
            found += "${subject.name}: a Fake must implement a supertype ending in $fakeOf"
        }
        val implemented = subject.parents.filter { it in projectInterfaces }
        if (implemented.isNotEmpty() && implemented.none { subject.name.length > it.length && subject.name.endsWith(it) }) {
            found += "${subject.name}: implements ${implemented.joinToString()}; name it <Qualifier>${implemented.first()}"
        }
        return found
    }
}
