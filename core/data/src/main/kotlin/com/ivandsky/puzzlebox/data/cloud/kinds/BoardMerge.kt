package com.ivandsky.puzzlebox.data.cloud.kinds

import java.security.MessageDigest

/** One row of a saved board as the cloud holds it: no profile id, so nothing but what the board shows. */
internal data class BoardRow(
    val name: String,
    /** Null when the connection hides the time. */
    val timeMs: Long?,
    val hintFree: Boolean,
    val flawless: Boolean,
    /** True for the row of the profile that read the board; at most one row per board. */
    val isOwn: Boolean,
)

/** A saved board whole: the unit that merges, uploads and restores. [profileKey] is the hash of the reading profile. */
internal data class BoardValue(
    val game: String,
    val day: String,
    val profileKey: String,
    val readAtMs: Long,
    val complete: Boolean,
    val rows: List<BoardRow>,
) {
    /**
     * A hash of everything a board document says, whatever the order of [rows]. Two boards with the same fingerprint
     * are the same document, which is how an acknowledgement is matched to the content that was uploaded.
     */
    fun fingerprint(): String {
        val body = rows.sortedWith(ROW_ORDER).joinToString(";") { "${it.name}|${it.timeMs}|${it.hintFree}|${it.flawless}|${it.isOwn}" }
        return sha256Hex("$game|$day|$profileKey|$readAtMs|$complete|$body")
    }
}

/** Rows in the one order a document and a fingerprint use. */
internal val ROW_ORDER: Comparator<BoardRow> =
    compareBy<BoardRow>({ !it.isOwn }, { it.timeMs == null }, { it.timeMs }, { it.name }, { it.hintFree }, { it.flawless })

/**
 * Which saved board a (game, day) keeps (A19, A25), pure. A board merges whole, never row by row, so a connection who
 * hides their time later stays hidden. Among the boards of one profile a complete board is never replaced by a partial
 * one, otherwise the newer read wins. Among profiles the currently linked one's board comes first, else the latest read.
 */
internal object BoardMerge {

    /**
     * The board of ([game], [day]) to keep among [candidates], the local one first so that a tie keeps it. The result
     * is one of the [candidates] and does not depend on the order of any but a tied pair, whose content is the same.
     * [linkedKey] is the profile key of the linked LinkedIn profile, null when none is linked.
     */
    fun winner(candidates: List<BoardValue>, linkedKey: String?): BoardValue {
        val perProfile = candidates.groupBy { it.profileKey }.values.map { it.maxWith(SAME_PROFILE) }
        return perProfile.maxWith(compareBy<BoardValue>({ it.profileKey == linkedKey }, { it.readAtMs }, { it.profileKey }))
    }

    private val SAME_PROFILE = compareBy<BoardValue>({ it.complete }, { it.readAtMs }, { it.fingerprint() })
}

internal fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
