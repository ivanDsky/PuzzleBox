package com.ivandsky.puzzlebox.compete.tab

import com.ivandsky.puzzlebox.compete.domain.DuelRow
import com.ivandsky.puzzlebox.compete.domain.DuelRowKind
import com.ivandsky.puzzlebox.compete.domain.needsYou
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The duel the tablet's pane shows when nobody has picked one: the first that needs the player, else the most recent.
 * A row carries no start time, so the latest deadline among the duels still running stands for the most recent (a
 * duel is open for a fixed span); with only finished duels the first row shows. Null when there are none.
 */
internal fun defaultSelection(rows: List<DuelRow>): ShareCode? =
    (rows.firstOrNull { it.kind.needsYou() } ?: rows.filter { it.kind !is DuelRowKind.Finished }.maxByOrNull { it.deadline } ?: rows.firstOrNull())
        ?.code

/** [pinned] while it is still one of [rows], else [defaultSelection]: a pick holds until its duel leaves the list. */
internal fun resolveSelection(rows: List<DuelRow>, pinned: ShareCode?): ShareCode? =
    pinned?.takeIf { code -> rows.any { it.code == code } } ?: defaultSelection(rows)
