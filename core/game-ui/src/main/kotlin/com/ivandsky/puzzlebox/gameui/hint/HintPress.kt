package com.ivandsky.puzzlebox.gameui.hint

/** Ceiling of a play's hint count (spec A9): presses saturate here, never wrap or throw. */
const val MAX_HINTS_USED: Int = 255

/** The answer to one hint press: what the caller does, and the play's hint count afterwards. */
data class HintPress(val action: Action, val used: Int) {
    enum class Action {
        /** Show the step's Spotlight. */
        SHOW,

        /** Erase the culprit entry (spec A4). Only for a culprit pressed while it is shown; always free. */
        ERASE_CULPRIT,
    }
}
