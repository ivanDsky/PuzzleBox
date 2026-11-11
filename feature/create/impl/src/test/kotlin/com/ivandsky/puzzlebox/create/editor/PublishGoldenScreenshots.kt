package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.settleAndCapture
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.domain.PublishOutcome
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Studio's publish states after a passed test, phone and tablet, light and dark: the details
 * (in the tools' slot, on the tablet in the side pane's) signed in and signed out, the sign-in
 * sheet, the Publishing spinner pill, Offline, Rejected and the daily cap with their banners
 * and Retry publish, then the share ticket once published and the struck read-only record once
 * unpublished. The board is the Studio preview's placeholder square. Goldens in `snapshots/publish/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class PublishGoldenScreenshotsPhone : PublishGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class PublishGoldenScreenshotsTablet : PublishGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class PublishGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `details light`() = capture("details", PublishSession.State(details = DETAILS), dark = false)
    @Test fun `details dark`() = capture("details", PublishSession.State(details = DETAILS), dark = true)

    @Test fun `signed out light`() = capture("signed-out", SIGNED_OUT, dark = false)
    @Test fun `signed out dark`() = capture("signed-out", SIGNED_OUT, dark = true)

    @Test fun `sign-in sheet light`() = capture("sign-in-sheet", SIGNED_OUT.copy(signIn = PublishSession.SignIn()), dark = false)
    @Test fun `sign-in sheet dark`() = capture("sign-in-sheet", SIGNED_OUT.copy(signIn = PublishSession.SignIn()), dark = true)

    @Test fun `publishing light`() = capture("publishing", PublishSession.State(busy = true, details = DETAILS), dark = false)
    @Test fun `publishing dark`() = capture("publishing", PublishSession.State(busy = true, details = DETAILS), dark = true)

    @Test fun `offline light`() = capture("offline", outcome(PublishOutcome.Offline), dark = false)
    @Test fun `offline dark`() = capture("offline", outcome(PublishOutcome.Offline), dark = true)

    @Test fun `rejected light`() = capture("rejected", outcome(PublishOutcome.Rejected(null)), dark = false)
    @Test fun `rejected dark`() = capture("rejected", outcome(PublishOutcome.Rejected(null)), dark = true)

    @Test fun `daily cap light`() = capture("daily-cap", outcome(PublishOutcome.DailyCapReached), dark = false)
    @Test fun `daily cap dark`() = capture("daily-cap", outcome(PublishOutcome.DailyCapReached), dark = true)

    @Test fun `ticket light`() = capture("ticket", PUBLISHED, dark = false, ticket = TICKET)
    @Test fun `ticket dark`() = capture("ticket", PUBLISHED, dark = true, ticket = TICKET)

    @Test fun `unpublished record light`() = capture("unpublished", PUBLISHED, dark = false, ticket = TICKET.copy(unpublished = true))
    @Test fun `unpublished record dark`() = capture("unpublished", PUBLISHED, dark = true, ticket = TICKET.copy(unpublished = true))

    private fun capture(name: String, publish: PublishSession.State, dark: Boolean, ticket: ShareTicket? = null) {
        rule.showGolden(dark) {
            Studio(publish, ticket)
            SignInPublishSheet(signIn = publish.signIn, onGoogle = {}, onCancel = {})
        }
        rule.settleAndCapture("publish", name, device, dark)
    }

    @Composable
    private fun Studio(publish: PublishSession.State, ticket: ShareTicket?) {
        StudioScreen(
            title = TITLE,
            subtitle = "Queens 6×6",
            saving = false,
            verdict = UNIQUE,
            layout = layout,
            onBack = {},
            onRename = {},
            onRollTitle = { "Amber Falcon" },
            onDuplicate = {},
            onDelete = {},
            board = { PlaceholderBoard(it) },
            toolStrip = { slotModifier ->
                ToolSlot(
                    open = detailsOpen(UNIQUE, PASSED, publish),
                    editBoard = false,
                    onEditBoard = {},
                    tools = { Tools(it) },
                    form = { formModifier, editBoardChip ->
                        PublishDetails(
                            title = TITLE,
                            details = publish.details,
                            signedIn = publish.signedIn,
                            onRename = {},
                            onRollTitle = { "Amber Falcon" },
                            onAuthorName = {},
                            onNote = {},
                            modifier = formModifier,
                            enabled = !publish.busy,
                            paired = true,
                            action = editBoardChip,
                            history = { UndoRedoButtons(canUndo = true, canRedo = false, onUndo = {}, onRedo = {}) },
                        )
                    },
                    modifier = slotModifier,
                )
            },
            actionArea = { areaModifier ->
                TicketMorph(ticket = ticket, onCopied = {}, modifier = areaModifier) { actionsModifier ->
                    ActionArea(
                        passed = PASSED,
                        canTestSolve = true,
                        onTestSolve = {},
                        blocks = emptyList(),
                        modifier = actionsModifier,
                        publish = publish,
                        title = TITLE,
                    )
                }
            },
            readOnly = ticket != null,
            unpublished = ticket?.unpublished == true,
            onUnpublish = {}.takeIf { ticket?.unpublished == false },
        )
    }

    @Composable
    private fun Tools(modifier: Modifier) {
        ToolStrip(
            canUndo = true,
            canRedo = false,
            onUndo = {},
            onRedo = {},
            sizes = listOf(SIZE, VariantId("queens/7")),
            size = SIZE,
            sizeLabel = { "6×6" },
            onResize = {},
            onClear = {},
            hint = { Text("Drag across cells, then pick a colour", it, style = MaterialTheme.typography.bodySmall) },
            modifier = modifier,
        )
    }

    @Composable
    private fun PlaceholderBoard(modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true).background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.small))
        }
    }

    private companion object {
        const val TITLE = "Sunday Queens"

        val SIZE = VariantId("queens/6")
        val UNIQUE = Verdict.Unique(Solution(listOf(1, 9, 12, 22, 26, 35)))
        val PASSED = TestSolve(layoutKey = "golden", timeMs = 102_000, hints = 0)
        val DETAILS = PublishSession.Details(authorName = "Ivan", note = "Start from the corner.")
        val SIGNED_OUT = PublishSession.State(signedIn = false)
        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        val PUBLISHED = PublishSession.State(outcome = PublishOutcome.Published(CODE), details = DETAILS)
        val TICKET = ShareTicket(CODE, TITLE, "Ivan", PASSED.timeMs, "Queens", AccentSlot.Violet, fresh = true)

        fun outcome(outcome: PublishOutcome) = PublishSession.State(outcome = outcome, details = DETAILS)
    }
}
