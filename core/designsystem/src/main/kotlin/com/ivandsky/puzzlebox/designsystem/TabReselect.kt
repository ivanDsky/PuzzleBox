package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Fires once when the user taps the selected tab while it sits at its root. A tab root
 * listens ([OnTabReselect]) and scrolls its list to the top; an
 * in-route detail that hides its list listens instead and closes back to it. The event is fire and
 * forget: no replay, dropped when nothing listens, so a root that composes later never
 * scrolls for an old tap. The app provides the live flow; the default never fires.
 */
val LocalTabReselect = staticCompositionLocalOf<SharedFlow<Unit>> { MutableSharedFlow() }

/**
 * Runs [action] on each tab reselect while this is composed. The latest [action] is used, so a
 * lambda that captures changing state needs no key.
 */
@Composable
fun OnTabReselect(action: suspend () -> Unit) {
    val reselect = LocalTabReselect.current
    val latest by rememberUpdatedState(action)
    LaunchedEffect(reselect) { reselect.collect { latest() } }
}

@PuzzleBoxPreviews
@Composable
private fun OnTabReselectPreview() {
    PuzzleBoxTheme {
        val list = rememberLazyListState()
        OnTabReselect { list.animateScrollToItem(0) }
        LazyColumn(state = list) { items(20) { Text("Row $it") } }
    }
}
