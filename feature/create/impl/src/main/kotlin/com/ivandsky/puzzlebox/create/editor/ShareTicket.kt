package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import android.content.Intent
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * A published draft's share [code] with what the ticket shows beside it: the [title], the
 * [author] (blank reads as no byline), the author's test time [authorTimeMs], the [game]'s
 * name and its [accent]. [unpublished] once the author took the code down; [fresh] while the
 * code is the one just published here, which is the only time its ticket stamps in.
 */
internal data class ShareTicket(
    val code: ShareCode,
    val title: String,
    val author: String,
    val authorTimeMs: Long?,
    val game: String,
    val accent: AccentSlot,
    val unpublished: Boolean = false,
    val fresh: Boolean = false,
) {
    /** What Share sends: the title and byline, the link, and the code to type in by hand. */
    val shareText: String
        get() = buildString {
            append(title).append(" — a ").append(game).append(" puzzle")
            if (author.isNotBlank()) append(" by ").append(author)
            append("\nPlay it: ").append(code.link)
            append("\nor enter code ").append(code.display).append(" in PuzzleBox")
        }
}

/** Opens the system chooser with [ticket]'s [ShareTicket.shareText]. */
internal fun Context.share(ticket: ShareTicket) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, ticket.title)
        .putExtra(Intent.EXTRA_TEXT, ticket.shareText)
    startActivity(Intent.createChooser(send, null))
}
