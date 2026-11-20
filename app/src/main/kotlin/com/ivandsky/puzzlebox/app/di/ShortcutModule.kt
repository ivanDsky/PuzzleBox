package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.shortcut.AndroidShortcutPublisher
import com.ivandsky.puzzlebox.app.shortcut.ShortcutAppHooks
import com.ivandsky.puzzlebox.app.shortcut.ShortcutGame
import com.ivandsky.puzzlebox.app.shortcut.ShortcutPublisher
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.play.flags.PlayFlag
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The launcher long-press menu: the system publisher and the start hook, which reads the flag here and nowhere else. */
val shortcutModule = module {
    single<ShortcutPublisher> { AndroidShortcutPublisher(androidContext()) }
    single {
        ShortcutAppHooks(
            enabled = get<ReleaseFlags>().isEnabled(PlayFlag.AppShortcuts),
            // Resolved when the hook runs, off the main thread, so the app start never builds the registry for it.
            games = { get<GameRegistry>().all.map { ShortcutGame(it.definition.id, it.definition.displayName) } },
            publisher = get(),
            scope = get<CoroutineScope>(),
        )
    }
}
