package com.ivandsky.puzzlebox.app.shortcut

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ivandsky.puzzlebox.app.R

/** [ShortcutPublisher] over the shortcut manager: stable ids (`up_next`, `game_<id>`), labels from resources, icons from [ShortcutIcons]. */
class AndroidShortcutPublisher(private val context: Context) : ShortcutPublisher {

    /** Entries arrive ranked, so a launcher with fewer slots than entries drops the last games, never the menu (A9). */
    override fun publish(entries: List<ShortcutEntry>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        val kept = if (max > 0) entries.take(max) else entries
        ShortcutManagerCompat.setDynamicShortcuts(context, kept.mapNotNull(::shortcutOf))
    }

    override fun clear() {
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }

    /** Null for a Today entry: that request rides the widget's intents, never a launcher shortcut. */
    private fun shortcutOf(entry: ShortcutEntry): ShortcutInfoCompat? {
        val (id, shortLabel, longLabel, icon) = when (val target = entry.target) {
            ShortcutTarget.UpNext -> Look(
                UP_NEXT_ID,
                context.getString(R.string.shortcut_up_next_short),
                context.getString(R.string.shortcut_up_next_long),
                ShortcutIcons.upNext,
            )
            is ShortcutTarget.Game -> {
                val name = entry.gameName ?: target.id.value
                Look("$GAME_ID_PREFIX${target.id.value}", name, context.getString(R.string.shortcut_game_long, name), ShortcutIcons.forGame(target.id))
            }
            ShortcutTarget.Today -> return null
        }
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(ShortcutIntents.intent(context, entry.target))
            .setRank(entry.rank)
            .build()
    }

    private data class Look(val id: String, val shortLabel: String, val longLabel: String, val icon: Int)

    private companion object {
        const val UP_NEXT_ID = "up_next"
        const val GAME_ID_PREFIX = "game_"
    }
}
