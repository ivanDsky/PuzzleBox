package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

/**
 * The page's search field: [query] as typed, a clear button once there is text, the keyboard's Search only putting the
 * keyboard away (the list filters as you type). With [focus] it takes focus when the page opens.
 */
@Composable
internal fun SeeAllSearchField(query: String, onQuery: (String) -> Unit, focus: Boolean, modifier: Modifier = Modifier) {
    val requester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(focus) { if (focus) requester.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.focusRequester(requester).testTag("seeall.search"),
        placeholder = { Text("Search title, author or game", maxLines = 1) },
        leadingIcon = { Icon(PuzzleIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }, modifier = Modifier.testTag("seeall.search.clear")) {
                    Icon(PuzzleIcons.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = AppShapes.small,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}
