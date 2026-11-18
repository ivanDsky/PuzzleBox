package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object P12LinkedInPostPinnedWrites : Rule {
    override val id = "P12"

    private const val MODULE = ":core:network"
    private const val PACKAGE = "com.ivandsky.puzzlebox.network.linkedin"
    private const val POST_API_FILE = "LinkedInPostApi.kt"
    private const val SERVER_REQUEST_PATH = "flagship-web/rsc-action/actions/server-request"
    private const val VOYAGER_SAVE_PATH = "voyager/api/graphql"
    private const val POST_HINT =
        "$POST_API_FILE has exactly two non-GET annotations: @POST(\"$SERVER_REQUEST_PATH\") and @POST(\"$VOYAGER_SAVE_PATH\")"
    private const val HAND_BUILT_HINT = "no hand-built write (.post/.put/.patch/.delete/.method); the post API holds the only writes"

    /** The two writes of ADR-0032: the `updateGameState` server request and the voyager save (§11). Each once. */
    private val pinnedPaths = setOf(SERVER_REQUEST_PATH, VOYAGER_SAVE_PATH)

    // Read from strippedText: the path literal must be visible, comments must not count.
    private val nonGet = Regex("""@(?:retrofit2\.http\.)?(?:POST|PUT|PATCH|DELETE|HEAD|OPTIONS|HTTP)\b""")
    private val post = Regex("""@(?:retrofit2\.http\.)?POST\(\s*"([^"]*)"\s*\)""")
    private val handBuilt = Regex("""\.(?:post|put|patch|delete|method)\s*\(""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filter {
            it.module.path == MODULE && it.packageName == PACKAGE
        }
        val violations = files.mapNotNull { file ->
            val text = file.strippedText
            val hints = buildList {
                if (file.path.substringAfterLast('/') == POST_API_FILE && !onlyPinnedPosts(text)) add(POST_HINT)
                if (handBuilt.containsMatchIn(text)) add(HAND_BUILT_HINT)
            }
            if (hints.isEmpty()) null else Violation(file.path, null, hints.joinToString("; "))
        }
        return RuleResult(files.size, violations)
    }

    /** Every non-GET annotation is a POST of a pinned path, and each pinned path is posted exactly once. */
    private fun onlyPinnedPosts(text: String): Boolean {
        val paths = nonGet.findAll(text).map { post.matchAt(text, it.range.first)?.groupValues?.get(1) }.toList()
        return paths.size == pinnedPaths.size && paths.toSet() == pinnedPaths
    }
}
