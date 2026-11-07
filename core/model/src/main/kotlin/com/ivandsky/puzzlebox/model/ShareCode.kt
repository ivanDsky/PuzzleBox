package com.ivandsky.puzzlebox.model

import java.net.URI
import java.net.URISyntaxException
import kotlin.random.Random

/** A published puzzle's code: 6 Crockford base32 characters, canonical upper case, no dash. */
@JvmInline
value class ShareCode private constructor(val value: String) {
    val display: String get() = value.substring(0, 3) + "-" + value.substring(3)
    val link: String get() = LINK_PREFIX + value

    companion object {
        const val LENGTH: Int = 6
        const val LINK_PREFIX: String = "https://puzzlebox-f3356.web.app/p/"

        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val HOST = "puzzlebox-f3356.web.app"
        private const val PATH_PREFIX = "/p/"

        /** Case-insensitive; ignores '-' and spaces; O→0, I/L→1; null when invalid. */
        fun parse(input: String): ShareCode? {
            val canonical = StringBuilder(LENGTH)
            for (c in input) {
                if (c == '-' || c == ' ') continue
                val symbol = canonicalSymbol(c) ?: return null
                if (canonical.length == LENGTH) return null
                canonical.append(symbol)
            }
            return if (canonical.length == LENGTH) ShareCode(canonical.toString()) else null
        }

        /** https, host puzzlebox-f3356.web.app, path /p/<code>[/]; the code goes through [parse]; null otherwise. */
        fun fromLink(uri: String): ShareCode? {
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
            return parse(path.removePrefix(PATH_PREFIX).removeSuffix("/"))
        }

        fun random(random: Random): ShareCode =
            ShareCode(String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }))

        // ASCII-only upper-casing, so locale case rules (e.g. dotless ı) never map into the alphabet.
        private fun canonicalSymbol(c: Char): Char? {
            val upper = if (c in 'a'..'z') c - ('a' - 'A') else c
            val mapped = when (upper) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> upper
            }
            return mapped.takeIf { it in ALPHABET }
        }
    }
}
