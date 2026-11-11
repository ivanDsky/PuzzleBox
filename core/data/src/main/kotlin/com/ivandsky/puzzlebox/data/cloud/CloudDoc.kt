package com.ivandsky.puzzlebox.data.cloud

/**
 * One document in an account's cloud space. [fields] hold plain values only: `String`, `Long`, `Boolean`, `Double`,
 * `ByteArray`, null, and lists and `Map<String, *>` of them. [updatedAtMs] is the server's stamp (epoch ms) and the
 * delta-sync cursor; it is null on a doc that has not been written yet, and the space ignores any value passed to `put`.
 */
data class CloudDoc(val id: String, val fields: Map<String, Any?>, val updatedAtMs: Long? = null)
