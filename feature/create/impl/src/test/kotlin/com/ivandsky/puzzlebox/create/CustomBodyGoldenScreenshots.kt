package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.designsystem.Spacing
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
 * The Custom list on a phone, light and dark: New puzzle over Continue (two drafts), a stand-in for the caller's
 * friends section, and Published by you (two puzzles). Rendered from fake [CreateUiState]. Goldens in
 * `snapshots/custom/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class CustomBodyGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `list light`() = capture(dark = false)
    @Test fun `list dark`() = capture(dark = true)

    private fun capture(dark: Boolean) {
        rule.showGolden(dark) {
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
        rule.settleAndCapture("custom", "body", "phone", dark)
    }

    private companion object {
        fun draft(index: Int, title: String, verdict: Verdict) = CreateUiState.DraftRow(
            id = "d$index",
            gameId = PreviewGames[index].id,
            brand = PreviewGames[index].brand,
            title = title,
            meta = "${PreviewGames[index].name} · ${PreviewGames[index].defaultSize.label}",
            edited = "edited ${index + 1} h ago",
            verdict = verdict,
        )

        fun published(code: String, title: String, game: Int, solves: Int, age: String) = CreateUiState.PublishedRow(
            code = checkNotNull(ShareCode.parse(code)),
            gameId = PreviewGames[game].id,
            brand = PreviewGames[game].brand,
            title = title,
            meta = "Author 1:42",
            published = age,
            solves = solves,
        )

        val DRAFTS = listOf(
            draft(0, "Corner trap", Verdict.Unique(Solution(listOf(1, 9, 12, 22, 26, 35)))),
            draft(1, "Untitled 1", Verdict.Undetermined),
        )

        val LISTED = CreateUiState(
            loading = false,
            drafts = DRAFTS,
            draftCount = DRAFTS.size,
            games = PreviewGames,
            published = listOf(
                published("K7Q2MX", "Sunday special", game = 0, solves = 12, age = "published 2 d ago"),
                published("M3P8RT", "Warm-up", game = 1, solves = 1, age = "published 5 d ago"),
            ),
        )
    }
}
