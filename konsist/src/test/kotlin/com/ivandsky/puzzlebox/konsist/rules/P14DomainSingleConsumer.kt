package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceFile
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

/**
 * A `:core:domain` file whose declarations only one feature reaches belongs in that feature's `:impl` (code-quality
 * review item 7). References are found by name: a qualified name (an import or an inline use), or a simple name in a file of
 * the same package or one that star-imports it. A domain file that names another hands its consumers on to it.
 */
object P14DomainSingleConsumer : Rule {
    override val id = "P14"

    private const val DOMAIN = ":core:domain"
    private const val APP = ":app"

    private val feature = Regex("""^(:feature:[^:]+)(?::.*)?$""")
    private val identifier = Regex("""[A-Za-z_]\w*""")

    override fun check(tree: SourceTree): RuleResult {
        val domainFiles = tree.files(SourceSetKind.PRODUCTION).filter { it.module.path == DOMAIN && !isDi(it) }
        val root = domainFiles.firstOrNull()?.module?.packageRoot ?: return RuleResult(0, emptyList())
        val index = Index(root, domainFiles.associateWith(::declaredNames).filterValues { it.isNotEmpty() })

        val consumers = index.subjects.associateWith { mutableSetOf<String>() }
        for (file in consumerFiles(tree)) {
            val unit = unitOf(file.module.path)
            index.references(file).forEach { consumers.getValue(it) += unit }
        }
        closeOverDomain(domainFiles, index, consumers)

        val violations = consumers.mapNotNull { (file, units) ->
            val only = units.singleOrNull()?.takeIf { it.startsWith(":feature:") } ?: return@mapNotNull null
            Violation(
                file.path,
                null,
                "only $only uses it; move it to a `domain` package of $only:impl " +
                    "and its Koin definition to that feature's module",
            )
        }
        return RuleResult(index.subjects.size, violations)
    }

    /** Production code of every other module; tests too, except `:core:domain`'s and `:app`'s, which can follow a move. */
    private fun consumerFiles(tree: SourceTree): List<SourceFile> =
        tree.files(SourceSetKind.PRODUCTION).filter { it.module.path != DOMAIN } +
            tree.files(SourceSetKind.TEST).filter { it.module.path != DOMAIN && it.module.path != APP }

    /** A domain file that names F passes its consumers on to F, to a fixpoint; `di` files are not consumers. */
    private fun closeOverDomain(
        domainFiles: List<SourceFile>,
        index: Index,
        consumers: Map<SourceFile, MutableSet<String>>,
    ) {
        val edges = domainFiles.associateWith { index.references(it) - it }
        var changed = true
        while (changed) {
            changed = false
            for ((from, targets) in edges) {
                val units = consumers[from] ?: continue
                targets.forEach { if (consumers.getValue(it).addAll(units)) changed = true }
            }
        }
    }

    private fun unitOf(modulePath: String): String = feature.find(modulePath)?.groupValues?.get(1) ?: modulePath

    private fun isDi(file: SourceFile) = file.packageName.substringAfterLast('.') == "di"

    /** Top-level declarations another file can name: everything but `private`. */
    private fun declaredNames(file: SourceFile): Set<String> {
        val ko = file.ko
        val types = ko.classes(includeNested = false) + ko.interfaces(includeNested = false) + ko.objects(includeNested = false)
        val typeNames = types.filterNot { it.hasPrivateModifier }.map { it.name }
        val functions = ko.functions(includeNested = false, includeLocal = false)
            .filterNot { it.hasPrivateModifier }.map { it.name }
        val properties = ko.properties(includeNested = false).filterNot { it.hasPrivateModifier }.map { it.name }
        val aliases = ko.typeAliases.filterNot { it.hasPrivateModifier }.map { it.name }
        return (typeNames + functions + properties + aliases).toSet()
    }

    private class Index(root: String, names: Map<SourceFile, Set<String>>) {
        val subjects: Set<SourceFile> = names.keys

        /** Qualified name → declaring files; overloads may spread one name over several files. */
        private val byFqn: Map<String, List<SourceFile>> =
            names.flatMap { (file, declared) -> declared.map { "${file.packageName}.$it" to file } }
                .groupBy({ it.first }, { it.second })

        /** Package → simple name → declaring files. */
        private val byPackage: Map<String, Map<String, List<SourceFile>>> =
            names.entries.groupBy { it.key.packageName }.mapValues { (_, entries) ->
                entries.flatMap { (file, declared) -> declared.map { it to file } }.groupBy({ it.first }, { it.second })
            }

        private val escapedRoot = Regex.escape(root)
        private val qualified = Regex("""(?<![\w.])$escapedRoot(?:\.\w+)+""")
        private val starImport = Regex("""\bimport\s+($escapedRoot(?:\.\w+)*)\.\*""")

        fun references(file: SourceFile): Set<SourceFile> {
            val code = file.codeText
            val found = mutableSetOf<SourceFile>()
            qualified.findAll(code).forEach { match ->
                // `X.Binding`, `X.invoke`: every prefix is tried, so a nested or member reference still names X.
                var prefix = ""
                for (segment in match.value.split('.')) {
                    prefix = if (prefix.isEmpty()) segment else "$prefix.$segment"
                    byFqn[prefix]?.let(found::addAll)
                }
            }
            val packages = (starImport.findAll(code).map { it.groupValues[1] } + file.packageName)
                .filter { it in byPackage }.toSet()
            if (packages.isNotEmpty()) {
                val tokens = identifier.findAll(code).map { it.value }.toSet()
                for (pkg in packages) {
                    byPackage.getValue(pkg).forEach { (name, files) -> if (name in tokens) found += files }
                }
            }
            return found
        }
    }
}
