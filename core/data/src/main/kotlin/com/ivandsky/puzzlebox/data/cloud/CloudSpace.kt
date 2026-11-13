package com.ivandsky.puzzlebox.data.cloud

/**
 * The one seam to an account's cloud space (ADR-0029 decision 2). Kind-agnostic: a [kind] is a name and a doc is plain
 * values, so a new synced kind never touches an adapter. Every write returns [CloudResult.WrongAccount] without
 * writing when the signed-in account is not [uid].
 */
interface CloudSpace {
    /** The docs of [kind] whose `updatedAtMs >= cursorMs`, or all of them when [cursorMs] is null; read from the server. */
    suspend fun changedSince(uid: String, kind: String, cursorMs: Long?): CloudResult<List<CloudDoc>>

    /** Deep-merges each doc into its id (nested maps merge, any other value replaces) and stamps `updatedAtMs`. */
    suspend fun put(uid: String, kind: String, docs: List<CloudDoc>): CloudResult<Unit>

    /** Deletes the docs with [ids]; an id that does not exist is not an error. */
    suspend fun delete(uid: String, kind: String, ids: List<String>): CloudResult<Unit>

    /** Deletes every doc of the given [kinds] (the erase passes [CloudSpaceKinds.ALL]), leaving other kinds alone. */
    suspend fun deleteAll(uid: String, kinds: List<String>): CloudResult<Unit>
}
