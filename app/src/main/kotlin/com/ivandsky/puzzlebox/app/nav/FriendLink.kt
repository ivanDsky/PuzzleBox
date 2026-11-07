package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.model.ShareCode

/** A tapped share link: a friend's [Code], or [Invalid] text from a link whose code doesn't parse. */
internal sealed interface FriendLink {

    data class Code(val code: ShareCode) : FriendLink

    /** What followed `/p/` (query, fragment and a trailing slash dropped), for the code sheet to show. */
    data class Invalid(val text: String) : FriendLink

    companion object {
        /** [uri] as a link under [ShareCode.LINK_PREFIX], read by [ShareCode.fromLink]; null for any other URI. */
        fun of(uri: String?): FriendLink? {
            if (uri == null || !uri.startsWith(ShareCode.LINK_PREFIX, ignoreCase = true)) return null
            ShareCode.fromLink(uri)?.let { return Code(it) }
            val text = uri.substring(ShareCode.LINK_PREFIX.length).substringBefore('#').substringBefore('?')
            return Invalid(text.removeSuffix("/"))
        }
    }
}
