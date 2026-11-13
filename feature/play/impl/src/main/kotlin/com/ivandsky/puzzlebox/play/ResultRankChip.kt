package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.gameui.ResultHeroChip

/**
 * The Result hero's rank chip, "#12 of 340", that opens the hub through [onClick]; null for every rank but a
 * [ResultRank.Ranked] one, so the Today chip keeps its place when there is no place to show.
 */
internal fun resultRankChip(rank: ResultRank, onClick: () -> Unit): ResultHeroChip? =
    (rank as? ResultRank.Ranked)?.let { ResultHeroChip("result.hero.chip.rank", "#${it.position}", "of ${it.count}", onClick) }
