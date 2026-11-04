package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.CommentStripper
import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.Scan
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration

object P3ViewModelShape : Rule {
    override val id = "P3"

    private const val VIEW_MODEL = "ViewModel"
    private val IMPL_MODULE = Regex(""":feature:[^:]+:impl""")

    /** A11: `MutableStateFlow(…)` and lowercase Compose factories such as `mutableStateOf(…)` alike. */
    private val MUTABLE_FACTORY = Regex("""^[Mm]utable\w*\s*\(""")

    /** Calls that return their receiver, so `_state.also { }` is still `_state`. */
    private val IDENTITY_CALLS = setOf("also", "apply")
    private val ALIAS_HEAD = Regex("""^(?:this\s*\.\s*)?(\w+)""")
    private val WORD = Regex("""\w+""")

    override fun check(tree: SourceTree): RuleResult {
        val classes = tree.scope(SourceSetKind.PRODUCTION).classes(includeNested = false)
        val viewModels = viewModelNames(classes)
        val subjects = classes.filter { it.name in viewModels || it.name.endsWith(VIEW_MODEL) }
            .map { tree.fileFor(it.containingFile) to it }
        val violations = subjects.groupBy({ it.first }, { it.second }).mapNotNull { (file, classes) ->
            val problems = classes.flatMap { problems(it, it.name in viewModels, file.module.path) }
            if (problems.isEmpty()) null else Violation(file.path, null, problems.joinToString("; "))
        }
        return RuleResult(subjects.size, violations)
    }

    /**
     * Names of the scanned classes that reach `ViewModel` through scanned base classes, matched by simple name.
     * Not Konsist `parents(indirectParents = true)`: that resolves through every Kotlin file under the project
     * root, scanned or not, so a fixture or test class could decide a production subject.
     */
    private fun viewModelNames(classes: List<KoClassDeclaration>): Set<String> {
        val parents = classes.groupBy({ it.name }, { cls -> cls.parents().map { it.name.substringBefore('<').trim() } })
            .mapValues { (_, lists) -> lists.flatten().toSet() }
        val known = mutableSetOf(VIEW_MODEL)
        do {
            val grew = parents.filter { (_, ps) -> ps.any { it in known } }.keys.any(known::add)
        } while (grew)
        return known - VIEW_MODEL
    }

    private fun problems(cls: KoClassDeclaration, extends: Boolean, modulePath: String) = buildList {
        val named = cls.name.endsWith(VIEW_MODEL)
        if (extends && !named) add("${cls.name} extends ViewModel but is not named *ViewModel")
        if (named && !extends) add("${cls.name} is named *ViewModel but does not extend ViewModel")
        if (!IMPL_MODULE.matches(modulePath)) add("${cls.name} lives in $modulePath, not :feature:<x>:impl")
        // Konsist lists primary-constructor `val`/`var` parameters among the class properties.
        val properties = cls.properties(includeNested = false)
        val mutableNames = properties.filter { it.isVar || isMutableByShape(it) }.map { it.name }.toSet()
        properties.filter { exposesMutableState(it, mutableNames) }.forEach {
            add("${cls.name}.${it.name} is public mutable state: expose a read-only type, keep the Mutable* private")
        }
    }

    private fun exposesMutableState(property: KoPropertyDeclaration, mutableNames: Set<String>): Boolean {
        if (property.hasPrivateModifier) return false
        if (property.isVar || isMutableByShape(property)) return true
        // `val state = _state`, `val state get() = _state` and `val state = _state.also { }` hand out the target.
        if (declaredType(property) != null) return false
        return listOfNotNull(property.value, getterExpression(property)).any { aliasTarget(it) in mutableNames }
    }

    private fun isMutableByShape(property: KoPropertyDeclaration): Boolean {
        val declared = declaredType(property)
        if (declared != null) return declared.startsWith("Mutable")
        return MUTABLE_FACTORY.containsMatchIn(property.value.orEmpty().trim())
    }

    /** The type's simple name without arguments or `?`, so `kotlinx.coroutines.flow.MutableStateFlow<T>?` reads `MutableStateFlow`. */
    private fun declaredType(property: KoPropertyDeclaration): String? = property.type?.bareSourceType

    private fun getterExpression(property: KoPropertyDeclaration): String? =
        property.getter?.takeIf { it.hasExpressionBody }?.body

    /** The property an expression is just a name of, through `this.` and receiver-returning calls; null otherwise. */
    private fun aliasTarget(expression: String): String? {
        val text = CommentStripper.code(expression).trim()
        val head = ALIAS_HEAD.find(text) ?: return null
        var i = Scan.skipSpace(text, head.range.last + 1)
        while (i < text.length) {
            i = when {
                text.startsWith("?.", i) -> i + 2
                text[i] == '.' -> i + 1
                else -> return null
            }
            i = Scan.skipSpace(text, i)
            val call = WORD.find(text, i)?.takeIf { it.range.first == i && it.value in IDENTITY_CALLS } ?: return null
            i = Scan.skipSpace(text, call.range.last + 1)
            val start = i
            if (text.getOrNull(i) == '(') i = Scan.skipSpace(text, Scan.closing(text, i) ?: return null)
            if (text.getOrNull(i) == '{') i = Scan.skipSpace(text, Scan.closing(text, i) ?: return null)
            if (i == start) return null
        }
        return head.groupValues[1]
    }
}
