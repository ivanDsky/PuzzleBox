package com.ivandsky.puzzlebox.database

/** A replay of an account's completion as the cloud sees it: the completion's cloud id and the encoded solve. */
class CompletionReplayCloudRow(
    val syncId: String,
    val replay: ByteArray,
)
