package com.ivandsky.puzzlebox.game

/**
 * A LinkedIn persisted GraphQL query (ADR-0013): the server-side [queryId] hash plus its
 * Rest.li [variables], e.g. `(gameTypeId:7)`. Both are sent verbatim — Rest.li syntax must
 * reach the wire unescaped, and a rotated hash surfaces as Unavailable rather than a guess.
 */
class PersistedQuery(val queryId: String, val variables: String)
