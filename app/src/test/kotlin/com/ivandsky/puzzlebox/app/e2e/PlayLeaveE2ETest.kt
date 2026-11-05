package com.ivandsky.puzzlebox.app.e2e

import android.os.Looper
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Leave-and-resume rows over the real shell: N12 (same board, clock paused while away), N13
 * (reselect saves), N21 (one shared play across tabs), N22 (offline fallback, retry, back
 * to Home), SC5 (every leave path ends in a resumable save; the kill path is
 * ProcessDeathE2ETest's N19), SC6 (resume restores board, banked clock ±1 s and a working
 * Undo) and SC11 (hub row, Home card and Play-tab dot; tapping each resumes).
 *
 * Predictive back's animation cannot be observed on the JVM: its row asserts the committed
 * gesture's state and save only (the device suite covers the feel).
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class PlayLeaveE2ETest {

    @Test
    fun `N12 hopping to Profile and back keeps the board, and the clock pauses while away`() = shellRobot(playHub) {
        openGame(ZIP)
        val live = playMidGame()

        tapTab(ProfileKey)
        assertState(inZip.selectTab(ProfileKey))
        assertRoute(PROFILE)
        val away = session(live)
        assertNull("clock paused while away", away.runningSince)
        Thread.sleep(AWAY_MS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(AWAY_MS))
        assertEquals("no time counted while away", away.elapsedMs, session(live).elapsedMs)

        tapTab(PlayKey)
        assertState(inZip)
        assertRoute(GAME)
        assertSame("the same live play", live, rule.runOnIdle { zip() })
        val back = session(live)
        assertEquals("same board", MID_GAME, back.path)
        assertEquals("time away not counted", away.elapsedMs, back.elapsedMs)
        assertNotNull("clock runs again", back.runningSince)
    }

    @Test
    fun `N13 SC5 reselecting Play pops to the hub and saves`() = leaveAndResume(PlayLeave.Released) {
        tapTab(PlayKey)
        assertState(playHub)
        assertRoute(HUB)
    }

    @Test
    fun `SC5 SC6 system back saves and resumes`() = leaveAndResume(PlayLeave.Released) { back() }

    @Test
    fun `SC5 SC6 predictive back saves and resumes`() = leaveAndResume(PlayLeave.Released) { predictiveBack(commit = true) }

    @Test
    fun `SC5 SC6 Up saves and resumes`() = leaveAndResume(PlayLeave.Released) { tap(GAME_UP) }

    @Test
    fun `SC5 SC6 picking another game saves and resumes`() = leaveAndResume(PlayLeave.Released) {
        back()
        openGame(TANGO)
        assertState(stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(TANGO))))
        back()
    }

    @Test
    fun `SC5 SC6 a tab switch saves and resumes`() = leaveAndResume(PlayLeave.Kept) {
        tapTab(HomeKey)
        assertRoute(HOME)
    }

    @Test
    fun `SC5 SC6 backgrounding saves and resumes`() = leaveAndResume(PlayLeave.Backgrounded) { background() }

    @Test
    fun `N21 one shared Zip play across Home and Play`() {
        val both = stacks(tab = HomeKey, home = listOf(HomeKey, GameKey(ZIP)), play = listOf(PlayKey, GameKey(ZIP)))
        shellRobot(both) {
            assertRoute(GAME)
            val live = playMidGame()

            tapTab(PlayKey)
            assertState(both.selectTab(PlayKey))
            assertRoute(GAME)
            assertSame("one shared play", live, rule.runOnIdle { zip() })
            assertEquals(MID_GAME, session(live).path)
            val more = ZipPlayFixture.solution[MID_GAME.size]
            rule.runOnIdle { live.extend(more) }

            tapTab(HomeKey)
            assertState(both)
            assertRoute(GAME)
            assertSame(live, rule.runOnIdle { zip() })
            assertEquals("Home shows Play's move", MID_GAME + more, session(live).path)
        }
    }

    @Test
    fun `N22 offline with no cache Play today shows the fallback, retries, and back returns to Home`() {
        val offline = E2eKoinGraph(E2E_TODAY).apply {
            daily.setToday(ZipDefinition.id, DailyToday.Unavailable(FetchError.Unavailable()))
        }
        shellRobot(graph = offline) {
            tapText(PLAY_TODAY)
            assertState(stacks(tab = HomeKey, home = listOf(HomeKey, GameKey(ZIP))))
            assertRoute(GAME)
            awaitTag(BOARD_ERROR)

            graph.daily.setToday(ZipDefinition.id, DailyToday.Fresh(ZipPlayFixture.envelope()))
            rule.onNodeWithText(TRY_AGAIN).performScrollTo().performClick()
            awaitZip()

            back()
            assertState(stacks(tab = HomeKey))
            assertRoute(HOME)
        }
    }

    @Test
    fun `SC11 the hub row reads In progress, and tapping it resumes`() = shellRobot(playHub) {
        openGame(ZIP)
        val live = playMidGame()
        back()
        awaitSaved()

        awaitText(IN_PROGRESS)
        openGame(ZIP)
        assertState(inZip)
        assertResumed(session(live))
    }

    @Test
    fun `SC11 Home's card reads Resume, and tapping it resumes`() = shellRobot(playHub) {
        openGame(ZIP)
        val live = playMidGame()
        back()
        awaitSaved()
        tapTab(HomeKey)

        tapText(RESUME)
        assertState(stacks(tab = HomeKey, home = listOf(HomeKey, GameKey(ZIP))))
        assertRoute(GAME)
        assertResumed(session(live))
    }

    @Test
    fun `SC11 the Play tab shows a dot, and tapping it resumes`() = shellRobot(playHub) {
        openGame(ZIP)
        val live = playMidGame()
        tapTab(HomeKey)
        awaitSaved()

        awaitTag(PLAY_BADGE)
        tapTab(PlayKey)
        assertState(inZip)
        assertRoute(GAME)
        assertResumed(session(live))
    }

    @Test
    fun `SC11 after a kill the cues still show, and the hub row resumes`() = shellRobot(playHub) {
        openGame(ZIP)
        val killed = session(playMidGame())
        background()
        awaitSaved()
        foreground()

        relaunch()
        assertState(playHub)
        awaitText(IN_PROGRESS)
        awaitTag(PLAY_BADGE)
        openGame(ZIP)
        assertResumed(killed)
    }

    private enum class PlayLeave { Released, Kept, Backgrounded } // popped, kept on its stack, or stopped

    /** Opens Zip mid-game from the hub, runs [leave], checks the save, then reopens and checks SC6. */
    private fun leaveAndResume(kind: PlayLeave, leave: ShellRobot.() -> Unit) = shellRobot(playHub) {
        openGame(ZIP)
        val live = playMidGame()

        leave()
        awaitSaved()
        // A released or stopped play folds its clock before its final save: read it after.
        val left = session(live)
        assertNull("clock folded on leave", left.runningSince)

        when (kind) {
            PlayLeave.Released -> openGame(ZIP)
            PlayLeave.Kept -> tapTab(PlayKey)
            PlayLeave.Backgrounded -> foreground()
        }
        assertState(inZip)
        assertRoute(GAME)
        assertResumed(left)
    }

    /** SC6: the reopened board has [left]'s trace, undo stack and banked time (±1 s), and Undo works. */
    private fun ShellRobot.assertResumed(left: ZipSession) {
        val resumed = awaitZip()
        assertEquals("same board", left.path, resumed.path)
        assertEquals("same undo stack", left.history, resumed.history)
        assertEquals("same elapsed time (±1 s)", left.elapsedMs.toDouble(), resumed.elapsedMs.toDouble(), 1_000.0)
        rule.runOnIdle { zip().undo() }
        assertEquals("undo works after resume", left.path.dropLast(1), rule.runOnIdle { zip().session?.path })
    }

    /** Waits for today's Zip to reach the saved plays, stepping the throttle window's clock. */
    private fun ShellRobot.awaitSaved() {
        rule.waitUntil(TIMEOUT_MS) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(STEP_MS))
            todaySlot in graph.saved.rows
        }
    }

    private fun ShellRobot.playMidGame(): ZipPlayController {
        awaitZip()
        rule.runOnIdle { MID_GAME.forEach { zip().extend(it) } }
        return rule.runOnIdle { zip() }
    }

    private fun session(controller: ZipPlayController): ZipSession = checkNotNull(controller.session)

    private val ShellRobot.store: PlaySessionStore get() = graph.koin.get()

    private fun ShellRobot.zip(): ZipPlayController =
        checkNotNull(store.peek(todaySlot)) { "no live zip play for $E2E_TODAY" }.controller as ZipPlayController

    private fun ShellRobot.awaitZip(): ZipSession {
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { store.peek(todaySlot) }?.let { (it.controller as ZipPlayController).session } != null }
        return checkNotNull(rule.runOnIdle { zip().session })
    }

    private fun ShellRobot.awaitText(text: String) =
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ZipPlayController.extend(cell: Cell) = onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        profile: List<NavKey> = listOf(ProfileKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, profile))

    private val playHub = stacks(tab = PlayKey)
    private val inZip = stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(ZIP)))
    private val todaySlot = PlaySlot(ZipDefinition.id, E2E_TODAY)

    private companion object {
        const val HOME = "route.home"
        const val HUB = "route.hub"
        const val GAME = "route.game"
        const val PROFILE = "route.profile"
        const val GAME_UP = "game.up"
        const val BOARD_ERROR = "play.error"
        const val PLAY_TODAY = "Play today"
        const val RESUME = "Resume"
        const val IN_PROGRESS = "In progress"
        const val TRY_AGAIN = "Try again"
        const val ZIP = "zip"
        const val TANGO = "tango"
        const val AWAY_MS = 300L
        const val STEP_MS = 100L
        const val TIMEOUT_MS = 5_000L
        val PLAY_BADGE = "${topLevelDestinations.single { it.key == PlayKey }.tag}.badge"
        val MID_GAME: List<Cell> = ZipPlayFixture.solution.take(3)
    }
}
