package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

// Renders the complete token set (A12): color, type, shape, spacing, motion.
// It draws whatever theme wraps it, so dark and light both come from the same tree.
@Composable
fun TokenCatalogScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "Design system",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Every color, type style, corner, gap, and motion the app is built from — one token set, dark and light.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CatalogSection(title = "Color") {
            colorPairSpecs().forEach { pair ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp, 56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(pair.container),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.titleMedium,
                            color = pair.on,
                        )
                    }
                    Column {
                        Text(
                            text = pair.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "on: ${pair.onName} · ${pair.on.toHex6()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = pair.container.toHex6(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            ColorTierRow()
        }

        CatalogSection(title = "Typography") {
            typographySamples().forEach { (name, style) ->
                Column {
                    Text(
                        text = "$name · ${style.fontSize.value.toInt()}sp / ${style.lineHeight.value.toInt()}sp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Puzzle 12",
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        CatalogSection(title = "Shape") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                listOf(
                    "extraSmall" to MaterialTheme.shapes.extraSmall,
                    "small" to MaterialTheme.shapes.small,
                    "medium" to MaterialTheme.shapes.medium,
                    "large" to MaterialTheme.shapes.large,
                    "extraLarge" to MaterialTheme.shapes.extraLarge,
                ).forEach { (name, shape) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        CatalogSection(title = "Spacing") {
            listOf(
                "xs" to Spacing.xs,
                "sm" to Spacing.sm,
                "md" to Spacing.md,
                "lg" to Spacing.lg,
                "xl" to Spacing.xl,
                "xxl" to Spacing.xxl,
            ).forEach { (name, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        modifier = Modifier
                            .width(value)
                            .height(Spacing.lg)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = "$name · ${value.value.toInt()}dp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

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

        ComponentCatalogSections()
    }
}

@Composable
internal fun CatalogSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

private data class ColorPairSpec(val name: String, val onName: String, val container: Color, val on: Color)

@Composable
private fun colorPairSpecs(): List<ColorPairSpec> {
    val s = MaterialTheme.colorScheme
    return listOf(
        ColorPairSpec("primary", "onPrimary", s.primary, s.onPrimary),
        ColorPairSpec("primaryContainer", "onPrimaryContainer", s.primaryContainer, s.onPrimaryContainer),
        ColorPairSpec("secondary", "onSecondary", s.secondary, s.onSecondary),
        ColorPairSpec("secondaryContainer", "onSecondaryContainer", s.secondaryContainer, s.onSecondaryContainer),
        ColorPairSpec("tertiary", "onTertiary", s.tertiary, s.onTertiary),
        ColorPairSpec("tertiaryContainer", "onTertiaryContainer", s.tertiaryContainer, s.onTertiaryContainer),
        ColorPairSpec("error", "onError", s.error, s.onError),
        ColorPairSpec("errorContainer", "onErrorContainer", s.errorContainer, s.onErrorContainer),
        ColorPairSpec("surface", "onSurface", s.surface, s.onSurface),
        ColorPairSpec("surfaceVariant", "onSurfaceVariant", s.surfaceVariant, s.onSurfaceVariant),
        ColorPairSpec("inverseSurface", "inverseOnSurface", s.inverseSurface, s.inverseOnSurface),
    )
}

@Composable
private fun ColorTierRow() {
    val s = MaterialTheme.colorScheme
    val tiers = listOf(
        "surfaceDim" to s.surfaceDim,
        "surfaceBright" to s.surfaceBright,
        "surfaceContainerLowest" to s.surfaceContainerLowest,
        "surfaceContainerLow" to s.surfaceContainerLow,
        "surfaceContainer" to s.surfaceContainer,
        "surfaceContainerHigh" to s.surfaceContainerHigh,
        "surfaceContainerHighest" to s.surfaceContainerHighest,
        "outline" to s.outline,
        "outlineVariant" to s.outlineVariant,
        "inversePrimary" to s.inversePrimary,
        "scrim" to s.scrim,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        tiers.forEach { (name, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp, 24.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
                )
                Text(
                    text = "$name · ${color.toHex6()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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

private fun Color.toHex6(): String = "#%06X".format(toArgb() and 0xFFFFFF)

private fun Easing.displayName(): String =
    when (this) {
        MotionTokens.EasingStandard -> "emphasized"
        MotionTokens.EasingEmphasizedDecelerate -> "emphasized decelerate"
        MotionTokens.EasingEmphasizedAccelerate -> "emphasized accelerate"
        else -> "linear"
    }

@Composable
private fun typographySamples(): List<Pair<String, TextStyle>> {
    val t = MaterialTheme.typography
    return listOf(
        "displayLarge" to t.displayLarge,
        "displayMedium" to t.displayMedium,
        "displaySmall" to t.displaySmall,
        "headlineLarge" to t.headlineLarge,
        "headlineMedium" to t.headlineMedium,
        "headlineSmall" to t.headlineSmall,
        "titleLarge" to t.titleLarge,
        "titleMedium" to t.titleMedium,
        "titleSmall" to t.titleSmall,
        "bodyLarge" to t.bodyLarge,
        "bodyMedium" to t.bodyMedium,
        "bodySmall" to t.bodySmall,
        "labelLarge" to t.labelLarge,
        "labelMedium" to t.labelMedium,
        "labelSmall" to t.labelSmall,
    )
}
