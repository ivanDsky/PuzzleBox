package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The tutorial's frame ([TutorialHost]) as a play screen: one column at most
 * [PlayBoardMaxWidth] wide, centred; a header row where the tutorial has its step dots and
 * Skip (title and subtitle, the timer, ⓘ), the degrade banner, then the board and the
 * caption slot as one group centred in the height left. For a game that draws its undo
 * and hint controls by its board, so no side pane is needed. [board] is sized to its
 * content within the space left under the header, less the caption slot.
 */
@Composable
fun PlayColumn(
    title: String,
    subtitle: String,
    clock: PlayClock?,
    caption: String?,
    banner: String?,
    modifier: Modifier = Modifier,
    onHowToPlay: (() -> Unit)? = null,
    board: @Composable BoxScope.() -> Unit,
) {
    if (clock != null) PlayClockEffects(clock)

    Box(modifier.fillMaxSize().testTag("play.column"), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = PlayBoardMaxWidth)
                .fillMaxSize()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().testTag("play.header"), verticalAlignment = Alignment.CenterVertically) {
                PlayTitle(title, subtitle, Modifier.weight(1f))
                if (clock != null) {
                    Text(
                        text = formatClock(clock.displayedElapsedMs()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (onHowToPlay != null) HowToPlayButton(onHowToPlay)
            }
            banner?.let { DegradeBanner(it) }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                    content = board,
                )
                PlayCaptionSlot(caption = caption, textAlign = TextAlign.Center)
            }
        }
    }
}
