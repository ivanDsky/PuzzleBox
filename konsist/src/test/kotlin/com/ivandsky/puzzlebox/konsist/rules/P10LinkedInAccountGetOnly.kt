package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object P10LinkedInAccountGetOnly : Rule {
    override val id = "P10"

    private const val MODULE = ":core:network"
    private const val PACKAGE = "com.ivandsky.puzzlebox.network.linkedin"
    private const val METHOD_HINT = "the account API is GET-only"
    // The one file exempt from the non-GET check: P12 pins its single POST (ADR-0032). Its client-leak checks stay on.
    private const val POST_API_FILE = "LinkedInPostApi.kt"
    private const val CLIENT_HINT = "no logging interceptor and no cookie jar on the account client"

    // Both the annotation and its import are matched, so a fully qualified use without an import is caught too.
    private val nonGet = Regex("""(?:@|retrofit2\.http\.)(?:POST|PUT|PATCH|DELETE|HEAD|OPTIONS|HTTP)\b""")

    // The account client carries a session cookie; a logger or a jar would write or re-send it.
    private val clientLeaks = listOf(
        Regex("""\bHttpLoggingInterceptor\b"""),
        Regex("""\bokhttp3\.logging\b"""),
        Regex("""\.cookieJar\s*\("""),
        Regex("""\bCookieJar\b"""),
    )

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filter {
            it.module.path == MODULE && it.packageName == PACKAGE
        }
        val violations = files.mapNotNull { file ->
            val code = file.codeText
            val hints = buildList {
                if (file.path.substringAfterLast('/') != POST_API_FILE && nonGet.containsMatchIn(code)) add(METHOD_HINT)
                if (clientLeaks.any { it.containsMatchIn(code) }) add(CLIENT_HINT)
            }
            if (hints.isEmpty()) null else Violation(file.path, null, hints.joinToString("; "))
        }
        return RuleResult(files.size, violations)
    }
}
