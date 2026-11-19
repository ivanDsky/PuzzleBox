package com.ivandsky.puzzlebox.gameui.replay

/** How a replay board draws one frame, beyond the frame itself. */
class ReplayBoardOptions(
    /** The replay is playing, the frame is not a seek's landing and Reduce motion is off: a forward step may animate ([stepsForward]). Paused, seeking or stepping back, the board rests. */
    val animate: Boolean,
    /** False hides every rule-violation mark; errors are derived per frame, never stored. */
    val showErrors: Boolean,
)
