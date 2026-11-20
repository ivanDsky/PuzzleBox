package com.ivandsky.puzzlebox.app.shortcut

/** Records what the hook published and how often it cleared; [failure] makes both calls throw like a refusing shortcut manager. */
class FakeShortcutPublisher(private val failure: Exception? = null) : ShortcutPublisher {
    var published: List<ShortcutEntry>? = null
        private set
    var clears = 0
        private set

    override fun publish(entries: List<ShortcutEntry>) {
        failure?.let { throw it }
        published = entries
    }

    override fun clear() {
        failure?.let { throw it }
        clears++
    }
}
