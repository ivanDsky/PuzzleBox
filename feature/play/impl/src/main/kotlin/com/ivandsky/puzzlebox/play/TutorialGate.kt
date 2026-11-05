package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.gameui.TutorialSession

/** Whether a game entry shows its tutorial (spec A11) instead of its board. */
internal sealed interface TutorialGate {

    /** The first-play check (tutorial seen?) has not landed: neither board nor tutorial shows yet. */
    data object Checking : TutorialGate

    /** The tutorial runs [session] — a first play, or How to play over the live play. */
    data class Open(val session: TutorialSession) : TutorialGate

    /** The board shows. */
    data object Closed : TutorialGate
}
