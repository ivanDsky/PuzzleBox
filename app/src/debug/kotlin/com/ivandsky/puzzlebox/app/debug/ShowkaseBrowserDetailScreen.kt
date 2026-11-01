package com.ivandsky.puzzlebox.app.debug

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.models.ShowkaseBrowserComponent
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * Device/form variants the detail screen can render (spec: hybrid detail panes). Each
 * maps to a set of environment overrides in [VariantEnvironment]; selecting one swaps
 * the light+dark pair for a single variant pane, tapping it again restores the pair.
 */
internal enum class ComponentVariant(val label: String) {
    TabletWide("Tablet/wide"),
    Landscape("Landscape"),
    Rtl("RTL"),
    Font2x("Font 2x"),
}

// Tablet-class screen bounds handed to LocalConfiguration (sw800dp => XLARGE bucket):
// Tablet/wide is a portrait tablet (medium width, tall), landscape a 1280×800 window
// (expanded width) — distinct width/height classifications for size-adaptive content.
private const val TABLET_SHORT_DP = 800
private const val TABLET_LONG_DP = 1280

private const val VARIANT_FONT_SCALE = 2f

/**
 * Detail screen for one indexed component (ticket showkase-v2-04): light + dark panes
 * side-by-side on the shared [PreviewBackdrop] by default; the four variant chips each
 * swap the pair for a single pane rendering the component under that variant. Tapping
 * the active chip returns to the base pair. The backdrop FILL arrives from the
 * browser's toolbar (ticket 07) — every pane here follows the same
 * [PreviewBackdropStyle] session choice.
 */
@Composable
internal fun ShowkaseComponentDetailScreen(
    component: ShowkaseBrowserComponent,
    backdropStyle: PreviewBackdropStyle,
    onBack: () -> Unit,
) {
    // survives rotation; intentionally not persisted — a fresh entry starts on the pair.
    var variant by rememberSaveable { mutableStateOf<ComponentVariant?>(null) }

    Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxSize()) {
            DetailHeader(component, onBack)
            VariantChipRow(
                selected = variant,
                onSelect = { picked -> variant = if (picked == variant) null else picked },
            )
            Crossfade(
                targetState = variant,
                animationSpec = tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedDecelerate),
                label = "detailPanes",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { active ->
                if (active == null) {
                    BasePanes(component, backdropStyle, Modifier.fillMaxWidth())
                } else {
                    VariantPane(component, active, backdropStyle, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(component: ShowkaseBrowserComponent, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to browser")
        }
        Column(Modifier.padding(start = Spacing.xs)) {
            Text(
                component.componentName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(component.group)
                    component.styleName?.takeIf { !component.isDefaultStyle }?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VariantChipRow(
    selected: ComponentVariant?,
    onSelect: (ComponentVariant) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ComponentVariant.entries.forEach { candidate ->
            FilterChip(
                selected = selected == candidate,
                onClick = { onSelect(candidate) },
                label = { Text(candidate.label) },
            )
        }
    }
}

/** The default view: the component under explicit light and dark themes, side by side. */
@Composable
private fun BasePanes(
    component: ShowkaseBrowserComponent,
    backdropStyle: PreviewBackdropStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        DetailPane("Light", Modifier.weight(1f), darkTheme = false, variant = null, backdropStyle = backdropStyle) {
            component.component()
        }
        DetailPane("Dark", Modifier.weight(1f), darkTheme = true, variant = null, backdropStyle = backdropStyle) {
            component.component()
        }
    }
}

/** The active variant replacing the base pair: one pane, light theme, variant overrides. */
@Composable
private fun VariantPane(
    component: ShowkaseBrowserComponent,
    variant: ComponentVariant,
    backdropStyle: PreviewBackdropStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = Spacing.lg)) {
        Text(
            variant.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreviewBackdrop(
            modifier = Modifier.fillMaxWidth(),
            style = backdropStyle,
            heightHint = component.heightDp?.dp,
        ) {
            val canvasWidth = when (variant) {
                ComponentVariant.TabletWide -> TABLET_SHORT_DP.dp
                ComponentVariant.Landscape -> TABLET_LONG_DP.dp
                ComponentVariant.Rtl, ComponentVariant.Font2x -> null
            }
            if (canvasWidth == null) {
                VariantEnvironment(darkTheme = false, variant = variant) {
                    component.component()
                }
            } else {
                // Pan the variant-width canvas inside the pane: the bound is real even
                // when the pane is narrower than the variant's screen.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Box(Modifier.width(canvasWidth)) {
                        VariantEnvironment(darkTheme = false, variant = variant) {
                            component.component()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPane(
    label: String,
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    variant: ComponentVariant?,
    backdropStyle: PreviewBackdropStyle,
    content: @Composable () -> Unit,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreviewBackdrop(modifier = Modifier.fillMaxWidth(), style = backdropStyle, heightHint = null) {
            VariantEnvironment(darkTheme = darkTheme, variant = variant, content = content)
        }
    }
}

/**
 * The research-01 mechanism: render [content] under an explicit theme plus, for a
 * [variant], CompositionLocal overrides — LocalConfiguration screen-size bounds
 * (tablet/landscape), LocalLayoutDirection RTL, LocalDensity fontScale 2×.
 *
 * The pane's dark/light request is ALSO written into the configuration copy's uiMode:
 * indexed design-system previews self-theme with `PuzzleBoxTheme {}`, whose default
 * follows `isSystemInDarkTheme()` — i.e. LocalConfiguration, not the surrounding
 * explicit theme. Without the uiMode override those components would ignore the pane
 * and render in whatever mode the device is in.
 */
@Composable
private fun VariantEnvironment(
    darkTheme: Boolean,
    variant: ComponentVariant?,
    content: @Composable () -> Unit,
) {
    val ambientConfiguration = LocalConfiguration.current
    val configuration = remember(ambientConfiguration, darkTheme, variant) {
        Configuration(ambientConfiguration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            when (variant) {
                ComponentVariant.TabletWide -> {
                    screenWidthDp = TABLET_SHORT_DP
                    screenHeightDp = TABLET_LONG_DP
                    smallestScreenWidthDp = TABLET_SHORT_DP
                    orientation = Configuration.ORIENTATION_PORTRAIT
                    screenLayout = (screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK.inv()) or
                        Configuration.SCREENLAYOUT_SIZE_XLARGE
                }
                ComponentVariant.Landscape -> {
                    screenWidthDp = TABLET_LONG_DP
                    screenHeightDp = TABLET_SHORT_DP
                    smallestScreenWidthDp = TABLET_SHORT_DP
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                    screenLayout = (screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK.inv()) or
                        Configuration.SCREENLAYOUT_SIZE_XLARGE
                }
                ComponentVariant.Rtl, ComponentVariant.Font2x, null -> Unit
            }
        }
    }
    val density = LocalDensity.current
    val ambientLayoutDirection = LocalLayoutDirection.current
    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides if (variant == ComponentVariant.Rtl) {
            LayoutDirection.Rtl
        } else {
            ambientLayoutDirection
        },
        LocalDensity provides if (variant == ComponentVariant.Font2x) {
            Density(density = density.density, fontScale = VARIANT_FONT_SCALE)
        } else {
            density
        },
    ) {
        PuzzleBoxTheme(darkTheme = darkTheme) {
            content()
        }
    }
}

// region Previews

/** Light + dark chrome in one annotation — the detail screen must read in both. */
@Preview(
    name = "Light",
    group = "Showkase detail",
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
    widthDp = 800,
    heightDp = 600,
)
@Preview(
    name = "Dark",
    group = "Showkase detail",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    backgroundColor = 0xFF101014,
    widthDp = 800,
    heightDp = 600,
)
private annotation class DetailThemePreview

@DetailThemePreview
@Composable
private fun ShowkaseComponentDetailPreview() {
    PuzzleBoxTheme {
        ShowkaseComponentDetailScreen(
            previewDetailComponent(),
            backdropStyle = PreviewBackdropStyle.Checkerboard,
            onBack = {},
        )
    }
}

// Directional fixture (icon + text row) so the preview panel shows RTL mirroring and
// the theme panes visibly differ, the way real indexed components would.
@Composable
private fun previewDetailComponent() = ShowkaseBrowserComponent(
    componentKey = "Buttons.PrimaryButton",
    group = "Buttons",
    componentName = "PrimaryButton",
    componentKDoc = "",
    component = {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text("Confirm", style = MaterialTheme.typography.bodyLarge)
        }
    },
)

// endregion
