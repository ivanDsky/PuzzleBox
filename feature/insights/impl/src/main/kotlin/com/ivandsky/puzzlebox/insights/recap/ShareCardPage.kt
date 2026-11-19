package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.share.RecapShareContent
import com.ivandsky.puzzlebox.insights.share.RecapShareImage
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import com.ivandsky.puzzlebox.insights.share.ScaledToFit
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The story's last page: a preview of the image the recap shares and the Share button. The preview is the very
 * composable that is captured when Share is tapped, so what the player sees is what leaves the app. [onShare] gets that
 * capture and the content it shows. Share is off while the history is still coming back (A23): the image would show a
 * partial period.
 */
@Composable
internal fun ShareCardPage(
    cards: List<RecapCard>,
    state: RecapShareState,
    onShare: (ImageBitmap, RecapShareContent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = remember(cards, state.title, state.displayName) { RecapShareContent.of(state.title, state.displayName, cards) }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .testTag("recap.share"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScaledToFit(Modifier.clip(PreviewShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewShape)) {
                RecapShareImage(
                    content,
                    Modifier.drawWithContent {
                        layer.record(size = IntSize(size.width.roundToInt(), size.height.roundToInt())) { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
                )
            }
        }
        state.notice()?.let { notice ->
            Text(
                text = notice,
                modifier = Modifier.testTag("recap.share.notice"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        PrimaryButton(
            text = "Share",
            onClick = { scope.launch { onShare(layer.toImageBitmap(), content) } },
            modifier = Modifier.fillMaxWidth().testTag("recap.share.button"),
            enabled = state.canShare,
            loading = state.sharing,
        )
    }
}

private fun RecapShareState.notice(): String? = when {
    restoring -> "Sharing is off while your history comes back."
    failed -> "Couldn't prepare the image. Try again."
    else -> null
}

/** The preview's frame: the image has the page's own background, so the frame is what shows where it ends. */
private val PreviewShape = RoundedCornerShape(12.dp)
