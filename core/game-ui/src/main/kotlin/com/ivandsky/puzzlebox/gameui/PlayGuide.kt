package com.ivandsky.puzzlebox.gameui

/**
 * A controller that can guide a lesson (spec A12): it draws the answer for every open
 * square faintly on its own board until the player matches it, and holds back rule
 * captions and rule styling, because the rules come in later lessons. [TutorialSandbox]
 * calls [guide] once, right after a guided step's puzzle is adopted.
 */
interface PlayGuide {
    fun guide()
}
