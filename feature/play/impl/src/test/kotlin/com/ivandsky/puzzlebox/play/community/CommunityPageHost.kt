package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.play.CommunityNewTodayPage
import org.koin.androidx.compose.koinViewModel

/** The live New today page ([CommunityNewTodayPage]) over the Koin [CommunityViewModel], inside [CommunityBound]. */
@Composable
internal fun CommunityPageHost(onOpen: (code: String) -> Unit = {}) {
    val viewModel: CommunityViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CommunityBound(viewModel, state, onOpen = { onOpen(it.value) }) { actions ->
        CommunityNewTodayPage(state, actions, rememberLazyListState(), onMake = null)
    }
}
