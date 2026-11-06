package com.ivandsky.puzzlebox.create

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 29: the Create root's states (spec S15, A3), phone and tablet, light and dark: a few
 * drafts with each background verdict, the first-time empty state, long titles, and more
 * than 8 drafts with the search field and game filters. Rendered from fake [CreateUiState].
 * Recorded by default, verified with `-Proborazzi.verify=true`; goldens in `snapshots/create/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class CreateRootGoldenScreenshotsPhone : CreateRootGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class CreateRootGoldenScreenshotsTablet : CreateRootGoldenScreenshots("tablet")

abstract class CreateRootGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `default light`() = capture("default", DEFAULT, dark = false)
    @Test fun `default dark`() = capture("default", DEFAULT, dark = true)

    @Test fun `empty light`() = capture("empty", EMPTY, dark = false)
    @Test fun `empty dark`() = capture("empty", EMPTY, dark = true)

    @Test fun `long titles light`() = capture("long-titles", LONG_TITLES, dark = false)
    @Test fun `long titles dark`() = capture("long-titles", LONG_TITLES, dark = true)

    @Test fun `more than 8 with search light`() = capture("search", SEARCH, dark = false)
    @Test fun `more than 8 with search dark`() = capture("search", SEARCH, dark = true)

    private fun capture(name: String, state: CreateUiState, dark: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                // The app's scene canvas: an opaque background behind every screen.
                PuzzleBoxTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        CreateScreen(
                            state = state,
                            onNew = { _, _ -> },
                            onOpen = {},
                            onQuery = {},
                            onFilter = {},
                            onDuplicate = {},
                            onDelete = {},
                            onUndoDelete = {},
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.onRoot().captureRoboImage(out(name, dark))
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/create/$name-$device-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        const val SETTLE_MS = 2_000L

        val SOLUTION = Solution(listOf(1, 9, 12, 22, 26, 35))

        /** Each row's background verdict in turn; null is a draft this build cannot open. */
        val VERDICTS: List<Verdict?> = listOf(
            Verdict.Unique(SOLUTION),
            Verdict.Checking,
            Verdict.Incomplete(IncompleteReason("queens.unpainted", 12)),
            Verdict.Multiple(SOLUTION, Solution(listOf(1, 9, 12, 23, 26, 34)), setOf(22, 23, 34, 35)),
            Verdict.None(setOf(0, 5)),
            Verdict.Undetermined,
            null,
        )

        fun rows(count: Int, title: (Int) -> String = { "Untitled ${count - it}" }): List<CreateUiState.DraftRow> =
            (0 until count).map { index ->
                val game = PreviewGames[index % PreviewGames.size]
                CreateUiState.DraftRow(
                    id = "d$index",
                    gameId = game.id,
                    brand = game.brand,
                    title = title(index),
                    meta = "${game.name} · ${game.defaultSize.label}",
                    edited = "edited ${index + 1} h ago",
                    verdict = VERDICTS[index % VERDICTS.size],
                )
            }

        fun listed(rows: List<CreateUiState.DraftRow>, search: Boolean = false) = CreateUiState(
            loading = false,
            drafts = rows,
            draftCount = rows.size,
            games = PreviewGames,
            searchVisible = search,
            filters = if (search) PreviewGames.map { game -> CreateUiState.GameFilter(game.id, game.name, game.brand, rows.count { it.gameId == game.id }) } else emptyList(),
        )

        val DEFAULT = listed(rows(4))
        val EMPTY = CreateUiState(loading = false, games = PreviewGames)
        val LONG_TITLES = listed(
            rows(3) { index ->
                listOf(
                    "The longest Sunday crossword-sized Queens board anyone has drawn",
                    "Zip warm-up for the commute, version two, now with fewer dead ends",
                    "Tango",
                )[index]
            },
        )
        val SEARCH = listed(rows(12), search = true)
    }
}
