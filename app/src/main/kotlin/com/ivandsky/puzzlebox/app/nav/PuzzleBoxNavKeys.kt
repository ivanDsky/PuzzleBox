package com.ivandsky.puzzlebox.app.nav

import androidx.savedstate.serialization.SavedStateConfiguration
import com.ivandsky.puzzlebox.create.api.createNavKeys
import com.ivandsky.puzzlebox.history.api.historyNavKeys
import com.ivandsky.puzzlebox.home.api.homeNavKeys
import com.ivandsky.puzzlebox.navigation.navKeySavedStateConfiguration
import com.ivandsky.puzzlebox.play.api.playNavKeys
import com.ivandsky.puzzlebox.profile.api.profileNavKeys

/**
 * The back stack's saved-state configuration: every NavKey the app can push, from every
 * feature api plus the app's own [CatalogKey]. A key missing here fails restore after
 * process death — NavKeySavedStateTest round-trips each one.
 */
internal val puzzleBoxNavKeys: SavedStateConfiguration = navKeySavedStateConfiguration {
    homeNavKeys()
    playNavKeys()
    createNavKeys()
    historyNavKeys()
    profileNavKeys()
    subclass(CatalogKey::class, CatalogKey.serializer())
}
