package com.ivandsky.puzzlebox.game

/**
 * Translates completed play into the normalized record (ADR-0004). Pure; never persists —
 * games never read or write records tables. Returning null means this session's completion
 * carries no record identity (e.g. an unsigned custom board) and is not record-eligible.
 */
interface ScoreModel<S : GameSession> {
    fun completion(session: S): CompletionRecord?
}
