package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.nav.Home.DebugOnly
import com.ivandsky.puzzlebox.app.nav.Home.PushedOn
import com.ivandsky.puzzlebox.app.nav.Home.Shell
import com.ivandsky.puzzlebox.app.nav.Home.TabRoot
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import kotlin.reflect.KClass

/**
 * Where a key lives and what chrome it wears: one [PlacementRow] per [NavKey] (ADR-0027,
 * `docs/adr/0027-navigation-placement.md`). Every NavKey needs a row; [placementOf] throws
 * for a key without one, and the Konsist P9 rule fails the build for a declaration that
 * has none.
 *
 * The homes describe the NavV2 information architecture, the target. They are not the live
 * tab set: with the flag off Profile is a tab and History is not, and the table still
 * describes the target. `NavigationState.tabs` is the runtime truth for which tabs exist;
 * code that needs the live tab set reads it there, never here.
 */
internal data class Placement(val home: Home, val chrome: Chrome)

/** One row of the table: [key]'s class and its [placement]. */
internal data class PlacementRow(val key: KClass<out NavKey>, val placement: Placement)

/** The four tabs of the NavV2 IA. A tab is named here, not by a key. */
internal enum class Tab { Today, Games, History, Create }

/** Where a key lives. */
internal sealed interface Home {
    /** The root of [tab]'s stack. */
    data class TabRoot(val tab: Tab) : Home

    /** Pushed on [tab]'s stack. */
    data class PushedOn(val tab: Tab) : Home

    /** Belongs to the shell, on no tab: Profile and Settings. */
    data object Shell : Home

    /** Exists in debug builds only. */
    data object DebugOnly : Home
}

/** What the shell draws around a key's screen. */
internal enum class Chrome {
    /** The bar, plus the avatar and gear once the shell actions land. */
    Browse,

    /** The bar, no avatar or gear. */
    Tool,

    /** No bar, no avatar or gear. */
    Immersive,
}

/** A list, not a map literal, so a test can see a key class written twice. */
internal val placementRows: List<PlacementRow> = listOf(
    PlacementRow(HomeKey::class, Placement(TabRoot(Tab.Today), Chrome.Browse)),
    PlacementRow(PlayKey::class, Placement(TabRoot(Tab.Games), Chrome.Browse)),
    PlacementRow(CreateKey::class, Placement(TabRoot(Tab.Create), Chrome.Browse)),
    PlacementRow(HistoryRootKey::class, Placement(TabRoot(Tab.History), Chrome.Browse)),
    PlacementRow(HistoryKey::class, Placement(PushedOn(Tab.History), Chrome.Browse)),
    PlacementRow(ProfileKey::class, Placement(Shell, Chrome.Tool)),
    PlacementRow(SettingsKey::class, Placement(Shell, Chrome.Tool)),
    PlacementRow(GameKey::class, Placement(PushedOn(Tab.Games), Chrome.Immersive)),
    PlacementRow(ResultKey::class, Placement(PushedOn(Tab.Games), Chrome.Immersive)),
    PlacementRow(CustomGameKey::class, Placement(PushedOn(Tab.Games), Chrome.Immersive)),
    PlacementRow(CustomResultKey::class, Placement(PushedOn(Tab.Games), Chrome.Immersive)),
    PlacementRow(FriendPuzzleKey::class, Placement(PushedOn(Tab.Games), Chrome.Browse)),
    PlacementRow(MatchLegKey::class, Placement(PushedOn(Tab.Games), Chrome.Immersive)),
    PlacementRow(MatchLegResultKey::class, Placement(PushedOn(Tab.Games), Chrome.Immersive)),
    PlacementRow(DuelKey::class, Placement(PushedOn(Tab.Games), Chrome.Browse)),
    PlacementRow(DuelRevealKey::class, Placement(PushedOn(Tab.Games), Chrome.Immersive)),
    PlacementRow(EditorKey::class, Placement(PushedOn(Tab.Create), Chrome.Tool)),
    PlacementRow(PublishedPuzzleKey::class, Placement(PushedOn(Tab.Create), Chrome.Browse)),
    PlacementRow(CatalogKey::class, Placement(DebugOnly, Chrome.Tool)),
)

/** [placementRows] by key class. */
internal val placements: Map<KClass<out NavKey>, Placement> = placementRows.associate { it.key to it.placement }

/** [key]'s row; a key without one is a programming error. */
internal fun placementOf(key: NavKey): Placement =
    checkNotNull(placements[key::class]) {
        "${key::class.qualifiedName} has no placement row. Add a PlacementRow to placementRows in " +
            "app/src/main/kotlin/com/ivandsky/puzzlebox/app/nav/Placement.kt (see docs/adr/0027-navigation-placement.md)."
    }
