package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.PlayKey
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shell's duels wiring over the real app: Games has the Duels tab and Play carries the dot for a duel waiting on
 * the player.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class DuelsShellE2ETest {

    @Test
    fun gamesHasTheDuelsTabAndPlayCarriesTheDot() = shellRobot(graph = E2eKoinGraph(E2E_TODAY)) {
        seedDuelNeedingMe()
        awaitTag(PLAY_BADGE)

        tapTab(PlayKey)
        awaitTag("library.tab.duels")
        assertTrue("Games has the Duels tab", shows("library.tab.duels"))
        tapTab(HomeKey)
        assertTrue("Play still carries the dot elsewhere", shows(PLAY_BADGE))
    }

    /** A friend's duel inviting the player: one duel needs them. */
    private fun ShellRobot.seedDuelNeedingMe() = runBlocking {
        graph.matches.now = Instant.now()
        graph.matches.signedInUid = FRIEND
        val draft = MatchDraft(
            code = DUEL_CODE,
            deadlineHours = Match.DEADLINE_CHOICES.first(),
            challenger = Player(FRIEND, FRIEND_NAME, photoUrl = null),
            invitedUid = ME,
            legs = listOf(MatchLeg(LegRef.Daily(ZipDefinition.id, E2E_TODAY, PUZZLE_NUMBER), authorUid = null)),
            includeFriends = false,
            rematchOf = null,
        )
        check(graph.matches.create(draft) is CreateResult.Created) { "the duel was not created" }
        graph.identity.currentIdentity = Identity(uid = ME, displayName = ME_NAME, photoUrl = null)
        graph.matches.signedInUid = ME
    }

    private companion object {
        const val ME = "me"
        const val ME_NAME = "Me"
        const val FRIEND = "sam"
        const val FRIEND_NAME = "Sam"
        const val PUZZLE_NUMBER = 592
        val DUEL_CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        val PLAY_BADGE = "${topLevelDestinations.single { it.key == PlayKey }.tag}.badge"
    }
}
