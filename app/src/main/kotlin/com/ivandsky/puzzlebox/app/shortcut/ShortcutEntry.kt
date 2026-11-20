package com.ivandsky.puzzlebox.app.shortcut

/** One launcher menu entry. [gameName] is null for [ShortcutTarget.UpNext]; [rank] 0 is Up next, then 1..n. */
data class ShortcutEntry(val target: ShortcutTarget, val gameName: String?, val rank: Int)
