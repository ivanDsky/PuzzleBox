package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// The Motion section of [TokenCatalogScreen] and its demo widgets, extracted from
// TokenCatalog.kt (catalog-mixing finding): the pattern cards and spring tracks are
// stateful, tap-driven showcases — different in kind from the read-only token listings
// that file renders. The Easing display-name mapping stays private to these demos.

/** The Motion catalog section: screen-pattern specs plus tappable spring tracks. */
@Composable
internal fun MotionCatalogSection() {
    CatalogSection(title = "Motion") {
        Text(
            text = "Screen patterns",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        listOf(SharedAxisTransition, ContainerTransform).forEach { pattern ->
            MotionPatternCard(pattern)
        }
        Text(
            text = "Springs — tap a track",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SpringDemo(
            label = "spring · default (critically damped)",
            dampingRatio = MotionTokens.SpringDampingDefault,
            stiffness = MotionTokens.SpringStiffnessDefault,
        )
        SpringDemo(
            label = "spring · momentum (damping 0.8)",
            dampingRatio = MotionTokens.SpringDampingMomentum,
            stiffness = MotionTokens.SpringStiffnessDefault,
        )
    }
}

/** Spec card for one named [MotionPattern]: description plus enter/exit timing. */
@Composable
private fun MotionPatternCard(pattern: MotionPattern) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = pattern.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = pattern.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "enter ${pattern.enterDurationMillis}ms · ${pattern.enterEasing.displayName()}   |   exit ${pattern.exitDurationMillis}ms · ${pattern.exitEasing.displayName()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** Tap-to-toggle spring track: the knob travels edge-to-edge on the given spring. */
@Composable
private fun SpringDemo(label: String, dampingRatio: Float, stiffness: Float) {
    var atEnd by remember { mutableStateOf(false) }
    val knobSize = 24.dp
    val edgePadding = Spacing.sm
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // BoxWithConstraints so the travel derives from the measured track: the knob
        // rides edge-to-edge minus knob size + edge padding at any pane width.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { atEnd = !atEnd },
        ) {
            val x by animateDpAsState(
                targetValue = if (atEnd) maxWidth - knobSize - edgePadding * 2 else 0.dp,
                animationSpec = spring(dampingRatio = dampingRatio, stiffness = stiffness),
                label = "spring-demo",
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = x + edgePadding)
                    .size(knobSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Token identity → human name; the easings are singletons, so `when` compares exactly. */
private fun Easing.displayName(): String =
    when (this) {
        MotionTokens.EasingStandard -> "emphasized"
        MotionTokens.EasingEmphasizedDecelerate -> "emphasized decelerate"
        MotionTokens.EasingEmphasizedAccelerate -> "emphasized accelerate"
        else -> "linear"
    }
