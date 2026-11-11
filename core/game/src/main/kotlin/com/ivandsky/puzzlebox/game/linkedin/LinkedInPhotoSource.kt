package com.ivandsky.puzzlebox.game.linkedin

/**
 * Downloads a connection's profile photo. Unlike the account source it carries no session: a photo is a
 * public file on LinkedIn's image host, so nothing of the player's login may ride along.
 */
interface LinkedInPhotoSource {
    /**
     * One GET of [url], or none when the url is not an https url on that host. Null for anything but an
     * image of a sane size: a refusal, an error answer, a failed connection and a wrong or oversized body
     * all read the same, because the caller never retries. Never throws except to cancel.
     */
    suspend fun fetch(url: String): ByteArray?
}
