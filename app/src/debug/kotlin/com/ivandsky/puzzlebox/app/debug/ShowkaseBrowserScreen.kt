package com.ivandsky.puzzlebox.app.debug

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.models.ShowkaseBrowserColor
import com.airbnb.android.showkase.models.ShowkaseBrowserComponent
import com.airbnb.android.showkase.models.ShowkaseBrowserTypography
import com.airbnb.android.showkase.models.ShowkaseElementsMetadata
import com.airbnb.android.showkase.models.ShowkaseProvider
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

// Same resolution mechanism as the stock browser activity: load the generated provider
// by class name. The codegen is a plain class (not an object — see the generated
// PuzzleBoxShowkaseModuleCodegen.kt), so it needs its constructor, not objectInstance.
private const val SHOWKASE_CODEGEN_CLASS =
    "com.ivandsky.puzzlebox.designsystem.PuzzleBoxShowkaseModuleCodegen"

private fun loadShowkaseProvider(): ShowkaseProvider? = try {
    Class.forName(SHOWKASE_CODEGEN_CLASS)
        .getDeclaredConstructor()
        .newInstance() as? ShowkaseProvider
} catch (_: Throwable) {
    null
}

/**
 * The Showkase browser that replaces the Catalog route content (ticket showkase-v2-01).
 * Unlike the old pinned-dark activity, it draws with the AMBIENT theme — the route sits
 * inside PuzzleBoxTheme, so light/dark chrome follows the app's persisted theme mode.
 */
@Composable
fun ShowkaseBrowserScreen() {
    val context = LocalContext.current
    val prefs = remember { ShowkaseBrowserPrefs(context) }
    var expandedGroups by remember { mutableStateOf(prefs.expandedGroups()) }
    val metadata = remember { loadShowkaseProvider()?.metadata() }

    ShowkaseBrowserContent(
        metadata = metadata,
        expandedGroups = expandedGroups,
        onToggleGroup = { key ->
            val next = if (key in expandedGroups) expandedGroups - key else expandedGroups + key
            expandedGroups = next
            // Write-through: SharedPreferences outlives process death and app restarts,
            // which rememberSaveable alone cannot cover.
            prefs.setExpandedGroups(next)
        },
    )
}

/**
 * Stateless body (previewable): metadata arrives as data, expansion as an event, so
 * previews need no provider loading and Layoutlib stays composition-safe.
 */
@Composable
internal fun ShowkaseBrowserContent(
    metadata: ShowkaseElementsMetadata?,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
) {
    // Surface (not a bare background) so LocalContentColor follows the scheme — default
    // Texts would otherwise inherit an unspecified color and can vanish on dark.
    Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxSize()) {
            BrowserHeader()
            when {
                metadata == null -> ErrorPane(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                else -> GroupList(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    metadata = metadata,
                    expandedGroups = expandedGroups,
                    onToggleGroup = onToggleGroup,
                )
            }
        }
    }
}

@Composable
private fun BrowserHeader() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Text(
            "Design system",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Showkase browser — tap a group to expand it",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorPane(modifier: Modifier) {
    Column(
        modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Showkase metadata unavailable", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "The generated provider (${SHOWKASE_CODEGEN_CLASS
                .removePrefix("com.ivandsky.puzzlebox.designsystem.")}) could not be loaded. " +
                "Clean-build :core:designsystem and retry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// One lazy item per group (header + collapsible body): AnimatedVisibility gives the
// A8 expand/collapse motion, and the body composes only while open. Bounded metadata
// keeps a whole group cheap enough that per-group recycling loss doesn't matter.
@Composable
private fun GroupList(
    modifier: Modifier,
    metadata: ShowkaseElementsMetadata,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = Spacing.xl),
    ) {
        section(
            title = "Components",
            entries = metadata.componentList.sortedBy { it.componentName },
            groupOf = { it.group },
            keyPrefix = "component",
            expandedGroups = expandedGroups,
            onToggleGroup = onToggleGroup,
        ) { ComponentRow(it) }
        section(
            title = "Colors",
            entries = metadata.colorList.sortedBy { it.colorName },
            groupOf = { it.colorGroup },
            keyPrefix = "color",
            expandedGroups = expandedGroups,
            onToggleGroup = onToggleGroup,
        ) { ColorRow(it) }
        section(
            title = "Typography",
            entries = metadata.typographyList.sortedBy { it.typographyName },
            groupOf = { it.typographyGroup },
            keyPrefix = "typography",
            expandedGroups = expandedGroups,
            onToggleGroup = onToggleGroup,
        ) { TypographyRow(it) }
    }
}

// Section title, then one collapsible item per Showkase group, alphabetical (A7).
// Group keys are "<section>:<group>" — namespaces across sections stay unique.
private fun <T> LazyListScope.section(
    title: String,
    entries: List<T>,
    groupOf: (T) -> String,
    keyPrefix: String,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    row: @Composable (T) -> Unit,
) {
    item(key = "$keyPrefix-section") {
        Text(
            "$title — ${entries.size}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.xs),
        )
    }
    entries.groupBy(groupOf).toSortedMap().forEach { (groupName, groupItems) ->
        val groupKey = "$keyPrefix:$groupName"
        item(key = "$keyPrefix-group-$groupName") {
            CollapsibleGroup(
                title = groupName,
                count = groupItems.size,
                expanded = groupKey in expandedGroups,
                onToggle = { onToggleGroup(groupKey) },
            ) {
                groupItems.forEach { row(it) }
            }
        }
    }
}

@Composable
private fun CollapsibleGroup(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Chevron(expanded)
        }
        // A8: standard Material3 expand/collapse — fade + vertical size, nothing exotic.
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedDecelerate)) +
                expandVertically(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedDecelerate)),
            exit = fadeOut(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedAccelerate)) +
                shrinkVertically(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedAccelerate)),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun Chevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedDecelerate),
        label = "groupChevron",
    )
    Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = if (expanded) "Collapse" else "Expand",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.rotate(rotation),
    )
}

@Composable
private fun ComponentRow(component: ShowkaseBrowserComponent) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Text(component.componentName, style = MaterialTheme.typography.bodyLarge)
        component.styleName?.takeIf { !component.isDefaultStyle }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        // Inline preview (ticket 02): the indexed composable invoked in place, on the
        // shared backdrop — no tap needed. Declared @Preview sizes arrive as hints and
        // cap, never stretch; anything taller than 480dp is contained by the backdrop.
        PreviewBackdrop(
            modifier = Modifier.fillMaxWidth(),
            widthHint = component.widthDp?.dp,
            heightHint = component.heightDp?.dp,
        ) {
            component.component()
        }
    }
}

@Composable
private fun ColorRow(color: ShowkaseBrowserColor) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A3: the swatch IS the color's inline preview — the backdrop's checkerboard
        // doubles as a transparency grid, and the border marks the sampled region.
        PreviewBackdrop(contentPadding = PaddingValues(Spacing.sm)) {
            Box(Modifier.size(44.dp).background(color.color))
        }
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(color.colorName, style = MaterialTheme.typography.bodyLarge)
            Text(
                color.hex(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypographyRow(typography: ShowkaseBrowserTypography) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Text(
            typography.typographyName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        // A3: sample text on the shared backdrop, so the type specimen reads as a
        // framed sample rather than body copy.
        PreviewBackdrop(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Sample text",
                style = typography.textStyle.resolved(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Token styles carry Color.Unspecified; resolve against the AMBIENT scheme so samples
// stay legible in light and dark (the old browser pinned this to dark).
@Composable
private fun TextStyle.resolved(): TextStyle = copy(color = MaterialTheme.colorScheme.onSurface)

private fun ShowkaseBrowserColor.hex(): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

// region Previews

/** Light + dark chrome in one annotation — the browser must read in both (DoD). */
@Preview(
    name = "Light",
    group = "Showkase browser",
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
    widthDp = 400,
    heightDp = 800,
)
@Preview(
    name = "Dark",
    group = "Showkase browser",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    backgroundColor = 0xFF101014,
    widthDp = 400,
    heightDp = 800,
)
private annotation class BrowserThemePreview

private class ExpandedGroupsProvider : PreviewParameterProvider<Set<String>> {
    // List-backed: the skill's guidance for fast indexed access in the preview panel.
    override val values: Sequence<Set<String>> = listOf(
        setOf("component:Buttons", "color:Primary"),
        emptySet(),
    ).asSequence()

    override fun getDisplayName(index: Int): String = when (index) {
        0 -> "Two groups expanded"
        else -> "All collapsed"
    }
}

@BrowserThemePreview
@Composable
private fun ShowkaseBrowserContentPreview(
    @PreviewParameter(ExpandedGroupsProvider::class) expandedGroups: Set<String>,
) {
    PuzzleBoxTheme {
        ShowkaseBrowserContent(previewMetadata(), expandedGroups, onToggleGroup = {})
    }
}

@BrowserThemePreview
@Composable
private fun ShowkaseBrowserErrorPreview() {
    PuzzleBoxTheme {
        ShowkaseBrowserContent(metadata = null, expandedGroups = emptySet(), onToggleGroup = {})
    }
}

@Composable
private fun previewMetadata(): ShowkaseElementsMetadata = ShowkaseElementsMetadata(
    componentList = listOf(
        previewComponent("PuzzleBoard", "Board"),
        previewComponent("PrimaryButton", "Buttons"),
        previewComponent("GhostButton", "Buttons", styleName = "Ghost"),
    ),
    colorList = listOf(
        previewColor("Primary", "Primary"),
        previewColor("Primary", "OnPrimary"),
        previewColor("Surface", "Surface"),
    ),
    typographyList = listOf(
        previewTypography("Display", "DisplayLarge"),
        previewTypography("Body", "BodyMedium"),
    ),
)

private fun previewComponent(
    name: String,
    group: String,
    styleName: String? = null,
) = ShowkaseBrowserComponent(
    componentKey = "$group.$name",
    group = group,
    componentName = name,
    componentKDoc = "",
    // Inline previews invoke this lambda in place (ticket 02): a stub render, not an
    // empty box, so the preview panel shows realistic backdrops.
    component = {
        Text("…$name…", style = MaterialTheme.typography.bodyMedium)
    },
    styleName = styleName,
)

private fun previewColor(group: String, name: String) = ShowkaseBrowserColor(
    colorGroup = group,
    colorName = name,
    colorKDoc = "",
    color = Color(0xFF2E7D32),
)

@Composable
private fun previewTypography(group: String, name: String) = ShowkaseBrowserTypography(
    typographyGroup = group,
    typographyName = name,
    typographyKDoc = "",
    textStyle = MaterialTheme.typography.headlineSmall,
)

// endregion
