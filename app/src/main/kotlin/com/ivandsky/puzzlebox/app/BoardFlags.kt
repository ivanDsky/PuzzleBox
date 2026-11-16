package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag

/** The one Boolean the root theme takes for the readable board (nav-rethink B3): true exactly when BoardTokens is on. */
internal fun Set<ReleaseFlag>.readableBoard(): Boolean = ReleaseFlag.BoardTokens in this
