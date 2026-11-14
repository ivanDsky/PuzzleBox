package com.ivandsky.puzzlebox.data.cloud.kinds

/**
 * The only keys a board document may carry (ADR-0029 decisions 1 and 8, A30). The encoder builds a doc from these
 * and nothing else, so no other field, photo reference or session value can reach the cloud. The rules' `hasOnly`
 * lists for boards are these [DOC] keys plus the server's `updatedAt`, and, for every row of `rows`, these [ROW] keys.
 * A key added here without the rules' list (or the other way round) makes every board write refused, so a change
 * here edits `firestore.rules` in the same change; `BoardDocKeysRulesTest` fails when the two lists differ.
 * [REF] is a connection's LinkedIn member id, kept so a restored board shows the same avatars and row identity; it
 * sits in the account's owner-only space, and the Play Data safety text before the flip (A42) names it.
 */
internal object BoardDocKeys {
    const val FORMAT_VERSION = "formatVersion"
    const val GAME = "game"
    const val DAY = "day"
    const val PROFILE_KEY = "profileKey"
    const val READ_AT_MS = "readAtMs"
    const val COMPLETE = "complete"
    const val ROWS = "rows"

    const val NAME = "name"
    const val TIME_MS = "timeMs"
    const val HINT_FREE = "hintFree"
    const val FLAWLESS = "flawless"
    const val IS_OWN = "isOwn"
    const val REF = "ref"

    /** The keys of a board document, `updatedAt` aside (the space stamps it). */
    val DOC: Set<String> = setOf(FORMAT_VERSION, GAME, DAY, PROFILE_KEY, READ_AT_MS, COMPLETE, ROWS)

    /** The keys of one row of a board document. */
    val ROW: Set<String> = setOf(NAME, TIME_MS, HINT_FREE, FLAWLESS, IS_OWN, REF)
}
