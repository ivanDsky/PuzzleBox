package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object P12LinkedInPostOnlyServerRequest : Rule {
    override val id = "P12"

    private const val MODULE = ":core:network"
    private const val PACKAGE = "com.ivandsky.puzzlebox.network.linkedin"
    private const val POST_API_FILE = "LinkedInPostApi.kt"
    private const val POST_HINT = "$POST_API_FILE has exactly one non-GET annotation: @POST(\"flagship-web/rsc-action/actions/server-request\")"
    private const val HAND_BUILT_HINT = "no hand-built write (.post/.put/.patch/.delete/.method); the post API is the one write"

    // Read from strippedText: the path literal must be visible, comments must not count.
    private val nonGet = Regex("""@(?:retrofit2\.http\.)?(?:POST|PUT|PATCH|DELETE|HEAD|OPTIONS|HTTP)\b""")
    private val serverRequestPost =
        Regex("""@(?:retrofit2\.http\.)?POST\(\s*"flagship-web/rsc-action/actions/server-request"\s*\)""")
    private val handBuilt = Regex("""\.(?:post|put|patch|delete|method)\s*\(""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filter {
            it.module.path == MODULE && it.packageName == PACKAGE
        }
        val violations = files.mapNotNull { file ->
            val text = file.strippedText
            val hints = buildList {
                if (file.path.substringAfterLast('/') == POST_API_FILE && !onlyServerRequestPost(text)) add(POST_HINT)
                if (handBuilt.containsMatchIn(text)) add(HAND_BUILT_HINT)
            }
            if (hints.isEmpty()) null else Violation(file.path, null, hints.joinToString("; "))
        }
        return RuleResult(files.size, violations)
    }

    private fun onlyServerRequestPost(text: String): Boolean {
        val annotations = nonGet.findAll(text).toList()
        return annotations.size == 1 && serverRequestPost.matchesAt(text, annotations.single().range.first)
    }
}
