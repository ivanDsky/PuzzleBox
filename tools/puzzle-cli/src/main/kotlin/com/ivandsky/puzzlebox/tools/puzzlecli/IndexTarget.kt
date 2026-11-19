package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker
import java.time.ZoneId

/**
 * One game the index pass walks: its daily [source], the [walker] of the plugin with the same game
 * id, and the [zone] of that game's calendar (its cadence), which says what "yesterday" is.
 */
class IndexTarget(val source: DailySource, val walker: TechniqueWalker, val zone: ZoneId)
