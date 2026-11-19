package com.ivandsky.puzzlebox.train.tab

import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.model.GameId

/**
 * What the Train pane shows: the trainer [games] in the registry's order, the [selected] one, its techniques with their
 * progress ([techniques], in the catalog's own order) and the technique whose round is [open], if any.
 */
internal data class TrainState(
    val games: List<GameId>,
    val selected: GameId,
    val techniques: List<TechniqueRow>,
    val open: OpenTechnique?,
)

/** One line of the technique list: the technique and what the player's progress says about it (New when never answered). */
internal data class TechniqueRow(
    val technique: TrainerTechnique,
    val progress: TechniqueProgress,
)

/**
 * The technique whose round is open, with the [game] that trains it. [attempt] counts the times a technique was opened,
 * so opening one again starts a new round while turning the device, which keeps the number, keeps the round.
 */
internal data class OpenTechnique(
    val game: TrainerGame,
    val technique: TrainerTechnique,
    val attempt: Int,
)
