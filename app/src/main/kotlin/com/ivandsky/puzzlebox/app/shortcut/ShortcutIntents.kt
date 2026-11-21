package com.ivandsky.puzzlebox.app.shortcut

import android.content.Context
import android.content.Intent
import com.ivandsky.puzzlebox.app.MainActivity
import com.ivandsky.puzzlebox.model.GameId

/** Builds a shortcut intent (launcher or widget) and reads its target back. Explicit, so the manifest needs no filter for it. */
object ShortcutIntents {
    const val ACTION: String = "com.ivandsky.puzzlebox.action.OPEN_SHORTCUT"
    const val EXTRA_GAME_ID = "com.ivandsky.puzzlebox.extra.SHORTCUT_GAME_ID"
    const val EXTRA_TODAY = "com.ivandsky.puzzlebox.extra.SHORTCUT_TODAY"

    fun intent(context: Context, target: ShortcutTarget): Intent =
        Intent(ACTION).setClass(context, MainActivity::class.java).apply {
            when (target) {
                ShortcutTarget.UpNext -> Unit
                is ShortcutTarget.Game -> putExtra(EXTRA_GAME_ID, target.id.value)
                ShortcutTarget.Today -> putExtra(EXTRA_TODAY, true)
            }
        }

    /** Null for any other intent, or for a game shortcut with a blank id. */
    fun targetOf(intent: Intent?): ShortcutTarget? {
        if (intent?.action != ACTION) return null
        if (intent.getBooleanExtra(EXTRA_TODAY, false)) return ShortcutTarget.Today
        val id = intent.getStringExtra(EXTRA_GAME_ID) ?: return ShortcutTarget.UpNext
        return id.takeIf { it.isNotBlank() }?.let { ShortcutTarget.Game(GameId(it)) }
    }
}
