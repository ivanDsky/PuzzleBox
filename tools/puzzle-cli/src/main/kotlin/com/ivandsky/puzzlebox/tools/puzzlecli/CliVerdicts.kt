package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.NotationParse
import com.ivandsky.puzzlebox.game.editor.NotationText
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.game.editor.capability

/** Maps a [NotationParse] (+ the analysis it runs under [budget]) to the JSON shape (§9.2). */
object CliVerdicts {

    fun of(parse: NotationParse, budget: Budget, withSolution: Boolean): CliVerdict = when (parse) {
        is NotationParse.Parsed -> parsed(parse, budget, withSolution)
        is NotationParse.TooLarge -> CliVerdict(
            game = "",
            verdict = "malformed",
            difficulty = null,
            needsGuessing = null,
            differingCells = emptyList(),
            conflicts = emptyList(),
            blocks = emptyList(),
            errors = listOf(
                CliError(parse.line, parse.col, "text is ${parse.bytes} bytes; the limit is ${NotationText.MAX_BYTES}"),
            ),
        )
        is NotationParse.UnknownGame -> CliVerdict(
            game = parse.gameId,
            verdict = "malformed",
            difficulty = null,
            needsGuessing = null,
            differingCells = emptyList(),
            conflicts = emptyList(),
            blocks = emptyList(),
            errors = listOf(CliError(1, 1, "puzzlebox ${parse.gameId} has no notation in this build")),
        )
        is NotationParse.UnsupportedVersion -> CliVerdict(
            game = parse.gameId,
            verdict = "malformed",
            difficulty = null,
            needsGuessing = null,
            differingCells = emptyList(),
            conflicts = emptyList(),
            blocks = emptyList(),
            errors = listOf(
                CliError(1, 1, "puzzlebox ${parse.gameId} v${parse.found} is unsupported; the highest this build reads is v${parse.maxKnown}"),
            ),
        )
        is NotationParse.Malformed -> CliVerdict(
            game = "",
            verdict = "malformed",
            difficulty = null,
            needsGuessing = null,
            differingCells = emptyList(),
            conflicts = emptyList(),
            blocks = emptyList(),
            errors = listOf(CliError(parse.line, parse.col, parse.message)),
        )
    }

    private fun parsed(parse: NotationParse.Parsed, budget: Budget, withSolution: Boolean): CliVerdict {
        val draft = parse.draft
        val verdict = draft.analyze(budget)
        val base = when (verdict) {
            is Verdict.Unique -> CliVerdict(
                game = draft.gameId.value,
                verdict = "unique",
                difficulty = verdict.difficulty?.level?.name?.lowercase(),
                needsGuessing = verdict.needsGuessing,
                differingCells = emptyList(),
                conflicts = emptyList(),
                blocks = draft.publishBlocks(verdict).map { it.id },
                errors = emptyList(),
                solution = if (withSolution) verdict.solution.values else null,
            )
            is Verdict.Multiple -> CliVerdict(
                game = draft.gameId.value,
                verdict = "multiple",
                difficulty = null,
                needsGuessing = null,
                differingCells = cells(draft, verdict.differingCells),
                conflicts = emptyList(),
                blocks = draft.publishBlocks(verdict).map { it.id },
                errors = emptyList(),
            )
            is Verdict.None -> CliVerdict(
                game = draft.gameId.value,
                verdict = "none",
                difficulty = null,
                needsGuessing = null,
                differingCells = emptyList(),
                conflicts = cells(draft, verdict.conflicts),
                blocks = draft.publishBlocks(verdict).map { it.id },
                errors = emptyList(),
            )
            is Verdict.Incomplete -> simple(draft, "incomplete", verdict)
            // analyze never yields Checking; the UI pipeline (00 §2.4) is its only producer.
            Verdict.Checking, Verdict.Undetermined -> simple(draft, "undetermined", verdict)
        }
        // withSolution carries the M5-04 `solve` payload; `validate` leaves solution null.
        return base
    }

    private fun simple(draft: EditorDraft<*>, verdict: String, analysis: Verdict) =
        CliVerdict(
            game = draft.gameId.value,
            verdict = verdict,
            difficulty = null,
            needsGuessing = null,
            differingCells = emptyList(),
            conflicts = emptyList(),
            blocks = draft.publishBlocks(analysis).map { it.id },
            errors = emptyList(),
        )

    /** Cell indices → sorted 1-based [row, col] pairs through [EditorNotation.columns]. */
    private fun cells(draft: EditorDraft<*>, indices: Set<Int>): List<List<Int>> {
        val board = draft.state.draft ?: return emptyList()
        return cellPairs(draft.editor, board, indices)
    }
}

private fun cellPairs(editor: GameEditor<*>, draft: Any, indices: Set<Int>): List<List<Int>> {
    // C is spelled over Any: the star-projected editor has no nameable D.
    val columns = editor.capability<_, EditorNotation<Any>>()?.columns(draft) ?: 1
    return indices.sorted().map { listOf(it / columns + 1, it % columns + 1) }
}
