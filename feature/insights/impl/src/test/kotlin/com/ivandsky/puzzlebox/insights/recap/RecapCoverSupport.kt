package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput

/** Taps the story forward off its cover, for a test that wants the first card. */
internal fun ComposeTestRule.tapPastCover() {
    onNodeWithTag("recap.pager").performTouchInput { click(Offset(width * 0.8f, height / 2f)) }
    waitForIdle()
}

/** Whether [this] opens on the cover: a fresh open (saved page 0) of a story with cards. */
internal val RecapUiState.opensOnCover: Boolean get() = this is RecapUiState.Ready && initialPage == 0
