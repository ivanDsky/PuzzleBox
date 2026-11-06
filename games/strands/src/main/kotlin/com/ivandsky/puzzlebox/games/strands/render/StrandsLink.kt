package com.ivandsky.puzzlebox.games.strands.render

import com.ivandsky.puzzlebox.games.strands.Coord

/** A connector polyline through [cells], drawn behind the letters. */
data class StrandsLink(val cells: List<Coord>, val kind: LinkKind)
