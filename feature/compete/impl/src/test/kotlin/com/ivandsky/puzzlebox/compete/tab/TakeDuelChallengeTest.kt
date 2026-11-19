package com.ivandsky.puzzlebox.compete.tab

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ivandsky.puzzlebox.compete.create.DuelChallenge
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A pending challenge opens New duel once and is then let go, however often the screen recomposes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TakeDuelChallengeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val challenge = DuelChallenge(friendUid = "sam", friendName = "Sam", game = GameId("beta"))
    private val opened = mutableListOf<DuelChallenge>()
    private var pending by mutableStateOf<DuelChallenge?>(null)
    private var taken = 0

    private fun show() = rule.setContent {
        TakeDuelChallenge(pending, onTaken = { taken++; pending = null }, open = { opened += it })
    }

    @Test
    fun aPendingChallengeOpensOnceAndIsTaken() {
        pending = challenge
        show()
        rule.waitForIdle()

        assertEquals(listOf(challenge), opened)
        assertEquals(1, taken)
        assertEquals(null, pending)
    }

    @Test
    fun noChallengeOpensNothingAndTakesNothing() {
        show()
        rule.waitForIdle()

        assertEquals(emptyList<DuelChallenge>(), opened)
        assertEquals(0, taken)
    }

    @Test
    fun aChallengeThatArrivesLaterIsTakenOnceToo() {
        show()
        rule.runOnIdle { pending = challenge }
        rule.waitForIdle()
        rule.runOnIdle { pending = null }
        rule.waitForIdle()

        assertEquals(listOf(challenge), opened)
        assertEquals(1, taken)
    }
}
