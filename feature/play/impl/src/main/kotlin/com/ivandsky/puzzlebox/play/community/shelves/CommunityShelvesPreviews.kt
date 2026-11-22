package com.ivandsky.puzzlebox.play.community.shelves

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme

/** The shelves home on a phone and in the tablet's list pane, light and dark. */
@Preview(name = "Phone light", widthDp = 360, heightDp = 1500, showBackground = true)
@Preview(name = "Phone dark", widthDp = 360, heightDp = 1500, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Tablet light", widthDp = 600, heightDp = 1500, showBackground = true)
@Preview(name = "Tablet dark", widthDp = 600, heightDp = 1500, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
internal annotation class ShelvesPreviews

@Composable
private fun ShelvesPreviewFrame(state: ShelvesUiState) {
    PuzzleBoxTheme {
        Surface(Modifier.fillMaxSize()) {
            CommunityShelvesScreen(
                state = state,
                actions = ShelvesActions(onEnterCode = {}, onOpen = {}, onRetry = {}),
                phone = LocalConfiguration.current.screenWidthDp < TABLET_WIDTH_DP,
            )
        }
    }
}

private const val TABLET_WIDTH_DP = 600

@ShelvesPreviews
@Composable
internal fun ShelvesRealPreview() = ShelvesPreviewFrame(sampleShelves(count = 30))

@ShelvesPreviews
@Composable
internal fun ShelvesFewPreview() = ShelvesPreviewFrame(sampleShelves(count = 8))

/** What it will look like once puzzles carry a solve count: the Spotlight and Most played show. */
@ShelvesPreviews
@Composable
internal fun ShelvesCountedPreview() = ShelvesPreviewFrame(sampleShelves(count = 30, counted = true))

@ShelvesPreviews
@Composable
internal fun ShelvesLongestPreview() = ShelvesPreviewFrame(sampleShelves(count = 30, counted = true, longest = true))

@ShelvesPreviews
@Composable
internal fun ShelvesEmptyPreview() = ShelvesPreviewFrame(ShelvesUiState.Empty(SampleEntries))

@ShelvesPreviews
@Composable
internal fun ShelvesLoadingPreview() = ShelvesPreviewFrame(ShelvesUiState.Loading(SampleEntries))

@ShelvesPreviews
@Composable
internal fun ShelvesOfflinePreview() = ShelvesPreviewFrame(ShelvesUiState.Offline(SampleEntries))

@ShelvesPreviews
@Composable
internal fun ShelvesRefusedPreview() = ShelvesPreviewFrame(ShelvesUiState.Refused(SampleEntries))
