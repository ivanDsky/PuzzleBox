package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.model.GameSetting

/** The [GameSetting]s Queens declares; the app's [QueensSettings] binding stores them. */
object QueensSettingKeys {
    val AutoCross = GameSetting(QueensDefinition.id, "auto_cross", default = true)
}
