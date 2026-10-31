package com.ivandsky.puzzlebox.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.models.ShowkaseBrowserColor
import com.airbnb.android.showkase.models.ShowkaseBrowserComponent
import com.airbnb.android.showkase.models.ShowkaseBrowserTypography
import com.airbnb.android.showkase.models.ShowkaseElementsMetadata
import com.airbnb.android.showkase.models.ShowkaseProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme

// Same resolution mechanism as the stock browser activity: load the generated provider
// by class name. The codegen is a plain class (not an object — see the generated
// PuzzleBoxShowkaseModuleCodegen.kt), so it needs its constructor, not objectInstance.
private const val SHOWKASE_CODEGEN_CLASS =
    "com.ivandsky.puzzlebox.designsystem.PuzzleBoxShowkaseModuleCodegen"

class ShowkaseBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The content is pinned to the dark theme below, so the system bars must get the
        // dark (light-icon) appearance even when the device is in day mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            PuzzleBoxTheme(darkTheme = true) {
                ShowkaseBrowserScreen(onClose = ::finish)
            }
        }
    }
}

private fun loadShowkaseProvider(): ShowkaseProvider? = try {
    Class.forName(SHOWKASE_CODEGEN_CLASS)
        .getDeclaredConstructor()
        .newInstance() as? ShowkaseProvider
} catch (_: Throwable) {
    null
}

// One saveable string ("component:<key>" / "color:<g>/<n>" / "typography:<g>/<n>")
// addresses the open detail so rotation doesn't snap back to the list.
private fun selectionKey(section: String, id: String) = "$section:$id"

@Composable
private fun ShowkaseBrowserScreen(onClose: () -> Unit) {
    val metadata = remember { loadShowkaseProvider()?.metadata() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selected != null) { selected = null }

    val surface = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing)

    when {
        metadata == null -> ErrorPane(surface, onClose)
        selected == null -> BrowserHome(surface, metadata, onClose) { selected = it }
        else -> DetailPane(surface, metadata, selected!!) { selected = null }
    }
}

@Composable
private fun ErrorPane(modifier: Modifier, onClose: () -> Unit) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Showkase metadata unavailable", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "The generated provider (${SHOWKASE_CODEGEN_CLASS.removePrefix("com.ivandsky.puzzlebox.designsystem.")}) " +
                "could not be loaded. Clean-build :core:designsystem and retry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserHome(
    modifier: Modifier,
    metadata: ShowkaseElementsMetadata,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
            Column {
                Text("Design system", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Showkase catalog — flat, always dark",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "section-components") {
                SectionTitle("Components — ${metadata.componentList.size}")
            }
            renderGroups(
                metadata.componentList,
                group = { it.group },
                keyPrefix = "comp",
                showHeaders = true,
                itemId = { it.componentKey },
            ) {
                ComponentRow(it) { onOpen(selectionKey("component", it.componentKey)) }
            }
            item(key = "section-colors") {
                SectionTitle("Colors — ${metadata.colorList.size}")
            }
            renderGroups(
                metadata.colorList,
                group = { it.colorGroup },
                keyPrefix = "color",
                showHeaders = false,
                itemId = { it.colorName },
            ) {
                ColorRow(it) { onOpen(selectionKey("color", "${it.colorGroup}/${it.colorName}")) }
            }
            item(key = "section-typography") {
                SectionTitle("Typography — ${metadata.typographyList.size}")
            }
            renderGroups(
                metadata.typographyList,
                group = { it.typographyGroup },
                keyPrefix = "type",
                showHeaders = false,
                itemId = { it.typographyName },
            ) {
                TypographyRow(it) {
                    onOpen(selectionKey("typography", "${it.typographyGroup}/${it.typographyName}"))
                }
            }
        }
    }
}

// Flat list: every group's header sits directly on top of its items (sticky in the
// components section, where there are several groups; single-group sections rely on
// their section title alone to avoid a duplicated header).
private fun <T> androidx.compose.foundation.lazy.LazyListScope.renderGroups(
    entries: List<T>,
    group: (T) -> String,
    keyPrefix: String,
    showHeaders: Boolean,
    itemId: (T) -> String,
    row: @Composable (T) -> Unit,
) {
    val groups = entries.groupBy(group).toSortedMap()
    groups.forEach { (groupName, groupItems) ->
        if (showHeaders) {
            stickyHeader(key = "$keyPrefix-header-$groupName") {
                GroupHeader(groupName)
            }
        }
        items(
            groupItems,
            key = { "$keyPrefix-${groupName}-${itemId(it)}" },
        ) { item ->
            row(item)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp, end = 16.dp),
    )
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ComponentRow(component: ShowkaseBrowserComponent, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(component.componentName, style = MaterialTheme.typography.bodyLarge)
        component.styleName?.takeIf { !component.isDefaultStyle }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColorRow(color: ShowkaseBrowserColor, onClick: () -> Unit) {
    val onColor = onColorFor(color.color)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.color),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", style = MaterialTheme.typography.labelLarge, color = onColor)
        }
        Spacer(Modifier.width(12.dp))
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
private fun TypographyRow(typography: ShowkaseBrowserTypography, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            typography.typographyName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Sample text",
            style = typography.textStyle.onDark(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailPane(
    modifier: Modifier,
    metadata: ShowkaseElementsMetadata,
    selected: String,
    onBack: () -> Unit,
) {
    val section = selected.substringBefore(':')
    val id = selected.substringAfter(':')
    when (section) {
        "component" -> metadata.componentList.firstOrNull { it.componentKey == id }
            ?.let { ComponentDetail(modifier, it, onBack) }
        "color" -> metadata.colorList.firstOrNull { "${it.colorGroup}/${it.colorName}" == id }
            ?.let { ColorDetail(modifier, it, onBack) }
        "typography" -> metadata.typographyList.firstOrNull {
            "${it.typographyGroup}/${it.typographyName}" == id
        }?.let { TypographyDetail(modifier, it, onBack) }
    }
}

@Composable
private fun DetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComponentDetail(
    modifier: Modifier,
    component: ShowkaseBrowserComponent,
    onBack: () -> Unit,
) {
    Column(modifier) {
        DetailHeader(component.componentName, component.group, onBack)
        component.componentKDoc?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            component.component()
        }
    }
}

@Composable
private fun ColorDetail(
    modifier: Modifier,
    color: ShowkaseBrowserColor,
    onBack: () -> Unit,
) {
    val onColor = onColorFor(color.color)
    Column(modifier) {
        DetailHeader(color.colorName, color.colorGroup, onBack)
        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(color.color),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(color.colorName, style = MaterialTheme.typography.headlineSmall, color = onColor)
                    Spacer(Modifier.height(4.dp))
                    Text(color.hex(), style = MaterialTheme.typography.bodyMedium, color = onColor)
                }
            }
        }
    }
}

@Composable
private fun TypographyDetail(
    modifier: Modifier,
    typography: ShowkaseBrowserTypography,
    onBack: () -> Unit,
) {
    val style = typography.textStyle.onDark()
    Column(modifier) {
        DetailHeader(typography.typographyName, typography.typographyGroup, onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("The quick brown fox jumps over the lazy dog", style = style)
            Text("0123456789", style = style)
            Text(
                "fontSize ${style.fontSize} · lineHeight ${style.lineHeight} · " +
                    "fontWeight ${style.fontWeight}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Token styles carry Color.Unspecified, but a hardcoded style would bypass the theme —
// resolve against the (always dark) scheme so every sample is legible.
@Composable
private fun TextStyle.onDark(): TextStyle = copy(color = MaterialTheme.colorScheme.onSurface)

private fun onColorFor(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black else Color.White

private fun ShowkaseBrowserColor.hex(): String = "#%06X".format(color.toArgb() and 0xFFFFFF)
