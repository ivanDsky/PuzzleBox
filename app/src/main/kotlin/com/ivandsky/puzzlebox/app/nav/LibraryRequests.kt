package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.GamesTab
import com.ivandsky.puzzlebox.play.LibraryRequest
import com.ivandsky.puzzlebox.play.api.PlayKey

/**
 * The one-shot request for Games: a game page or a section, made from another tab. Each verb
 * goes to Games with [openOwner] (so back returns to Today) and then stores the request;
 * Games reads [pending], acts, and calls [take]. [pending] is Compose state, so an entry that
 * reads it recomposes when it changes. The holder is saved ([Saver]), like the code draft.
 */
internal class LibraryRequests(initial: LibraryRequest? = null) {
    var pending: LibraryRequest? by mutableStateOf(initial)
        private set

    fun openGamePage(navigator: Navigator, gameId: String) = open(navigator, LibraryRequest.GamePage(gameId))

    fun openGamesSection(navigator: Navigator, tab: GamesTab) = open(navigator, LibraryRequest.Section(tab))

    fun take() {
        pending = null
    }

    private fun open(navigator: Navigator, request: LibraryRequest) {
        navigator.openOwner(PlayKey)
        pending = request
    }

    companion object {
        private const val PAGE = "page:"
        private const val SECTION = "section:"

        /**
         * Wire shape: `page:<gameId>`, `section:<GamesTab.name>`, or `""` for none. The app owns
         * it, so renaming a [GamesTab] entry changes what an old saved state holds; an
         * unreadable value restores as no request.
         */
        val Saver: Saver<LibraryRequests, String> = Saver(
            save = { requests ->
                when (val request = requests.pending) {
                    is LibraryRequest.GamePage -> PAGE + request.gameId
                    is LibraryRequest.Section -> SECTION + request.tab.name
                    null -> ""
                }
            },
            restore = { saved ->
                LibraryRequests(
                    when {
                        saved.startsWith(PAGE) -> LibraryRequest.GamePage(saved.removePrefix(PAGE))
                        saved.startsWith(SECTION) ->
                            GamesTab.entries.firstOrNull { it.name == saved.removePrefix(SECTION) }
                                ?.let(LibraryRequest::Section)
                        else -> null
                    },
                )
            },
        )
    }
}

@Composable
internal fun rememberLibraryRequests(): LibraryRequests =
    rememberSaveable(saver = LibraryRequests.Saver) { LibraryRequests() }
