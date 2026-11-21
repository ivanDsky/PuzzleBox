package com.ivandsky.puzzlebox.app.shortcut

/** Listed games to the launcher menu: Up next first, then one entry per game in the order given. */
class BuildShortcutEntries {

    operator fun invoke(games: List<ShortcutGame>): List<ShortcutEntry> =
        listOf(ShortcutEntry(ShortcutTarget.UpNext, gameName = null, rank = 0)) +
            games.mapIndexed { index, game -> ShortcutEntry(ShortcutTarget.Game(game.id), game.displayName, rank = index + 1) }
}
