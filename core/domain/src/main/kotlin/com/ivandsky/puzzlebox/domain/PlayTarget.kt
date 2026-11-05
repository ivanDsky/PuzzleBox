package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId

/**
 * The game "Play today" opens (A13, A15, SC11): the first game with an unfinished save,
 * else the first unsolved one, else the first game. Null only for an empty set.
 */
fun List<TodayEntry>.playTarget(): GameId? = (
    firstOrNull { it.status is TodayStatus.InProgress }
        ?: firstOrNull { it.status !is TodayStatus.Solved }
        ?: firstOrNull()
    )?.game
