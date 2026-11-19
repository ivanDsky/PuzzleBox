package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import com.ivandsky.puzzlebox.common.flags.DeclaredFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ShippedReleaseFlags
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module

/**
 * The Studio's test solve over the real shell's graph, with no flag on or, when [shipped], every declared flag at its
 * release value: read from the graph's `DeclaredFlags`, so a flag that moves to its owner's enum stays in. The Studio
 * keeps its drafts in [drafts], and every play controller a game creates is kept, so a test can play the test solve's
 * board: no play store slot holds it.
 */
internal class TestSolveWorld(shipped: Boolean = false) {

    val drafts = FakeDraftRepository()
    private val created = mutableListOf<GamePlayController>()

    val graph = shellGraph(
        overrides = module {
            single<DraftRepository> { drafts }
            single { GamePlayRegistry(getAll<GamePlayUi>().map(::Keeping)) }
            if (shipped) single<ReleaseFlags> { ShippedReleaseFlags(get<DeclaredFlags>().all) }
        },
    )

    /** The open test solve's controller: the last one a game created. */
    val controller: GamePlayController? get() = created.lastOrNull()

    /** The test stored on the draft, if any. */
    val stored: TestSolve? get() = drafts.rows[DRAFT]?.testSolve

    /** Keeps [board] as an untested draft titled [title], opens it in the Studio and taps Test solve once it is offered. */
    fun startTestSolve(robot: ShellRobot, board: EditorDraft<*>, title: String) = with(robot) {
        runBlocking { drafts.upsert(readyDraft(board, title).copy(testSolve = null)) }
        openYours()
        tap("create.draft.row")
        testSolveAgain(this)
    }

    /** Taps the open Studio's Test solve once the verdict has made it available. */
    fun testSolveAgain(robot: ShellRobot) = with(robot) {
        val offered = hasTestTag("create.action.testSolve") and isEnabled()
        rule.waitUntil(FRIEND_TIMEOUT_MS) { rule.onAllNodes(offered).fetchSemanticsNodes().isNotEmpty() }
        tap("create.action.testSolve")
    }

    /** [real], with every controller it creates kept in [created]. */
    private inner class Keeping(private val real: GamePlayUi) : GamePlayUi by real {
        override fun createController(env: PlayEnvironment): GamePlayController =
            real.createController(env).also { created += it }
    }

    private companion object {
        /** [readyDraft]'s id. */
        const val DRAFT = "draft"
    }
}
