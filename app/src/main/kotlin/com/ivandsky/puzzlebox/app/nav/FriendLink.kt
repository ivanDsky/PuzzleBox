package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.model.ShareCode

/** A tapped share link: a friend's [Code], a duel [Match], or [Invalid] text from a `/p/` link whose code doesn't parse. */
internal sealed interface FriendLink {

    data class Code(val code: ShareCode) : FriendLink

    /** What followed `/p/` (query, fragment and a trailing slash dropped), for the code sheet to show. */
    data class Invalid(val text: String) : FriendLink

    /** A `/m/` link whose code parses through [ShareCode.fromMatchLink]; one that doesn't is no friend link. */
    data class Match(val code: ShareCode) : FriendLink

    companion object {
        /** [uri] as a link under [ShareCode.LINK_PREFIX] or [ShareCode.MATCH_LINK_PREFIX]; null for any other URI. */
        fun of(uri: String?): FriendLink? {
            if (uri == null) return null
            if (uri.startsWith(ShareCode.MATCH_LINK_PREFIX, ignoreCase = true)) {
                return ShareCode.fromMatchLink(uri)?.let { Match(it) }
            }
            if (!uri.startsWith(ShareCode.LINK_PREFIX, ignoreCase = true)) return null
            ShareCode.fromLink(uri)?.let { return Code(it) }
            val text = uri.substring(ShareCode.LINK_PREFIX.length).substringBefore('#').substringBefore('?')
            return Invalid(text.removeSuffix("/"))
        }
    }
}
