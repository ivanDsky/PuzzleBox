package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
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
        val scope = tree.scope(SourceSetKind.PRODUCTION)
        val parentsByClass = scope.classes(includeNested = false)
            .groupBy({ it.name }, { cls -> cls.parents().map { it.name.substringBefore('<').trim() } })
            .mapValues { (_, lists) -> lists.flatten().toSet() }
        val subjects = scope.files.toList().flatMap { ko ->
            ko.classes(includeNested = false).filter { isSubject(it, parentsByClass) }.map { tree.fileFor(ko) to it }
        }
        val violations = subjects.groupBy({ it.first }, { it.second }).mapNotNull { (file, classes) ->
            val problems = classes.flatMap { problems(it, file.module.path, parentsByClass) }
            if (problems.isEmpty()) null else Violation(file.path, null, problems.joinToString("; "))
        }
        return RuleResult(subjects.size, violations)
    }

    private fun isSubject(cls: KoClassDeclaration, parentsByClass: Map<String, Set<String>>) =
        extendsViewModel(cls.name, parentsByClass) || cls.name.endsWith(VIEW_MODEL)

    /** Through project classes by simple name, so `class X : BaseViewModel()` extends ViewModel when the base does. */
    private fun extendsViewModel(name: String, parentsByClass: Map<String, Set<String>>): Boolean {
        val seen = mutableSetOf(name)
        var frontier = parentsByClass[name].orEmpty()
        while (frontier.isNotEmpty()) {
            if (VIEW_MODEL in frontier) return true
            frontier = frontier.filter(seen::add).flatMap { parentsByClass[it].orEmpty() }.toSet()
        }
        return false
    }

    private fun problems(cls: KoClassDeclaration, modulePath: String, parentsByClass: Map<String, Set<String>>) = buildList {
        val extends = extendsViewModel(cls.name, parentsByClass)
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

    private fun declaredType(property: KoPropertyDeclaration): String? = property.type?.text?.trim()

    private fun getterExpression(property: KoPropertyDeclaration): String? {
        val getter = property.getter ?: return null
        return if (getter.hasExpressionBody) getter.text.substringAfter('=').trim() else null
    }

    /** The property an expression is just a name of, through `this.` and receiver-returning calls; null otherwise. */
    private fun aliasTarget(expression: String): String? {
        val text = expression.trim()
        val head = ALIAS_HEAD.find(text) ?: return null
        var i = skipSpace(text, head.range.last + 1)
        while (i < text.length) {
            i = when {
                text.startsWith("?.", i) -> i + 2
                text[i] == '.' -> i + 1
                else -> return null
            }
            i = skipSpace(text, i)
            val call = WORD.find(text, i)?.takeIf { it.range.first == i && it.value in IDENTITY_CALLS } ?: return null
            i = skipSpace(text, call.range.last + 1)
            val start = i
            if (text.getOrNull(i) == '(') i = skipSpace(text, closing(text, i) ?: return null)
            if (text.getOrNull(i) == '{') i = skipSpace(text, closing(text, i) ?: return null)
            if (i == start) return null
        }
        return head.groupValues[1]
    }

    private fun skipSpace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    /** Index after the bracket matching the one at [open]; brackets inside string literals are not counted. */
    private fun closing(text: String, open: Int): Int? {
        var depth = 0
        var inString = false
        var i = open
        while (i < text.length) {
            val c = text[i]
            when {
                inString && c == '\\' -> i++
                c == '"' -> inString = !inString
                inString -> Unit
                c == '(' || c == '{' || c == '[' -> depth++
                c == ')' || c == '}' || c == ']' -> if (--depth == 0) return i + 1
            }
            i++
        }
        return null
    }
}
