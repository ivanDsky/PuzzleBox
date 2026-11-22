package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.NotationParse
import com.ivandsky.puzzlebox.game.editor.NotationText
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.game.editor.encodeShare
import com.ivandsky.puzzlebox.game.editor.shareFormatVersion
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * `share <file>`: the publish path's board half, for a seeding script. A board with exactly one solution and no publish block
 * prints one [ShareLine] built by the same codec the app publishes with; anything else prints nothing and exits like `validate`.
 */
internal class ShareCommand(
    private val read: (path: String) -> String,
    private val out: (String) -> Unit,
    private val err: (String) -> Unit,
) {
    fun run(args: List<String>): Int {
        val path = args.singleOrNull() ?: return usage()
        val text = try {
            read(path)
        } catch (failure: Exception) {
            err("cannot read $path: ${failure.message}")
            return CliExit.USAGE
        }
        val parsed = NotationText.parse(text, PuzzleCli.registry) as? NotationParse.Parsed
            ?: return CliExit.INVALID_TEXT.also { err("$path is not valid puzzle text; run validate for the errors") }
        val draft = parsed.draft
        val verdict = draft.analyze(Budget.within(PuzzleCli.BUDGET_MS, System::nanoTime))
        if (verdict !is Verdict.Unique || draft.publishBlocks(verdict).isNotEmpty()) {
            err("$path is not publishable (${draft.gameId.value}); run validate for the verdict")
            return CliExit.NOT_PUBLISHABLE
        }
        val line = line(draft, verdict) ?: return CliExit.NOT_PUBLISHABLE.also { err("${draft.gameId.value} cannot be shared") }
        out(Json.encodeToString(ShareLine.serializer(), line))
        return CliExit.PUBLISHABLE
    }

    private fun line(draft: EditorDraft<*>, verdict: Verdict.Unique): ShareLine? {
        val payload = draft.encodeShare() ?: return null
        return ShareLine(
            game = draft.gameId.value,
            variant = draft.variant.value,
            shareCodecVersion = checkNotNull(draft.shareFormatVersion),
            payload = Base64.getEncoder().encodeToString(payload),
            // The label the app shows: none when the solve needs guessing (CreateSharedPuzzle.shownDifficulty).
            difficulty = verdict.takeUnless { it.needsGuessing }?.difficulty?.level?.name?.lowercase(),
        )
    }

    private fun usage(): Int {
        err("usage: puzzle-cli share <file>")
        return CliExit.USAGE
    }
}
