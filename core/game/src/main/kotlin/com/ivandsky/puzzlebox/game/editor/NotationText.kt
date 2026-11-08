package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId

/**
 * The whole-text entry both import callers share (ADR-0021 §5: Create's import use case and
 * the CLI): one [NotationParse] for every way text can fail, positions whole-text and 1-based.
 */
object NotationText {
    /** UTF-8 byte cap, checked before any parsing (spec A23). */
    const val MAX_BYTES: Int = 64 * 1024

    /**
     * Cap → header → registry[GameId(header.gameId)]?.editor as? EditorNotation → version check →
     * parse with the header and comment lines blanked → EditorDraft(editor, DraftState(draft)).
     * Pure and blocking; callers choose the thread.
     */
    fun parse(text: String, registry: GameRegistry): NotationParse {
        val bytes = text.toByteArray(Charsets.UTF_8).size
        if (bytes > MAX_BYTES) {
            val (line, col) = firstPastCap(text)
            return NotationParse.TooLarge(bytes, line, col)
        }
        val headerLine = firstContentLine(text)
        val header = NotationHeader.find(text)
            ?: return NotationParse.Malformed(
                line = if (headerLine == 0) 1 else headerLine,
                col = 1,
                message = if (headerLine == 0) "missing header" else "garbled header",
            )
        val editor = registry[GameId(header.gameId)]?.editor
            ?: return NotationParse.UnknownGame(header.gameId)
        return parseGame(editor, header, headerLine, blanked(text, headerLine))
    }

    /**
     * The cast is erased: a game's editor implements the capability over its own [D], the same
     * contract [SharedOpen.verifyTyped] relies on.
     */
    private fun <D : Any> parseGame(
        editor: GameEditor<D>,
        header: NotationHeader,
        headerLine: Int,
        blanked: String,
    ): NotationParse {
        val notation = editor as? EditorNotation<D>
            ?: return NotationParse.UnknownGame(header.gameId)
        if (header.version > notation.notationVersion) {
            return NotationParse.UnsupportedVersion(
                header.gameId,
                header.version,
                notation.notationVersion,
            )
        }
        val draft = when (val parsed = notation.parse(blanked)) {
            is NotationResult.Ok -> parsed.draft
            is NotationResult.Err ->
                return NotationParse.Malformed(parsed.line, parsed.col, parsed.message)
        }
        if (editor.variantOf(draft) !in editor.variants) {
            return NotationParse.Malformed(headerLine + 1, 1, "unsupported size")
        }
        return NotationParse.Parsed(EditorDraft(editor, DraftState(draft)))
    }

    /** 1-based number of the first non-blank, non-`#` line; 0 when the text has no such line. */
    private fun firstContentLine(text: String): Int =
        text.lineSequence().indexOfFirst { it.isNotBlank() && !it.trimStart().startsWith("#") } + 1

    /**
     * The header line and every `#` comment line replaced by "": line and column positions in
     * what the game parser sees stay whole-text.
     */
    private fun blanked(text: String, headerLine: Int): String =
        text.split('\n').mapIndexed { index, line ->
            if (index + 1 == headerLine || line.trimStart().startsWith("#")) "" else line
        }.joinToString("\n")

    /**
     * 1-based (line, column) of the first character whose UTF-8 encoding ends past [MAX_BYTES]
     * (spec success criterion 14). The caller guarantees the text is over the cap.
     */
    private fun firstPastCap(text: String): Pair<Int, Int> {
        var consumed = 0
        var line = 1
        var col = 1
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            consumed += utf8Size(codePoint)
            if (consumed > MAX_BYTES) return line to col
            if (codePoint == '\n'.code) {
                line += 1
                col = 1
            } else {
                col += 1
            }
            index += Character.charCount(codePoint)
        }
        throw IllegalStateException("text is within the cap")
    }

    private fun utf8Size(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }
}
