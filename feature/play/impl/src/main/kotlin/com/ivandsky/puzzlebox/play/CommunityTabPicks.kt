package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.LocalSelectedRowCode

/**
 * The tablet Community's current tab and one saved pick per tab, as [CommunityPick.encoded] text so a rotation and
 * process death keep them ([rememberCommunityTabPicks]). A tab with no pick of its own shows its [default]; a row picks
 * only its own tab's pick, through [ProvideFor], so switching tab shows that tab's pick and highlights a row only in
 * the tab it was picked in.
 */
@Stable
internal class CommunityTabPicks(
    private val default: State<(CommunityTab) -> CommunityPick?>,
    tab: CommunityTab = CommunityTab.NewToday,
    initial: Map<CommunityTab, String> = emptyMap(),
) {
    /** The tab the list pane shows; the right pane follows its pick. */
    var tab by mutableStateOf(tab)
        private set

    private val saved = mutableStateMapOf<CommunityTab, String>().apply { putAll(initial) }

    private val pickers = CommunityTab.entries.associateWith { tab ->
        object : CommunityPicker {
            override fun puzzle(code: String) { saved[tab] = CommunityPick.Puzzle(code).encoded }
            override fun published(code: String) { saved[tab] = CommunityPick.Published(code).encoded }
        }
    }

    fun select(tab: CommunityTab) {
        this.tab = tab
    }

    /** [tab]'s pick: the one saved for it, else its default (null: the pane is empty). */
    fun pickOf(tab: CommunityTab): CommunityPick? = saved[tab]?.let(CommunityPick::parse) ?: default.value(tab)

    /** Forgets [tab]'s pick, which returns it to its default. */
    fun clear(tab: CommunityTab) {
        saved.remove(tab)
    }

    /** Draws [content], a row list of [tab], whose rows pick for [tab] alone and which highlights [tab]'s pick alone. */
    @Composable
    fun ProvideFor(tab: CommunityTab, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalCommunityPicker provides pickers.getValue(tab),
            LocalSelectedRowCode provides pickOf(tab)?.code,
            content = content,
        )
    }

    internal companion object {
        fun saver(default: State<(CommunityTab) -> CommunityPick?>) = Saver<CommunityTabPicks, List<String>>(
            save = { picks -> listOf(picks.tab.name) + picks.saved.flatMap { (tab, text) -> listOf(tab.name, text) } },
            restore = { list ->
                CommunityTabPicks(
                    default = default,
                    tab = CommunityTab.valueOf(list.first()),
                    initial = list.drop(1).chunked(2).associate { (tab, text) -> CommunityTab.valueOf(tab) to text },
                )
            },
        )
    }
}

/** The tablet Community's [CommunityTabPicks], saved across a restore; [default] is each tab's pick before one is made. */
@Composable
internal fun rememberCommunityTabPicks(default: (CommunityTab) -> CommunityPick?): CommunityTabPicks {
    val latest = rememberUpdatedState(default)
    return rememberSaveable(saver = CommunityTabPicks.saver(latest)) { CommunityTabPicks(latest) }
}
