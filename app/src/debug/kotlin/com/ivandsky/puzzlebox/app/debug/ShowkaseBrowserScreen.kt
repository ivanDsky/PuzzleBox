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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.luminance
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

// Every group key the browser can render, across all three sections — what the
// collapse-all/expand-all toolbar action (ticket 06) writes in one shot.
private fun allGroupKeys(metadata: ShowkaseElementsMetadata): Set<String> = buildSet {
    metadata.componentList.forEach { add(groupKey(COMPONENT_KEY_PREFIX, it.group)) }
    metadata.colorList.forEach { add(groupKey(COLOR_KEY_PREFIX, it.colorGroup)) }
    metadata.typographyList.forEach { add(groupKey(TYPOGRAPHY_KEY_PREFIX, it.typographyGroup)) }
}

/**
 * Theme override positions (tickets showkase-v2-06/09, rendered as a radio group on the
 * home and detail screens): [System] removes the override — the browser follows the app
 * theme the Catalog route already sits in; [Light]/[Dark] force the whole browser
 * (chrome, backdrops, detail screen) while set. Session-scoped like the search text:
 * survives rotation via rememberSaveable, deliberately not persisted.
 */
internal enum class BrowserThemeOverride(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

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
    // Ticket 06 theme switch: session-scoped override, see [BrowserThemeOverride].
    var themeOverride by rememberSaveable { mutableStateOf(BrowserThemeOverride.System) }
    // Ticket 07 backdrop fill: same session scope — rotation-safe via rememberSaveable,
    // deliberately not persisted across launches.
    var backdropStyle by rememberSaveable { mutableStateOf(PreviewBackdropStyle.Checkerboard) }
    // Transient filter text: survives rotation (rememberSaveable), intentionally not
    // persisted — a fresh launch always starts unfiltered.
    var query by rememberSaveable { mutableStateOf("") }
    val metadata = remember { loadShowkaseProvider()?.metadata() }
    // Ticket 04 row→detail: only the component KEY is saveable (the metadata model
    // carries a composable lambda that can't go in a Bundle) — re-resolve it from the
    // metadata after rotation or process restore.
    var selectedComponentKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Ticket 08: the list's scroll state is hoisted ABOVE the detail switch. Opening a
    // detail replaces the list branch — with the state created inside [GroupList] that
    // disposal restarted the list at the top on back. Held here, the same LazyListState
    // instance survives the round-trip and the list reappears at the exact offset.
    // rememberSaveable-backed, so rotation and process restore keep working too;
    // expansion and query already live at this scope and survive untouched.
    val listState = rememberLazyListState()
    val selectedComponent = metadata?.componentList
        ?.firstOrNull { it.componentKey == selectedComponentKey }
    BackHandler(enabled = selectedComponent != null) { selectedComponentKey = null }

    // Ambient darkness must be read BEFORE the override re-enters PuzzleBoxTheme — under
    // an override the scheme is already the forced one. Light/dark backgrounds differ
    // hugely in luminance (0xFFF2F4F7 vs 0xFF101418), making the scheme a faithful read.
    // The resolved flag themes the whole browser AND drives the detail screen's variant
    // pane: VariantEnvironment writes the pane's dark request into its uiMode copy, so
    // "Dark + RTL" renders the RTL pane dark (ticket showkase-v2-09).
    val ambientDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val browserDark = when (themeOverride) {
        BrowserThemeOverride.System -> ambientDark
        BrowserThemeOverride.Light -> false
        BrowserThemeOverride.Dark -> true
    }
    PuzzleBoxTheme(darkTheme = browserDark) {
        if (selectedComponent != null) {
            ShowkaseComponentDetailScreen(
                component = selectedComponent,
                themeOverride = themeOverride,
                onThemeOverrideChange = { themeOverride = it },
                backdropStyle = backdropStyle,
                onBackdropStyleChange = { backdropStyle = it },
                variantDarkTheme = browserDark,
                onBack = { selectedComponentKey = null },
            )
        } else {
            // All group keys upfront: the expand/collapse-all toolbar action writes
            // every key at once through the same write-through path as single toggles.
            val allKeys = remember(metadata) { metadata?.let(::allGroupKeys).orEmpty() }
            ShowkaseBrowserContent(
                metadata = metadata,
                listState = listState,
                expandedGroups = expandedGroups,
                onToggleGroup = { key ->
                    val next = if (key in expandedGroups) expandedGroups - key else expandedGroups + key
                    expandedGroups = next
                    // Write-through: SharedPreferences outlives process death and app restarts,
                    // which rememberSaveable alone cannot cover.
                    prefs.setExpandedGroups(next)
                },
                allGroupsExpanded = allKeys.isNotEmpty() && expandedGroups.containsAll(allKeys),
                onSetAllGroupsExpanded = { expand ->
                    val next = if (expand) allKeys else emptySet()
                    expandedGroups = next
                    prefs.setExpandedGroups(next)
                },
                themeOverride = themeOverride,
                onThemeOverrideChange = { themeOverride = it },
                backdropStyle = backdropStyle,
                onBackdropStyleChange = { backdropStyle = it },
                query = query,
                onQueryChange = { query = it },
                onOpenComponent = { selectedComponentKey = it.componentKey },
            )
        }
    }
}

/**
 * Previewable body: metadata, expansion, the search text and the list's scroll state
 * arrive as data/injected state (ticket 08 hoists [listState] above the detail switch
 * so back from detail restores the offset), toggles as events, so previews need no
 * provider loading and Layoutlib stays composition-safe.
 */
@Composable
internal fun ShowkaseBrowserContent(
    metadata: ShowkaseElementsMetadata?,
    listState: LazyListState,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    allGroupsExpanded: Boolean,
    onSetAllGroupsExpanded: (Boolean) -> Unit,
    themeOverride: BrowserThemeOverride,
    onThemeOverrideChange: (BrowserThemeOverride) -> Unit,
    backdropStyle: PreviewBackdropStyle,
    onBackdropStyleChange: (PreviewBackdropStyle) -> Unit,
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
            BrowserHeader(
                query = query,
                onQueryChange = onQueryChange,
                themeOverride = themeOverride,
                onThemeOverrideChange = onThemeOverrideChange,
                backdropStyle = backdropStyle,
                onBackdropStyleChange = onBackdropStyleChange,
                allGroupsExpanded = allGroupsExpanded,
                onSetAllGroupsExpanded = onSetAllGroupsExpanded,
                groupsActionEnabled = metadata != null,
            )
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
                            listState = listState,
                            results = results,
                            expandedGroups = visibleGroups,
                            onToggleGroup = onToggleGroup,
                            backdropStyle = backdropStyle,
                            onOpenComponent = onOpenComponent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    themeOverride: BrowserThemeOverride,
    onThemeOverrideChange: (BrowserThemeOverride) -> Unit,
    backdropStyle: PreviewBackdropStyle,
    onBackdropStyleChange: (PreviewBackdropStyle) -> Unit,
    allGroupsExpanded: Boolean,
    onSetAllGroupsExpanded: (Boolean) -> Unit,
    groupsActionEnabled: Boolean,
) {
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
        ToolbarControls(
            allGroupsExpanded = allGroupsExpanded,
            onSetAllGroupsExpanded = onSetAllGroupsExpanded,
            groupsActionEnabled = groupsActionEnabled,
        )
        Spacer(Modifier.height(Spacing.sm))
        SearchField(query, onQueryChange)
        // Ticket 09 (user addendum): the radio control row sits DIRECTLY under the
        // search bar — not above it, not in the bulk-action row.
        BrowserRadioControlRow(
            themeOverride = themeOverride,
            onThemeOverrideChange = onThemeOverrideChange,
            backdropStyle = backdropStyle,
            onBackdropStyleChange = onBackdropStyleChange,
        )
    }
}

/**
 * The one surviving toolbar chip (ticket 09): collapse/expand-all, trailing-aligned.
 * Theme/backdrop moved to [BrowserRadioControlRow] under the search bar. The bulk
 * action is meaningless without metadata, so it disables on the error pane.
 */
@Composable
private fun ToolbarControls(
    allGroupsExpanded: Boolean,
    onSetAllGroupsExpanded: (Boolean) -> Unit,
    groupsActionEnabled: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
    ) {
        AssistChip(
            onClick = { onSetAllGroupsExpanded(!allGroupsExpanded) },
            label = { Text(if (allGroupsExpanded) "Collapse all" else "Expand all") },
            enabled = groupsActionEnabled,
        )
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
    listState: LazyListState,
    results: SearchResults,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    backdropStyle: PreviewBackdropStyle,
    onOpenComponent: (ShowkaseBrowserComponent) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
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
                ComponentRow(it, backdropStyle, onClick = { onOpenComponent(it) })
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
            ) { ColorRow(it, backdropStyle) }
        }
        if (results.typography.isNotEmpty()) {
            section(
                title = "Typography",
                entries = results.typography.sortedBy { it.typographyName },
                groupOf = { it.typographyGroup },
                keyPrefix = TYPOGRAPHY_KEY_PREFIX,
                expandedGroups = expandedGroups,
                onToggleGroup = onToggleGroup,
            ) { TypographyRow(it, backdropStyle) }
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
private fun ComponentRow(
    component: ShowkaseBrowserComponent,
    backdropStyle: PreviewBackdropStyle,
    onClick: () -> Unit,
) {
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
            style = backdropStyle,
            widthHint = component.widthDp?.dp,
            heightHint = component.heightDp?.dp,
        ) {
            component.component()
        }
    }
}

@Composable
private fun ColorRow(color: ShowkaseBrowserColor, backdropStyle: PreviewBackdropStyle) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A3: the swatch IS the color's inline preview — the backdrop doubles as a
        // transparency grid (checkerboard) or a fixed fill (ticket 07 styles), and the
        // border marks the sampled region.
        PreviewBackdrop(contentPadding = PaddingValues(Spacing.sm), style = backdropStyle) {
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
private fun TypographyRow(typography: ShowkaseBrowserTypography, backdropStyle: PreviewBackdropStyle) {
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
        PreviewBackdrop(modifier = Modifier.fillMaxWidth(), style = backdropStyle) {
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
        ShowkaseBrowserContent(
            previewMetadata(),
            listState = rememberLazyListState(),
            expandedGroups = expandedGroups,
            onToggleGroup = {},
            allGroupsExpanded = false,
            onSetAllGroupsExpanded = {},
            themeOverride = BrowserThemeOverride.System,
            onThemeOverrideChange = {},
            backdropStyle = PreviewBackdropStyle.Checkerboard,
            onBackdropStyleChange = {},
            query = "",
            onQueryChange = {},
            onOpenComponent = {},
        )
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
            listState = rememberLazyListState(),
            expandedGroups = emptySet(),
            onToggleGroup = {},
            allGroupsExpanded = false,
            onSetAllGroupsExpanded = {},
            themeOverride = BrowserThemeOverride.System,
            onThemeOverrideChange = {},
            backdropStyle = PreviewBackdropStyle.Checkerboard,
            onBackdropStyleChange = {},
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
            listState = rememberLazyListState(),
            expandedGroups = emptySet(),
            onToggleGroup = {},
            allGroupsExpanded = false,
            onSetAllGroupsExpanded = {},
            themeOverride = BrowserThemeOverride.System,
            onThemeOverrideChange = {},
            backdropStyle = PreviewBackdropStyle.Checkerboard,
            onBackdropStyleChange = {},
            query = "",
            onQueryChange = {},
            onOpenComponent = {},
        )
    }
}

// Ticket 06 override proofs: both previews render in the LIGHT and DARK configs
// (BrowserThemePreview) while forcing the OPPOSITE mode, so the light-config render
// shows dark chrome and the dark-config render light chrome — the toolbar switch
// re-themes the whole body, not just the panes.
@BrowserThemePreview
@Composable
private fun ShowkaseBrowserForcedDarkPreview() {
    PuzzleBoxTheme(darkTheme = true) {
        ShowkaseBrowserContent(
            previewMetadata(),
            listState = rememberLazyListState(),
            expandedGroups = emptySet(),
            onToggleGroup = {},
            allGroupsExpanded = false,
            onSetAllGroupsExpanded = {},
            themeOverride = BrowserThemeOverride.Dark,
            onThemeOverrideChange = {},
            backdropStyle = PreviewBackdropStyle.Checkerboard,
            onBackdropStyleChange = {},
            query = "",
            onQueryChange = {},
            onOpenComponent = {},
        )
    }
}

@BrowserThemePreview
@Composable
private fun ShowkaseBrowserForcedLightPreview() {
    PuzzleBoxTheme(darkTheme = false) {
        ShowkaseBrowserContent(
            previewMetadata(),
            listState = rememberLazyListState(),
            expandedGroups = emptySet(),
            onToggleGroup = {},
            allGroupsExpanded = false,
            onSetAllGroupsExpanded = {},
            themeOverride = BrowserThemeOverride.Light,
            onThemeOverrideChange = {},
            backdropStyle = PreviewBackdropStyle.Checkerboard,
            onBackdropStyleChange = {},
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
