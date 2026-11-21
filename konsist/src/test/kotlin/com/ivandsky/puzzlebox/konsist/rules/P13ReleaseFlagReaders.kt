package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceFile
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.lemonappdev.konsist.api.declaration.KoEnumConstantDeclaration

/**
 * ADR-0022 §4: a shipped release flag entry gates something, so an entry no code reads is deleted, not kept. An entry
 * still dark (`releaseDefault = false`) may land before its first gate, as ticket 01 of a trunk feature does. Flag
 * enums are what [FlagEnums] matches, so the rule follows entries that move to an owner enum such as `InsightsFlag`.
 */
object P13ReleaseFlagReaders : Rule {
    override val id = "P13"

    /** Test-only support: its main sources feed tests, so a read there gates nothing in a release build. */
    private const val TESTING_MODULE = ":core:testing"

    /** One import line: the imported path, a wildcard marker and an alias. */
    private val importLine = Regex("""(?m)^[ \t]*import[ \t]+([\w.]+)(\.\*)?(?:[ \t]+as[ \t]+(\w+))?[ \t]*;?[ \t]*$""")

    override fun check(tree: SourceTree): RuleResult {
        val flags = flagEnums(tree)
        val violations = flags.groupBy { it.path }.mapNotNull { (path, inFile) ->
            val unread = inFile.flatMap { it.unread }
            if (unread.isEmpty()) null else Violation(path, null, hint(unread))
        }
        return RuleResult(flags.size, violations)
    }

    /** Every entry that needs a reader and has none, as `<Enum>.<Entry>`. */
    internal fun unread(tree: SourceTree): List<String> = flagEnums(tree).flatMap { it.unread }

    /** One flag enum, the rule's subject: its declaring file and its unread entries as `<Enum>.<Entry>`. */
    private class FlagEnum(val path: String, val unread: List<String>)

    private fun flagEnums(tree: SourceTree): List<FlagEnum> {
        val production = tree.files(SourceSetKind.PRODUCTION)
        val readers = production.filterNot { it.sourceSet.startsWith("debug") || it.module.path == TESTING_MODULE }
        return production.flatMap { file ->
            file.ko.classes().filter { FlagEnums.isFlagEnum(file, it) }.map { enum ->
                val constants = enum.enumConstants
                val names = constants.mapTo(mutableSetOf()) { it.name }
                // Null only for a local enum, which nothing outside its function can import.
                val fqName = enum.fullyQualifiedName ?: enum.name
                val read = readers
                    .filter { it.path != file.path && enum.name in it.codeText }
                    .flatMapTo(mutableSetOf()) { readsIn(it, enum.name, fqName, names) }
                val unread = constants.filter { it.name !in read && !it.isDark() }.map { "${enum.name}.${it.name}" }
                FlagEnum(file.path, unread)
            }
        }
    }

    /** Only an explicit named `releaseDefault = false` marks an entry dark; anything else needs a reader. */
    private fun KoEnumConstantDeclaration.isDark(): Boolean =
        arguments.any { it.name == "releaseDefault" && it.value?.trim() == "false" }

    /**
     * The entries of [enumName] this file names: `<Enum>.<Entry>` (plain, fully qualified, or through an alias of the
     * enum), or a bare `<Entry>` or alias after an import of the entry or of `<Enum>.*`.
     */
    private fun readsIn(file: SourceFile, enumName: String, fqName: String, entries: Set<String>): Set<String> {
        val qualifiers = mutableSetOf(enumName)
        val bare = mutableMapOf<String, String>()
        importLine.findAll(file.codeText).forEach { match ->
            val (path, wildcard, alias) = match.destructured
            when {
                path == fqName && wildcard.isNotEmpty() -> entries.forEach { bare[it] = it }
                path == fqName && alias.isNotEmpty() -> qualifiers += alias
                path.startsWith("$fqName.") && wildcard.isEmpty() -> {
                    val entry = path.removePrefix("$fqName.")
                    if (entry in entries) bare[alias.ifEmpty { entry }] = entry
                }
            }
        }
        val body = importLine.replace(file.codeText, "")
        val qualified = qualifiers.flatMap { qualifier ->
            Regex("""\b${Regex.escape(qualifier)}\s*\.\s*(\w+)""").findAll(body).map { it.groupValues[1] }.toList()
        }
        val named = bare.filterKeys { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(body) }.values
        return (qualified + named).filterTo(mutableSetOf()) { it in entries }
    }

    private fun hint(unread: List<String>): String =
        "no reader outside the declaring file for ${unread.joinToString()}: delete the entry with its last gate, gate " +
            "the feature's entry point on <Enum>.<Entry> before releaseDefault turns true, or name it " +
            "releaseDefault = false while it is still dark (ADR-0022 §4)"
}
