package com.ivandsky.puzzlebox.app.shortcut

/** The seam over the system's dynamic launcher shortcuts, so the publishing rules test without a shortcut manager. */
interface ShortcutPublisher {

    /** Replaces the whole dynamic set with [entries]. */
    fun publish(entries: List<ShortcutEntry>)

    /** Removes every dynamic shortcut. */
    fun clear()
}
