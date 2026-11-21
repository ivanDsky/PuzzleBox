package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.shortcut.AndroidShortcutPublisher
import com.ivandsky.puzzlebox.app.shortcut.ShortcutAppHooks
import com.ivandsky.puzzlebox.app.shortcut.ShortcutGame
import com.ivandsky.puzzlebox.app.shortcut.ShortcutPublisher
import com.ivandsky.puzzlebox.game.GameRegistry
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The launcher long-press menu: the system publisher and the start hook. */
val shortcutModule = module {
    single<ShortcutPublisher> { AndroidShortcutPublisher(androidContext()) }
    single {
        ShortcutAppHooks(
            // Resolved when the hook runs, off the main thread, so the app start never builds the registry for it.
            games = { get<GameRegistry>().all.map { ShortcutGame(it.definition.id, it.definition.displayName) } },
            publisher = get(),
            scope = get<CoroutineScope>(),
        )
    }
}
