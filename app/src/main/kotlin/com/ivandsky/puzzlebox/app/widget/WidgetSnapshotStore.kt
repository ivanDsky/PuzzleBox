package com.ivandsky.puzzlebox.app.widget

/** Where the widget keeps its last good picture between sessions. Neither call throws; a store that cannot read loads null. */
interface WidgetSnapshotStore {
    suspend fun load(): WidgetSnapshot?
    suspend fun save(snapshot: WidgetSnapshot)
}
