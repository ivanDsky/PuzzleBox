package com.ivandsky.puzzlebox.gameui.hint

/**
 * Where a game controller gets its [HintMode]. Read once, when the controller is created, so a
 * debug override applies to the next board opened and never mid-play (spec A20). Bound once in
 * the app's Koin graph; UI modules only resolve it and never read a release flag.
 */
fun interface HintModeSource {
    fun current(): HintMode
}
