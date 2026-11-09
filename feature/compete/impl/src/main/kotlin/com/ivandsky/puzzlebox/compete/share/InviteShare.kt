package com.ivandsky.puzzlebox.compete.share

import android.content.Context
import android.content.Intent
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The inviter's name when it is not known: the same fallback the invited rows use. */
internal const val UNKNOWN_INVITER = "A friend"

/**
 * The text a friend receives: the challenge line ("<inviter> challenges you: best of <n> — <games> · ends <day time>"),
 * the match link, then the code. The games clause is left out when [games] is null or empty.
 */
internal fun inviteText(
    inviter: String,
    bestOf: Int,
    games: List<String>?,
    endsAt: Instant,
    zone: ZoneId,
    locale: Locale,
    code: ShareCode,
): String {
    val ends = DateTimeFormatter.ofPattern("EEE HH:mm", locale).format(endsAt.atZone(zone))
    val gamesClause = games?.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { " — $it" }.orEmpty()
    val challenge = "$inviter challenges you: best of $bestOf$gamesClause · ends $ends"
    return listOf(challenge, code.matchLink, "Code ${code.display}").joinToString("\n")
}

/** The invite for a duel created just now, which ends [deadlineHours] from now, in the device zone and locale. */
internal fun createdInviteText(
    inviter: String?,
    bestOf: Int,
    games: List<String>,
    deadlineHours: Int,
    code: ShareCode,
): String = inviteText(
    inviter = inviter ?: UNKNOWN_INVITER,
    bestOf = bestOf,
    games = games,
    endsAt = Instant.now().plus(Duration.ofHours(deadlineHours.toLong())),
    zone = ZoneId.systemDefault(),
    locale = Locale.getDefault(),
    code = code,
)

/** Sends [text] through the system chooser as plain text. */
internal fun Context.shareInvite(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, null))
}
