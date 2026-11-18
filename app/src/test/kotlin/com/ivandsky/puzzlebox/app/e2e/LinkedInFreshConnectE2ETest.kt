package com.ivandsky.puzzlebox.app.e2e

import android.os.Looper
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.GHOSTS
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.zipRef
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A fresh connect lands while Zip's page is already on screen, the way a login's result reaches a page that stayed
 * composed (a wide layout). The day card had asked its question while no account was linked and been answered
 * NotLinked, so the first read after the connect must still go out and be shown, without the player leaving the
 * page and coming back.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class LinkedInFreshConnectE2ETest {

    private val world = LinkedInDataWorld()
    private val today = E2E_TODAY

    @After
    fun closeWorld() {
        world.close()
    }

    @Test
    fun connectingWhileTodaysPageIsOpenReadsAndShowsTodayOnTheFirstTry() {
        world.scriptDay(today, LinkedInOwnResult.Solved(timeSeconds = 64, hintFree = true), GHOSTS)
        shellRobot(graph = world.graph) {
            openZipPage()
            // Past the day view's hold-back: the page's sync has run and found no account.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            rule.waitForIdle()
            assertEquals("no account, no call", emptyList<String>(), world.source.calls)

            runBlocking { world.connect() }
            awaitSynced(today)

            assertEquals(
                "one read of the player's day and one of the board, once each",
                listOf(1, 1),
                listOf("own:", "board:").map { kind -> world.source.calls.count { it.startsWith(kind) } },
            )
            assertEquals(
                "the result is the player's own, as LinkedIn's",
                listOf(zipRef(today) to CompletionSource.LinkedIn),
                runBlocking { completions().observeRecords().first() }.map { it.puzzleRef to it.source },
            )
            val view = offMain { graph.koin.get<ObserveLinkedInDay>()(ZipDefinition.id, today).first() }
            assertEquals(LinkedInDayStatus.Imported(64_000L, 0), view.status)
            assertEquals(GHOSTS.size, (view.board as LinkedInBoardView.Shown).ghosts.size)
        }
    }

    /** Runs [block] on another thread while this one keeps turning the main looper, so work that needs the main thread never waits on it. */
    private fun <T> ShellRobot.offMain(block: suspend () -> T): T {
        val done = CompletableFuture.supplyAsync { runBlocking { block() } }
        awaitLooper { done.isDone }
        return done.get()
    }
}
