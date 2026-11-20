package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.runtime.staticCompositionLocalOf
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.model.GameId

/**
 * The accent slot of a game: what paints a game's band or chip in the recap. The route provides each game's own brand
 * ([com.ivandsky.puzzlebox.gameui.GameBrand]); a game nothing is provided for reads Neutral, like an unregistered one.
 */
internal val LocalGameAccent = staticCompositionLocalOf<(GameId) -> AccentSlot> { { AccentSlot.Neutral } }
