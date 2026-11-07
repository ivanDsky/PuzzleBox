package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/** Registers Play's keys (hub, game, result, friend card and friend game) for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.playNavKeys() {
    subclass(PlayKey::class, PlayKey.serializer())
    subclass(GameKey::class, GameKey.serializer())
    subclass(ResultKey::class, ResultKey.serializer())
    subclass(FriendPuzzleKey::class, FriendPuzzleKey.serializer())
    subclass(CustomGameKey::class, CustomGameKey.serializer())
}
