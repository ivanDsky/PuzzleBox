package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/** Registers Play's keys (hub, game, result, friend card, friend game, friend result, duel leg, duel leg result, replay and replay compare) for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.playNavKeys() {
    subclass(PlayKey::class, PlayKey.serializer())
    subclass(GameKey::class, GameKey.serializer())
    subclass(ResultKey::class, ResultKey.serializer())
    subclass(FriendPuzzleKey::class, FriendPuzzleKey.serializer())
    subclass(CustomGameKey::class, CustomGameKey.serializer())
    subclass(CustomResultKey::class, CustomResultKey.serializer())
    subclass(MatchLegKey::class, MatchLegKey.serializer())
    subclass(MatchLegResultKey::class, MatchLegResultKey.serializer())
    subclass(ReplayKey::class, ReplayKey.serializer())
    subclass(ReplayCompareKey::class, ReplayCompareKey.serializer())
}
