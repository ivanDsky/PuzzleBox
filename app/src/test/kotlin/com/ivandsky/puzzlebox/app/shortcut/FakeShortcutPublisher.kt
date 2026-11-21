package com.ivandsky.puzzlebox.app.shortcut

/** Records what the hook published; [failure] makes the call throw like a refusing shortcut manager. */
class FakeShortcutPublisher(private val failure: Exception? = null) : ShortcutPublisher {
    var published: List<ShortcutEntry>? = null
        private set

    override fun publish(entries: List<ShortcutEntry>) {
        failure?.let { throw it }
        published = entries
    }
}
