package com.ivandsky.puzzlebox.gameui

import kotlinx.coroutines.CoroutineScope

/**
 * Builds each lesson's controller in a sandbox (spec A11). The game gets a
 * [PlayEnvironment] whose session sink drops every change and whose win only advances
 * [session]. So a lesson never reaches the record emitter or the session store, and the
 * host starts no clock. A late win from an earlier lesson's controller is ignored.
 */
class TutorialSandbox(private val ui: GamePlayUi, private val session: TutorialSession) {

    /** A fresh controller holding [lesson]'s puzzle; [scope] is that lesson's lifetime. */
    fun controllerFor(lesson: TutorialState.Playing, scope: CoroutineScope): GamePlayController {
        val controller = ui.createController(
            PlayEnvironment(
                scope = scope,
                onSessionChanged = {},
                onWon = { if ((session.state.value as? TutorialState.Playing)?.index == lesson.index) session.advance() },
            ),
        )
        val adopted = controller.adoptToday(lesson.step.puzzle, lesson.step.ref)
        // The lessons are hand-authored fixtures: one the game cannot play is a bug.
        check(adopted == Adopt.Adopted) {
            "${ui.gameId.value} tutorial step ${lesson.index + 1} is not playable: $adopted"
        }
        return controller
    }
}
