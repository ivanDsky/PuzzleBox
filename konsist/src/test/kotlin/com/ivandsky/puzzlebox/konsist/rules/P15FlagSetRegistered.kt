package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceFile
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

/**
 * ADR-0022 amendment 2026-11-18: release flags belong to their owner. Every flag enum ([FlagEnums]) is registered with
 * `flagSet("<owner>", <Enum>.entries)` in a Koin module of its own module or `:app`, so `DeclaredFlags` sees it; no two
 * registrations share an owner; the central `ReleaseFlag` takes no new entry, and [FROZEN] only shrinks with it.
 */
object P15FlagSetRegistered : Rule {
    override val id = "P15"

    /**
     * The central entries on the day it froze. It only shrinks: a name the enum no longer has is stale, and an entry
     * that is not here is added.
     */
    private val FROZEN = setOf("ResultHeroPolish", "HintPolish", "RanksFreshness", "ZipDotPulse", "SheetScreens")

    /** The one module that may register another module's flags: the app wires a module with no Koin (core:game-ui). */
    private const val APP = ":app"
    private const val MAIN = "main"
    private const val NAME = "flagSet"
    private const val DI = "di"

    /** A call or a declaration; read on `codeText`, so a comment or a string never counts. */
    private val callStart = Regex("""\bflagSet\s*\(""")
    private val declaration = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?flagSet\b""")

    /** The one accepted call shape, read on `strippedText` from the call's start: a literal owner, an enum's entries. */
    private val shape = Regex("""flagSet\s*\(\s*(?:owner\s*=\s*)?"([^"\\$]*)"\s*,\s*(?:flags\s*=\s*)?([\w.]+)\s*\.\s*entries\s*,?\s*\)""")

    private const val SHAPE_HINT = "flagSet takes a string literal owner and an enum's entries: flagSet(\"<owner>\", <Owner>Flag.entries)"

    /** One `flagSet` call; [owner] and [ref] are null when it does not have the accepted shape. */
    private class Call(val file: SourceFile, val owner: String?, val ref: String?)

    override fun check(tree: SourceTree): RuleResult {
        val production = tree.files(SourceSetKind.PRODUCTION)
        val calls = production.flatMap(::callsIn)
        val hints = mutableListOf<Pair<String, String>>()

        calls.filter { it.owner == null }.forEach { hints += it.file.path to SHAPE_HINT }

        calls.filter { it.owner != null }.groupBy { it.owner }.filterValues { it.size > 1 }.forEach { (owner, repeated) ->
            repeated.forEach { hints += it.file.path to "owner \"$owner\" is registered ${repeated.size} times; owners are unique" }
        }

        var enums = 0
        production.forEach { file ->
            file.ko.classes().filter { FlagEnums.isFlagEnum(file, it) }.forEach { enum ->
                enums++
                // Null only for a local enum, which nothing outside its function can name.
                val fqName = enum.fullyQualifiedName ?: enum.name
                val registered = calls.any { call ->
                    val ref = call.ref ?: return@any false
                    (ref == enum.name || ref == fqName || fqName.endsWith(".$ref")) && call.registers(file)
                }
                if (!registered) {
                    hints += file.path to "${enum.name} is not registered: add flagSet(\"<owner>\", ${enum.name}.entries) " +
                        "to a Koin module in a `di` package of ${file.module.path}'s main sources (or :app's)"
                }
                if (FlagEnums.isCentral(file, enum)) {
                    val names = enum.enumConstants.map { it.name }
                    val added = names.filterNot { it in FROZEN }
                    if (added.isNotEmpty()) {
                        hints += file.path to "ReleaseFlag is frozen: move ${added.joinToString()} to the owner module's flag enum"
                    }
                    val stale = FROZEN.filterNot { it in names }
                    if (stale.isNotEmpty()) {
                        hints += file.path to "P15's FROZEN still lists ${stale.joinToString()}, gone from ReleaseFlag: " +
                            "delete it from FROZEN in P15FlagSetRegistered.kt"
                    }
                }
            }
        }

        val violations = hints.groupBy({ it.first }, { it.second }).map { (path, inFile) ->
            Violation(path, null, inFile.distinct().joinToString("; ") + " (ADR-0022 amendment 2026-11-18)")
        }
        return RuleResult(enums + calls.size, violations)
    }

    /** Only a call the app always loads counts: main sources, a `di` package, the enum's own module or `:app`. */
    private fun Call.registers(enumFile: SourceFile): Boolean =
        file.sourceSet == MAIN &&
            file.packageName.substringAfterLast('.') == DI &&
            (file.module.path == enumFile.module.path || file.module.path == APP)

    private fun callsIn(file: SourceFile): List<Call> {
        // Where each `fun … flagSet` declaration's name starts: that `flagSet(` is no call.
        val declared = declaration.findAll(file.codeText).mapTo(mutableSetOf()) { it.range.last + 1 - NAME.length }
        return callStart.findAll(file.codeText)
            .filterNot { it.range.first in declared }
            .map { start ->
                val match = shape.find(file.strippedText, start.range.first)?.takeIf { it.range.first == start.range.first }
                Call(file, match?.groupValues?.get(1), match?.groupValues?.get(2))
            }
            .toList()
    }
}
