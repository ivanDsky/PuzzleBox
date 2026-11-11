package com.ivandsky.puzzlebox.compete.tab

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The invite row at phone width: Accept and Decline read as an even pair under the text, and the title gets the width. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class InvitedRowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val row = DuelRow(
        code = checkNotNull(ShareCode.parse("JON333")),
        opponent = Player(uid = "uid-jonas", name = "Jonas", photoUrl = null),
        kind = DuelRowKind.Invited,
        legsDone = 0,
        theirDone = 0,
        legs = 3,
        deadline = Instant.parse("2026-11-10T12:00:00Z"),
    )

    private fun show(inFlight: Boolean = false, onAccept: () -> Unit = {}, onDecline: () -> Unit = {}) {
        rule.setContent {
            PuzzleBoxTheme {
                InvitedRow(
                    row = row,
                    inFlight = inFlight,
                    note = null,
                    onAccept = onAccept,
                    onDecline = onDecline,
                    onClick = {},
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
    }

    private fun SemanticsNodeInteraction.isEllipsized(): Boolean {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single().isLineEllipsized(0)
    }

    @Test
    fun acceptAndDeclineAreAnEqualWidthPairSideBySide() {
        show()

        val accept = rule.onNodeWithTag("duel.invited.accept").getBoundsInRoot()
        val decline = rule.onNodeWithTag("duel.invited.decline").getBoundsInRoot()

        assertEquals("equal width", accept.width.value, decline.width.value, 0.5f)
        assertEquals("equal height", accept.height.value, decline.height.value, 0.5f)
        assertEquals("one line", accept.top.value, decline.top.value, 0.5f)
        assertTrue("Accept first, Decline to its right", decline.left >= accept.right)
    }

    @Test
    fun thePairSitsUnderTheTextColumn() {
        show()

        val title = rule.onNodeWithText("Jonas challenged you", useUnmergedTree = true).getBoundsInRoot()
        val sub = rule.onNodeWithText("Best of 3", useUnmergedTree = true).getBoundsInRoot()
        val accept = rule.onNodeWithTag("duel.invited.accept").getBoundsInRoot()

        assertEquals("aligned with the text column", title.left.value, accept.left.value, 0.5f)
        assertTrue("below the sub line", accept.top >= sub.bottom)
    }

    @Test
    fun theTitleIsNotTruncatedAtPhoneWidth() {
        show()

        assertFalse(rule.onNodeWithText("Jonas challenged you", useUnmergedTree = true).isEllipsized())
    }

    @Test
    fun theButtonsCallBack() {
        var accepted = 0
        var declined = 0
        show(onAccept = { accepted++ }, onDecline = { declined++ })
        rule.onNodeWithTag("duel.invited.accept").performClick()
        rule.onNodeWithTag("duel.invited.decline").performClick()
        assertEquals(1, accepted)
        assertEquals(1, declined)
    }

    @Test
    fun bothButtonsAreDisabledWhileACallIsInFlight() {
        show(inFlight = true)

        rule.onNodeWithTag("duel.invited.accept").assertIsNotEnabled()
        rule.onNodeWithTag("duel.invited.decline").assertIsNotEnabled()
    }

    @Test
    fun bothButtonsAreEnabledOtherwise() {
        show()

        rule.onNodeWithTag("duel.invited.accept").assertIsEnabled()
        rule.onNodeWithTag("duel.invited.decline").assertIsEnabled()
    }
}
