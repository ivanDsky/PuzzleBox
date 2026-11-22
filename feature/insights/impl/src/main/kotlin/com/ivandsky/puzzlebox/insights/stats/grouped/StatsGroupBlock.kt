package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection

/** One [group] of the page: its [main] chart block and its [side] block, either absent when the scope has nothing for it. */
internal data class StatsGroupBlock(val group: StatsGroup, val main: StatsSection?, val side: StatsSection?)
