package com.ivandsky.puzzlebox.data.cloud

/**
 * Every kind `firestore.rules` accepts under an account's cloud space (its `isSpaceKind()` list). "Delete my cloud data"
 * erases all of them whichever kinds this install registered.
 */
object CloudSpaceKinds {
    val ALL: List<String> = listOf("completions", "drafts", "published", "received", "boards", "replays", "reveals", "trainer")
}
