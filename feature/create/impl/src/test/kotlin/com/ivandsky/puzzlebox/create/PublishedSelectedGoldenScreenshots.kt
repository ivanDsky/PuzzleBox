package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalSelectedRowCode
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Custom list as the Community tablet pane's 400 dp list column, light and dark, with one of your own published
 * puzzles picked ([LocalSelectedRowCode]): it is outlined like a picked friend or community row. Goldens in
 * `snapshots/custom/`. (The pane's wiring to the detail side is covered by the play module's tests.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class PublishedSelectedGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `own row selected light`() = capture(dark = false)
    @Test fun `own row selected dark`() = capture(dark = true)

    private fun capture(dark: Boolean) {
        rule.showGolden(dark) {
            CompositionLocalProvider(LocalSelectedRowCode provides "K7Q2MX") {
                Box(Modifier.fillMaxSize().padding(start = Spacing.xl, top = Spacing.lg, bottom = Spacing.xl)) {
                    Box(Modifier.width(400.dp)) {
                        CustomBodyContent(
                            state = LISTED,
                            listState = rememberLazyListState(),
                            onNewPuzzle = {},
                            onStart = { _, _ -> },
                            onImport = {},
                            onOpenDraft = {},
                            onDraftActions = {},
                            onOpenPublished = {},
                            onUndoDelete = {},
                        )
                    }
                }
            }
        }
        rule.settleAndCapture("custom", "published-selected", "tablet", dark)
    }

    private companion object {
        fun published(code: String, title: String, game: Int, solves: Int, age: String) = CreateUiState.PublishedRow(
            code = checkNotNull(ShareCode.parse(code)),
            gameId = PreviewGames[game].id,
            brand = PreviewGames[game].brand,
            title = title,
            meta = "Author 1:42",
            published = age,
            solves = solves,
        )

        val LISTED = CreateUiState(
            loading = false,
            games = PreviewGames,
            published = listOf(
                published("K7Q2MX", "Sunday special", game = 0, solves = 12, age = "published 2 d ago"),
                published("M3P8RT", "Warm-up", game = 1, solves = 1, age = "published 5 d ago"),
            ),
        )
    }
}
