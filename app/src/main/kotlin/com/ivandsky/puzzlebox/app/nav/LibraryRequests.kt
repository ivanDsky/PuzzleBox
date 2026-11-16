package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.DuelPrefill
import com.ivandsky.puzzlebox.play.GamesTab
import com.ivandsky.puzzlebox.play.LibraryRequest
import com.ivandsky.puzzlebox.play.api.PlayKey

/**
 * The one-shot request for Games: a section, made from another tab. Each verb
 * goes to Games with [openOwner] (so back returns to Today) and then stores the request;
 * Games reads [pending], acts, and calls [take]. [pending] is Compose state, so an entry that
 * reads it recomposes when it changes. The holder is saved ([Saver]), like the code draft.
 */
internal class LibraryRequests(initial: LibraryRequest? = null) {
    var pending: LibraryRequest? by mutableStateOf(initial)
        private set

    fun openGamesSection(navigator: Navigator, tabSet: TabSet, tab: GamesTab) =
        open(navigator, tabSet, LibraryRequest.Section(tab))

    /** Games' Custom section, for what arrives from outside: shared text, or a link that is no code. */
    fun openCustom(navigator: Navigator, tabSet: TabSet) =
        openGamesSection(navigator, tabSet, GamesTab.Custom)

    /** Games' Duels tab with a challenge to [prefill]'s friend in its game pending there, from Result. */
    fun openChallenge(navigator: Navigator, tabSet: TabSet, prefill: DuelPrefill) =
        open(navigator, tabSet, LibraryRequest.Challenge(prefill))

    fun take() {
        pending = null
    }

    private fun open(navigator: Navigator, tabSet: TabSet, request: LibraryRequest) {
        navigator.openOwner(PlayKey, tabSet)
        pending = request
    }

    companion object {
        private const val SECTION = "section:"
        private const val CHALLENGE = "challenge:"

        /**
         * Wire shape: `section:<GamesTab.name>`, `challenge:<game>:<friend uid>:<display name>`, or `""` for none.
         * The app owns it, so renaming a [GamesTab] entry changes what an old saved state holds; an
         * unreadable value restores as no request. The name comes last, so it may hold colons; a game id and a
         * uid never do.
         */
        val Saver: Saver<LibraryRequests, String> = Saver(
            save = { requests ->
                when (val request = requests.pending) {
                    is LibraryRequest.Section -> SECTION + request.tab.name
                    is LibraryRequest.Challenge -> with(request.prefill) { "$CHALLENGE${game.value}:$friendUid:$friendName" }
                    null -> ""
                }
            },
            restore = { saved ->
                LibraryRequests(
                    when {
                        saved.startsWith(SECTION) ->
                            GamesTab.entries.firstOrNull { it.name == saved.removePrefix(SECTION) }
                                ?.let(LibraryRequest::Section)
                        saved.startsWith(CHALLENGE) -> challengeOf(saved.removePrefix(CHALLENGE))
                        else -> null
                    },
                )
            },
        )

        private fun challengeOf(wire: String): LibraryRequest.Challenge? {
            val (game, uid, name) = wire.split(':', limit = 3).takeIf { it.size == 3 } ?: return null
            if (game.isEmpty() || uid.isEmpty()) return null
            return LibraryRequest.Challenge(DuelPrefill(friendUid = uid, friendName = name, game = GameId(game)))
        }
    }
}

@Composable
internal fun rememberLibraryRequests(): LibraryRequests =
    rememberSaveable(saver = LibraryRequests.Saver) { LibraryRequests() }
