package com.ivandsky.puzzlebox.designsystem

import android.content.res.Configuration
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The dialog scaffold: a card on surfaceContainerHigh, extraLarge corners, serif
 * title, body slot, end-aligned actions. Inline previews and the catalog render
 * [DialogCard] directly; only [AppDialog] adds the scrim, outside-dismiss, and the
 * entry animation.
 */
@Composable
fun DialogCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            content()
        }
    }
}

@Composable
fun AppDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    // Entry materializes from scale 0.95 + opacity 0 — never from scale(0); nothing
    // in the real world appears out of nothing. Critically damped, ~0.2s settle.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(
            1f,
            spring(
                dampingRatio = MotionTokens.SpringDampingDefault,
                stiffness = MotionTokens.SpringStiffnessDefault,
            ),
        )
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogCard(
            modifier = modifier
                .padding(horizontal = Spacing.xxl)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = beatScale(MotionTokens.EnterScaleFrom, appear.value)
                    scaleX = scale
                    scaleY = scale
                    alpha = appear.value
                },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

// Previews — Studio tooling only, zero public API surface (internal, not private, so
// Showkase's generated index can access them; ticket 00 visibility findings). Exactly
// light + dark, always inside PuzzleBoxTheme (spec A1/A5). A8: AppDialog's entry spring
// is composition-triggered, but it animates the card inside the platform Dialog window,
// which the Studio canvas does not render — per the pattern contract these previews
// compose the dialog content ([DialogCard]) instead, so the spring is not visible here;
// it runs in-app on every real dialog and in Studio interactive mode.

/** The bare scaffold: extraLarge card on surfaceContainerHigh with slot content. */
@ShowkaseComposable(name = "DialogCard", group = "Dialogs")
@PuzzleBoxPreviews
@Composable
internal fun DialogCardPreview() {
    PuzzleBoxTheme {
        DialogCard {
            Text(
                text = "Storage details",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Boards take 12.4 MB of local storage. Pieces are kept until you delete them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** AppDialog's content composition — the card layout it hosts, minus scrim and window. */
@ShowkaseComposable(name = "AppDialog", group = "Dialogs")
@PuzzleBoxPreviews
@Composable
internal fun AppDialogPreview() {
    PuzzleBoxTheme {
        DialogCard(
            modifier = Modifier
                .padding(horizontal = Spacing.xxl)
                .widthIn(max = 380.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = "Delete board?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "This removes the board and all of its pieces. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryButton(text = "Cancel", onClick = {})
                PrimaryButton(text = "Delete", onClick = {})
            }
        }
    }
}
