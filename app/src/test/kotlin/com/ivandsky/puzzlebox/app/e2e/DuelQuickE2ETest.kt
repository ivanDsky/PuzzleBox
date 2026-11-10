package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Quick preset: the New duel sheet's `duel.preset.quick` creates a best-of-3 over mixed past
 * dailies of different games with 24 hours to play, with no further choice made.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class DuelQuickE2ETest {

    private val graph = E2eKoinGraph(E2E_TODAY, releaseFlags = FakeReleaseFlags(ReleaseFlag.Competitions))

    @Test
    fun theQuickPresetCreatesABestOfThreeOverMixedPastDailiesWithADayToPlay() = shellRobot(onPlay, graph = graph) {
        graph.identity.currentIdentity = Identity(uid = "me", displayName = "Me", photoUrl = null)
        graph.matches.now = Instant.now()
        graph.matches.signedInUid = "me"
        val today = graph.koin.get<GetPuzzleDay>()()
        graph.calendar.setAll(today)
        // Three games have past dailies in the archive, ten days of them; the other games have none.
        listOf(ZipDefinition.id, TangoDefinition.id, QueensDefinition.id).forEach { game ->
            graph.daily.setArchiveRange(game, today.minusDays(10)..today.minusDays(1))
            graph.daily.setPuzzleNumbers(game) { day -> day.toEpochDay().toInt() }
        }

        val code = createQuickDuel()

        val match = checkNotNull(graph.matches.matches[code])
        assertEquals("Quick is a best of 3", 3, match.legs.size)
        assertEquals("Quick gives a day to play", 24, match.deadlineHours)
        assertTrue("every puzzle is a past or present daily", match.legs.all { (it.ref as? LegRef.Daily)?.day?.let { day -> day <= today } == true })
        assertEquals(
            "the puzzles are mixed across games, one from each",
            setOf(ZipDefinition.id, TangoDefinition.id, QueensDefinition.id),
            match.legs.map { it.ref.gameId }.toSet(),
        )
    }

    /** The Play tab is selected; Home's stack holds History. */
    private val onPlay = NavigationState(
        tab = PlayKey,
        stacks = listOf(listOf(HomeKey, HistoryKey()), listOf(PlayKey), listOf(ProfileKey)),
    )
}
