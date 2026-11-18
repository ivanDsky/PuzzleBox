package com.ivandsky.puzzlebox.play.random

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry

/** The games New today can list: those with an editor, as the Community tab's filter offers them, each with its name and brand. */
internal fun communityGamesOf(games: GameRegistry, plays: GamePlayRegistry): List<RandomGame> =
    games.all.filter { it.editor != null }.map {
        val id = it.definition.id
        RandomGame(id, games.displayName(id), plays.brandOf(id))
    }
