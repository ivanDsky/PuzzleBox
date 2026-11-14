package com.ivandsky.puzzlebox.backend.firebase

/** In-memory [CacheClearMark]; [writes] counts every [mark] so a test can tell "wrote nothing" from "wrote". */
internal class FakeCacheClearMark(var pending: Boolean = false) : CacheClearMark {
    var writes = 0

    override fun mark() {
        writes++
        pending = true
    }

    override fun isPending(): Boolean = pending

    override fun drop() {
        pending = false
    }
}
