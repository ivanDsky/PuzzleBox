package com.ivandsky.puzzlebox.app.widget

/** A [WidgetSnapshotStore] in memory: [snapshot] is what is saved. */
internal class MemoryWidgetSnapshotStore(var snapshot: WidgetSnapshot? = null) : WidgetSnapshotStore {
    override suspend fun load(): WidgetSnapshot? = snapshot
    override suspend fun save(snapshot: WidgetSnapshot) {
        this.snapshot = snapshot
    }
}
