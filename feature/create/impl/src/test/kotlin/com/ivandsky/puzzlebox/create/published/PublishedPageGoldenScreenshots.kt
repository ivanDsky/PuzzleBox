package com.ivandsky.puzzlebox.create.published

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.settleAndCapture
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * A published puzzle's page, phone (single pane) and tablet (two panes), light and dark: live
 * with its ticket and Unpublish…, the unpublish confirm dialog, the dialog's offline line after a
 * failed confirm, and the unpublished record with its code struck. The page runs on its real
 * view model over fake stores; the board is the plain stand-in of [PublishedPlainWorld]. Goldens in `snapshots/published/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class PublishedPageGoldenScreenshotsPhone : PublishedPageGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class PublishedPageGoldenScreenshotsTablet : PublishedPageGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class PublishedPageGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val world = PublishedPlainWorld(PlainGame.QUEENS)

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test fun `page light`() = capture("page", dark = false)
    @Test fun `page dark`() = capture("page", dark = true)

    @Test fun `unpublish dialog light`() = capture("unpublish-dialog", dark = false) { ask() }
    @Test fun `unpublish dialog dark`() = capture("unpublish-dialog", dark = true) { ask() }

    @Test fun `unpublish dialog offline light`() = capture("unpublish-offline", dark = false) { askOffline() }
    @Test fun `unpublish dialog offline dark`() = capture("unpublish-offline", dark = true) { askOffline() }

    @Test fun `unpublished record light`() = capture("page-unpublished", dark = false, unpublished = true)
    @Test fun `unpublished record dark`() = capture("page-unpublished", dark = true, unpublished = true)

    private fun capture(name: String, dark: Boolean, unpublished: Boolean = false, then: () -> Unit = {}) {
        world.start(unpublished)
        rule.showGolden(dark, frozen = false) { PublishedPuzzleRoute(world.key, layout, onBack = {}, onOpen = {}) }
        awaitTag("create.published.ticket")
        awaitTag(PLAIN_CONTENT)
        then()
        rule.settleAndCapture("published", name, device, dark)
    }

    private fun ask() {
        rule.onNodeWithTag("create.publishedPage.unpublish").performScrollTo().performClick()
        awaitTag("create.unpublish.dialog")
    }

    private fun askOffline() {
        ask()
        world.shared.online = false
        rule.onNodeWithTag("create.unpublish.confirm").performClick()
        awaitTag("create.unpublish.offline")
    }

    private fun awaitTag(tag: String) = rule.waitUntil(TIMEOUT_MS) {
        ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
    }
}
