package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * Win celebration (F1 §3). Renders from normalized A3 facts only — time and hints — plus
 * an optional record badge. [isRecord] arrives stamped by core/data and may be null while
 * the write is still in flight: the overlay NEVER waits on the record write (F2 §7).
 */
@Composable
fun WinOverlay(
    visible: Boolean,
    timeMs: Long,
    hintsUsed: Int,
    onReplay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isRecord: Boolean? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                ),
            exit = fadeOut(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.padding(Spacing.xl),
            ) {
                Text(
                    text = "Solved!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isRecord == true) {
                    Text(
                        text = "New personal record",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = formatClock(timeMs),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (hintsUsed == 1) "1 hint" else "$hintsUsed hints",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(text = "Play again", onClick = onReplay)
                SecondaryButton(text = "Back", onClick = onClose)
            }
        }
    }
}
