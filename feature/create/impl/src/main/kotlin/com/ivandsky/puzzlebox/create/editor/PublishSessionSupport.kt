package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.PublishBlock

/** The block shown once the store answered its daily cap. */
internal val DailyCapBlock = PublishBlock(PublishBlock.DAILY_CAP)

/** This string trimmed and cut to at most [max] code points, never splitting a character. */
internal fun String.clampedTo(max: Int): String {
    val trimmed = trim()
    return if (trimmed.codePointCount(0, trimmed.length) <= max) trimmed else trimmed.substring(0, trimmed.offsetByCodePoints(0, max))
}
