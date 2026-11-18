package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.play.community.CommunityActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Community from New today to Friends, a finger held 37 % and 62 % of the way, phone and tablet, light and dark: the
 * filter ⋮ fading out of the head with New today's page and the tablet's right pane with it, then Friends coming in.
 * Goldens in `snapshots/tab-fade/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TabFadeCommunityGoldenScreenshotsPhone : TabFadeCommunityGoldenScreenshots("phone") {
    override fun show(dark: Boolean) = rule.showGolden(dark, animated = true) {
        CommunityTabs(
            state = LOADED,
            actions = ACTIONS,
            friends = FRIENDS,
            custom = previewYoursSlot(3),
            onOpen = {},
            onRemove = {},
            onEnterCode = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class TabFadeCommunityGoldenScreenshotsTablet : TabFadeCommunityGoldenScreenshots("tablet") {
    override fun show(dark: Boolean) = rule.showGolden(dark, animated = true) {
        val summary = YoursSummary(count = 3, firstPublishedCode = null)
        CommunityPanes(
            default = { page -> defaultCommunityPick(page, LOADED.phase, FRIENDS, summary) },
            list = { size, picks ->
                CommunityTabs(
                    state = LOADED,
                    actions = ACTIONS,
                    friends = FRIENDS,
                    custom = previewYoursSlot(3),
                    onOpen = {},
                    onRemove = {},
                    onEnterCode = {},
                    modifier = size,
                    picks = picks,
                )
            },
            empty = { page -> CommunityEmptyPane(page, LOADED.phase) },
            detail = { pick, _ -> EmptyState("Puzzle ${pick.code}", "The page of the picked puzzle") },
        )
    }
}

abstract class TabFadeCommunityGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Shows Community on New today, in the golden theme, with animations on. */
    protected abstract fun show(dark: Boolean)

    @Test fun `37 light`() = capture("community-37", 0.37f, dark = false)
    @Test fun `37 dark`() = capture("community-37", 0.37f, dark = true)

    @Test fun `62 light`() = capture("community-62", 0.62f, dark = false)
    @Test fun `62 dark`() = capture("community-62", 0.62f, dark = true)

    private fun capture(name: String, fraction: Float, dark: Boolean) {
        show(dark)
        rule.holdSwipeAndCapture(fraction, name, device, dark)
    }

    internal companion object {
        val ACTIONS = CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {})
        val LOADED = CommunityPanesGoldenScreenshots.LOADED
        val FRIENDS = CommunityPanesGoldenScreenshots.FRIENDS
    }
}
