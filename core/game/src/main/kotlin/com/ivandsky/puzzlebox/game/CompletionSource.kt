package com.ivandsky.puzzlebox.game

/**
 * Where a completion came from. [App] is a solve earned in the app; [LinkedIn] is an import
 * of the player's own LinkedIn result. Stored by [storedName], so renaming a constant never
 * rewrites rows.
 */
enum class CompletionSource(val storedName: String) {
    App("app"),
    LinkedIn("linkedin"),
    ;

    companion object {
        /** An unknown name reads as [App]: a row written by a newer build must not crash an older one. */
        fun fromStored(name: String): CompletionSource = entries.firstOrNull { it.storedName == name } ?: App
    }
}
