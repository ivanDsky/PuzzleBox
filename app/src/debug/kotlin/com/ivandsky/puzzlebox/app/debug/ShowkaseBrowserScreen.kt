package com.ivandsky.puzzlebox.app.debug

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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

// Group keys are "<prefix>:<group>" — namespaces across sections stay unique. Shared by
// the list rendering and the search's matching-group computation so the format has one home.
private const val COMPONENT_KEY_PREFIX = "component"
private const val COLOR_KEY_PREFIX = "color"
private const val TYPOGRAPHY_KEY_PREFIX = "typography"

private fun groupKey(prefix: String, group: String) = "$prefix:$group"

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
    // Transient filter text: survives rotation (rememberSaveable), intentionally not
    // persisted — a fresh launch always starts unfiltered.
    var query by rememberSaveable { mutableStateOf("") }
    val metadata = remember { loadShowkaseProvider()?.metadata() }
    // Ticket 04 row→detail: only the component KEY is saveable (the metadata model
    // carries a composable lambda that can't go in a Bundle) — re-resolve it from the
    // metadata after rotation or process restore.
    var selectedComponentKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedComponent = metadata?.componentList
        ?.firstOrNull { it.componentKey == selectedComponentKey }
    BackHandler(enabled = selectedComponent != null) { selectedComponentKey = null }

    if (selectedComponent != null) {
        ShowkaseComponentDetailScreen(
            component = selectedComponent,
            onBack = { selectedComponentKey = null },
        )
        return
    }

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
        query = query,
        onQueryChange = { query = it },
        onOpenComponent = { selectedComponentKey = it.componentKey },
    )
}

/**
 * Stateless body (previewable): metadata, expansion, and the search text arrive as data,
 * toggles as events, so previews need no provider loading and Layoutlib stays
 * composition-safe.
 */
@Composable
internal fun ShowkaseBrowserContent(
    metadata: ShowkaseElementsMetadata?,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenComponent: (ShowkaseBrowserComponent) -> Unit,
) {
    // Surface (not a bare background) so LocalContentColor follows the scheme — default
    // Texts would otherwise inherit an unspecified color and can vanish on dark.
    Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxSize()) {
            BrowserHeader(query, onQueryChange)
            when {
                metadata == null -> ErrorPane(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                else -> {
                    val results = remember(metadata, query) { SearchResults.of(metadata, query) }
                    // A7: while a query is active every group holding a match renders
                    // expanded — the persisted set unioned with the matches — so matches
                    // are always visible. Clearing drops the union and the persisted
                    // expansion state shows through untouched.
                    val visibleGroups = if (results.queryActive) {
                        expandedGroups + results.matchingGroups
                    } else {
                        expandedGroups
                    }
                    if (results.queryActive && results.isEmpty) {
                        NoResults(query, Modifier.weight(1f).fillMaxWidth())
                    } else {
                        GroupList(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            results = results,
                            expandedGroups = visibleGroups,
                            onToggleGroup = onToggleGroup,
                            onOpenComponent = onOpenComponent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserHeader(query: String, onQueryChange: (String) -> Unit) {
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
            "Showkase browser — search narrows, tap a group to expand, tap a component for variants",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        SearchField(query, onQueryChange)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search components, colors, type") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

// Every section filtered out — say so instead of a silent blank pane.
@Composable
private fun NoResults(query: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            "No matches for “${query.trim()}”",
            style = MaterialTheme.typography.bodyLarge,
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
    results: SearchResults,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    onOpenComponent: (ShowkaseBrowserComponent) -> Unit,
) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = Spacing.xl),
    ) {
        // While searching, sections with zero matches drop out entirely (title included)
        // and the counts reflect the narrowed lists — the honest "filtered" view.
        if (results.components.isNotEmpty()) {
            section(
                title = "Components",
                entries = results.components.sortedBy { it.componentName },
                groupOf = { it.group },
                keyPrefix = COMPONENT_KEY_PREFIX,
                expandedGroups = expandedGroups,
                onToggleGroup = onToggleGroup,
            ) {
                // Whole row tappable (name + inline preview) → detail screen (ticket 04).
                ComponentRow(it, onClick = { onOpenComponent(it) })
            }
        }
        if (results.colors.isNotEmpty()) {
            section(
                title = "Colors",
                entries = results.colors.sortedBy { it.colorName },
                groupOf = { it.colorGroup },
                keyPrefix = COLOR_KEY_PREFIX,
                expandedGroups = expandedGroups,
                onToggleGroup = onToggleGroup,
            ) { ColorRow(it) }
        }
        if (results.typography.isNotEmpty()) {
            section(
                title = "Typography",
                entries = results.typography.sortedBy { it.typographyName },
                groupOf = { it.typographyGroup },
                keyPrefix = TYPOGRAPHY_KEY_PREFIX,
                expandedGroups = expandedGroups,
                onToggleGroup = onToggleGroup,
            ) { TypographyRow(it) }
        }
    }
}

/**
 * Query view of the metadata (A7): the three entry lists filtered by name OR group name,
 * plus the keys of every group holding at least one match. A blank query is the identity
 * view — full lists, no forced expansion.
 */
private class SearchResults private constructor(
    val queryActive: Boolean,
    val components: List<ShowkaseBrowserComponent>,
    val colors: List<ShowkaseBrowserColor>,
    val typography: List<ShowkaseBrowserTypography>,
    val matchingGroups: Set<String>,
) {
    val isEmpty: Boolean
        get() = components.isEmpty() && colors.isEmpty() && typography.isEmpty()

    companion object {
        fun of(metadata: ShowkaseElementsMetadata, rawQuery: String): SearchResults {
            val query = rawQuery.trim()
            if (query.isEmpty()) {
                return SearchResults(
                    queryActive = false,
                    components = metadata.componentList,
                    colors = metadata.colorList,
                    typography = metadata.typographyList,
                    matchingGroups = emptySet(),
                )
            }
            val components = metadata.componentList
                .matchingEntries(query, { it.componentName }, { it.group })
            val colors = metadata.colorList
                .matchingEntries(query, { it.colorName }, { it.colorGroup })
            val typography = metadata.typographyList
                .matchingEntries(query, { it.typographyName }, { it.typographyGroup })
            return SearchResults(
                queryActive = true,
                components = components,
                colors = colors,
                typography = typography,
                matchingGroups = buildSet {
                    components.forEach { add(groupKey(COMPONENT_KEY_PREFIX, it.group)) }
                    colors.forEach { add(groupKey(COLOR_KEY_PREFIX, it.colorGroup)) }
                    typography.forEach { add(groupKey(TYPOGRAPHY_KEY_PREFIX, it.typographyGroup)) }
                },
            )
        }
    }
}

// Case-insensitive substring on the entry name OR the group name — a group-name hit
// pulls in every entry of that group.
private fun <T> List<T>.matchingEntries(
    query: String,
    name: (T) -> String,
    group: (T) -> String,
): List<T> = filter {
    name(it).contains(query, ignoreCase = true) || group(it).contains(query, ignoreCase = true)
}

// Section title, then one collapsible item per Showkase group, alphabetical (A7).
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
        val key = groupKey(keyPrefix, groupName)
        item(key = "$keyPrefix-group-$groupName") {
            CollapsibleGroup(
                title = groupName,
                count = groupItems.size,
                expanded = key in expandedGroups,
                onToggle = { onToggleGroup(key) },
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
private fun ComponentRow(component: ShowkaseBrowserComponent, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        ShowkaseBrowserContent(previewMetadata(), expandedGroups, onToggleGroup = {}, query = "", onQueryChange = {}, onOpenComponent = {})
    }
}

@BrowserThemePreview
@Composable
private fun ShowkaseBrowserSearchPreview() {
    PuzzleBoxTheme {
        // "button" hits PrimaryButton/GhostButton by name AND their group: Components
        // narrows to the two buttons, Colors/Typography drop out, and Buttons renders
        // expanded even though the persisted set is empty (A7 auto-expand).
        ShowkaseBrowserContent(
            previewMetadata(),
            expandedGroups = emptySet(),
            onToggleGroup = {},
            query = "button",
            onQueryChange = {},
            onOpenComponent = {},
        )
    }
}

@BrowserThemePreview
@Composable
private fun ShowkaseBrowserErrorPreview() {
    PuzzleBoxTheme {
        ShowkaseBrowserContent(
            metadata = null,
            expandedGroups = emptySet(),
            onToggleGroup = {},
            query = "",
            onQueryChange = {},
            onOpenComponent = {},
        )
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
