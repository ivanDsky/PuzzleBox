package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel

/** The renderer model the board draws — built here (F2: the state→model call site). */
fun TangoPlayController.rendererModel(): TangoRendererModel? {
    val current = session ?: return null
    return TangoRendererModel.of(
        TangoState(current.puzzle, current.grid()),
        verdict = lastRejection,
        suppressedSets = suppressedSets,
    ).let(guidance::apply)
}

/** Versioned session bytes (A19/F2 §2) — what [com.ivandsky.puzzlebox.gameui.PlayEnvironment.onSessionChanged] receives. */
fun TangoPlayController.persist(): ByteArray? = session?.let(TangoSessionCodec::serialize)
