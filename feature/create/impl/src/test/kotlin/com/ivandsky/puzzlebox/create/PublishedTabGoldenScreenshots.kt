package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Create's Published tab, phone and tablet, light and dark: published rows with their solves
 * counts (not fetched yet, none, one, many) and an unpublished row, and the tab with nothing
 * published while drafts exist. Rendered from fake [CreateUiState]. Goldens in `snapshots/published/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class PublishedTabGoldenScreenshotsPhone : PublishedTabGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class PublishedTabGoldenScreenshotsTablet : PublishedTabGoldenScreenshots("tablet")

abstract class PublishedTabGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `tab with solves light`() = capture("tab", LISTED, dark = false)
    @Test fun `tab with solves dark`() = capture("tab", LISTED, dark = true)

    @Test fun `tab empty light`() = capture("tab-empty", EMPTY, dark = false)
    @Test fun `tab empty dark`() = capture("tab-empty", EMPTY, dark = true)

    private fun capture(name: String, state: CreateUiState, dark: Boolean) {
        rule.showGolden(dark) {
            CreateScreen(
                state = state,
                onNew = { _, _ -> },
                onOpen = {},
                onQuery = {},
                onFilter = {},
                onTab = {},
                onOpenPublished = {},
                onDuplicate = {},
                onDelete = {},
                onUndoDelete = {},
            )
        }
        rule.settleAndCapture("published", name, device, dark)
    }

    private companion object {
        val DRAFT = CreateUiState.DraftRow(
            id = "d0",
            gameId = PreviewGames[0].id,
            brand = PreviewGames[0].brand,
            title = "Untitled 2",
            meta = "${PreviewGames[0].name} · ${PreviewGames[0].defaultSize.label}",
            edited = "edited 1 h ago",
            verdict = Verdict.Unique(Solution(listOf(1, 9, 12, 22, 26, 35))),
        )

        fun row(code: String, title: String, game: Int, solves: Int?, published: String, unpublished: Boolean = false) =
            CreateUiState.PublishedRow(
                code = checkNotNull(ShareCode.parse(code)),
                gameId = PreviewGames[game].id,
                brand = PreviewGames[game].brand,
                title = title,
                meta = if (unpublished) "Unpublished" else "Author 1:42",
                published = published,
                unpublished = unpublished,
                solves = solves,
            )

        val ROWS = listOf(
            row("K7Q2MX", "Sunday Queens", 0, solves = null, published = "published just now"),
            row("B4R9TZ", "The longest Sunday crossword-sized board anyone has drawn", 0, solves = 0, published = "published 2 h ago"),
            row("M3N8PQ", "Commute warm-up", 1 % PreviewGames.size, solves = 1, published = "published yesterday"),
            row("H2J5WX", "Castle walls", 0, solves = 128, published = "published 3 d ago"),
            row("Z9Y8XW", "Old one", 0, solves = 4, published = "published 2 wk ago", unpublished = true),
        )

        val LISTED = CreateUiState(
            loading = false,
            drafts = listOf(DRAFT),
            draftCount = 1,
            games = PreviewGames,
            tab = CreateUiState.Tab.Published,
            published = ROWS,
        )
        val EMPTY = LISTED.copy(published = emptyList())
    }
}
