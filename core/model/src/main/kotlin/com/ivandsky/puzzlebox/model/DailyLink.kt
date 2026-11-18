package com.ivandsky.puzzlebox.model

import java.net.URI
import java.net.URISyntaxException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/** The link to one game's puzzle of a day: `https://puzzlebox-f3356.web.app/p/<game id>/<yyyy-MM-dd>`. */
object DailyLink {
    private const val HOST = "puzzlebox-f3356.web.app"
    private const val PATH_PREFIX = "/p/"
    private const val DATE_LENGTH = 10

    fun of(gameId: GameId, day: LocalDate): String = "${ShareCode.LINK_PREFIX}${gameId.value}/$day"

    /**
     * https, host puzzlebox-f3356.web.app, no user info or port, path /p/<id>/<yyyy-MM-dd>[/]; the id is lower-cased
     * and must be a-z0-9, the date strict ISO; a query or fragment is ignored. Null otherwise, so a code link or a
     * match link never reads as a daily one.
     */
    fun parse(uri: String): DailyRef? {
        val parsed = try {
            URI(uri)
        } catch (_: URISyntaxException) {
            return null
        }
        if (!parsed.scheme.equals("https", ignoreCase = true)) return null
        if (!parsed.host.equals(HOST, ignoreCase = true)) return null
        if (parsed.rawUserInfo != null || parsed.port != -1) return null
        val path = parsed.rawPath ?: return null
        if (!path.startsWith(PATH_PREFIX)) return null
        val parts = path.removePrefix(PATH_PREFIX).removeSuffix("/").split('/')
        if (parts.size != 2) return null
        val id = parts[0].lowercase(Locale.ROOT)
        if (id.isEmpty() || !id.all { it in 'a'..'z' || it in '0'..'9' }) return null
        val day = strictDay(parts[1]) ?: return null
        return DailyRef(GameId(id), day)
    }

    // Digits only, so a signed or short year ("+12026-02-03", "2026-2-3") never reaches the ISO parser.
    private fun strictDay(text: String): LocalDate? {
        if (text.length != DATE_LENGTH) return null
        if (text.withIndex().any { (i, c) -> if (i == 4 || i == 7) c != '-' else c !in '0'..'9' }) return null
        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
