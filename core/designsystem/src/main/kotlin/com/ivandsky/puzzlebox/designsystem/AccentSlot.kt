package com.ivandsky.puzzlebox.designsystem

/**
 * The design system's game hue slots (spec A1, identity §2.2): unnamed on purpose — a
 * game's brand picks one, so the design system never names a game. [Neutral] is the
 * fallback for a game with no brand; it derives from the ink and well roles. Resolve a
 * slot's colours with [colors] under [PuzzleBoxTheme]. A new game whose hue isn't here
 * adds exactly one slot (spec A25) plus its light/dark row in AccentTokens.kt.
 */
enum class AccentSlot { Blue, Orange, Violet, Teal, Pink, Neutral }
