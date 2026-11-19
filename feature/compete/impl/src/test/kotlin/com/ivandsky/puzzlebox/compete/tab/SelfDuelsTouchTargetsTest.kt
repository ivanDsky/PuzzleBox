package com.ivandsky.puzzlebox.compete.tab

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.compete.assertTouchTargetsAtLeast48
import com.ivandsky.puzzlebox.compete.domain.DuelRow
import com.ivandsky.puzzlebox.compete.domain.DuelRowKind
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Duels tab with Past you duels listed (signed in and out): every row and the challenge card are 48 dp touch targets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SelfDuelsTouchTargetsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-11-09T12:00:00Z")

    private fun self(code: String, kind: DuelRowKind, done: Int, legs: Int) = DuelRow(
        code = checkNotNull(ShareCode.parse(code)),
        opponent = Player(uid = SelfSeats.PAST, name = SelfSeats.PAST_NAME, photoUrl = null),
        kind = kind,
        legsDone = done,
        theirDone = legs,
        legs = legs,
        deadline = Instant.MAX,
        isSelf = true,
    )

    private val rows = listOf(
        self("SELF11", DuelRowKind.YourMove, 1, 3),
        self("SELF22", DuelRowKind.RevealReady, 3, 3),
        self("SELF44", DuelRowKind.Finished(2, 1), 3, 3),
    )

    private fun show(signedIn: Boolean) {
        rule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.fillMaxSize()) {
                    DuelsTabContent(
                        rows = rows,
                        signedIn = signedIn,
                        selfName = "Ivan",
                        inviter = if (signedIn) "Ivan" else null,
                        now = { now },
                        onNewDuel = {},
                        onOpenDuel = {},
                        onOpenReveal = {},
                        selected = null,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `signed in, every self duel row and the challenge card is a 48 dp touch target`() {
        show(signedIn = true)

        rule.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `signed out, every self duel row and the challenge card is a 48 dp touch target`() {
        show(signedIn = false)

        rule.assertTouchTargetsAtLeast48()
    }
}
