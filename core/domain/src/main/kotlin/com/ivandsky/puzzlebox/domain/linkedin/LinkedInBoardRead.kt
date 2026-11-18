package com.ivandsky.puzzlebox.domain.linkedin

/**
 * When a day's connections board was last read from LinkedIn, in epoch ms. [complete] says that read came after the day ended,
 * so it is the day's last: a board read while its day still ran is partial, and later reads can still change it.
 */
data class LinkedInBoardRead(val atMs: Long, val complete: Boolean)
