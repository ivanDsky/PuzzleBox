package com.ivandsky.puzzlebox.compete.create.lineup

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch

private const val TEXT_MIME = "text/*"

/** A pasted text is only ever a code or a link, so a long clipboard cannot flood the field. */
private const val PASTE_CAP = 200

/**
 * "Paste": hands the clipboard's text to [onPaste] when the player taps it, and does nothing when the clipboard holds
 * none. The add sheet shows it while its field is empty.
 */
@Composable
internal fun PasteCodeButton(onPaste: (String) -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val manager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val scope = rememberCoroutineScope()
    TextButton(
        onClick = { scope.launch { clipboard.textOrNull(manager)?.let { onPaste(it.take(PASTE_CAP)) } } },
        modifier = modifier.testTag("duel.lineup.search.paste"),
    ) { Text("Paste") }
}

/** The clipboard's first item as text, or null when it is empty or holds something else; an image is never read. */
private suspend fun Clipboard.textOrNull(manager: ClipboardManager): String? {
    if (manager.primaryClipDescription?.hasMimeType(TEXT_MIME) != true) return null
    val data = getClipEntry()?.clipData ?: return null
    if (data.itemCount == 0) return null
    return data.getItemAt(0).text?.toString()
}
