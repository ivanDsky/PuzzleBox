package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.PlayKey
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Nav-rethink 17: the before/after review of flat surfaces (B1). Renders each review screen through the real
 * shell ([shellRobot]) with the flat switch off and on, phone and tablet, light and dark, and writes
 * `<screen>-<phone|tablet>-<light|dark>-<off|on>.png` under `app/build/flat-review/`, plus one side-by-side
 * contact sheet per screen under `sheets/`. A build folder, never a tracked snapshots directory; the class name
 * carries `Screenshots`, so it runs only under `-Proborazzi.record` (or verify) and is deleted in cleanup.
 *
 * `./gradlew :app:testDebugUnitTest --tests '*FlatSurfacesReviewScreenshots*' -Proborazzi.record`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class)
class FlatSurfacesReviewScreenshots {

    @Test fun today() = render("today", HOME) { awaitTag("route.home") }

    @Test fun dailies() = render("dailies", PLAY) {
        awaitTag("route.library")
        if (shows("library.tab.dailies")) tap("library.tab.dailies")
    }

    @Test fun duels() = render("duels", PLAY) {
        seedDuelNeedingMe()
        openDuelsTab()
    }

    @Test fun `game page`() = render("game-page", PLAY) {
        openPage(ZIP)
    }

    @Test fun `zip board`() = render("zip-board", PLAY) {
        openGame(ZIP)
        traceZip(steps = ZIP_TRACE_STEPS)
    }

    @Test fun `sudoku board`() = render("sudoku-board", PLAY) {
        openGame(SUDOKU)
        awaitTag("sudoku.keypad")
    }

    @Test fun `strands board`() = render("strands-board", PLAY) {
        openGame(STRANDS)
        awaitTag("strands.hint")
    }

    private fun render(screen: String, state: () -> NavigationState, open: ShellRobot.() -> Unit) =
        FlatReviewMatrix.render(screen, state, open)

    /** A game's library page with today's card loaded, not yet played. */
    private fun ShellRobot.openPage(gameId: String) {
        if (shows("library.tab.dailies") && !shows("library.row.$gameId")) tap("library.tab.dailies")
        tap("library.row.$gameId")
        awaitTag("library.page.$gameId")
        val offline = hasTestTag("card.status_mark.offline") and hasAnyAncestor(hasTestTag("library.day_card"))
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodes(hasTestTag("library.play") and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(offline, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    /** A friend's Daily Zip duel inviting the player, so the Duels tab lists a row that needs them. */
    private fun ShellRobot.seedDuelNeedingMe() = runBlocking {
        graph.matches.now = Instant.now()
        graph.matches.signedInUid = FRIEND
        val draft = MatchDraft(
            code = checkNotNull(ShareCode.parse(DUEL_CODE)),
            deadlineHours = Match.DEADLINE_CHOICES.first(),
            challenger = Player(FRIEND, "Sam", photoUrl = null),
            invitedUid = ME,
            legs = listOf(MatchLeg(LegRef.Daily(ZipDefinition.id, E2E_TODAY, ZIP_NUMBER), authorUid = null)),
            includeFriends = false,
            rematchOf = null,
        )
        check(graph.matches.create(draft) is CreateResult.Created) { "the duel was not created" }
        graph.identity.currentIdentity = Identity(uid = ME, displayName = "Me", photoUrl = null)
        graph.matches.signedInUid = ME
    }

    /** The first [steps] cells of Zip's solution as drag events, leaving a trace on the board. */
    private fun ShellRobot.traceZip(steps: Int) {
        val store = graph.koin.get<PlaySessionStore>()
        val slot = PlaySlot.Daily(ZipDefinition.id, E2E_TODAY)
        fun zip() = rule.runOnIdle { store.peek(slot) }?.controller as? ZipPlayController
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { zip()?.session } != null }
        val controller = checkNotNull(zip()) { "no live zip play for $E2E_TODAY" }
        rule.runOnIdle {
            ZipPlayFixture.solution.take(steps).forEach {
                controller.onDragEvent(DragEvent.Extend(GridCell(it.idx / ZIP_SIZE, it.idx % ZIP_SIZE)))
            }
        }
        rule.waitForIdle()
    }

    private companion object {
        val HOME = { tabAt(HomeKey) }
        val PLAY = { tabAt(PlayKey) }
        const val ZIP = "zip"
        const val ZIP_NUMBER = 592
        const val ME = "me"
        const val FRIEND = "sam"
        const val DUEL_CODE = "K7Q2MX"
        const val SUDOKU = "minisudoku"
        const val STRANDS = "strands"
        const val ZIP_SIZE = 5
        const val ZIP_TRACE_STEPS = 13
        const val TIMEOUT_MS = 5_000L
    }
}
