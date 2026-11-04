package com.ivandsky.puzzlebox.games.sample

import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.games.sample.feed.FixtureSampleDailySource
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.bind
import org.koin.dsl.module

val samplePlugin: GamePlugin = GamePlugin(definition = SampleDefinition)

/** The id qualifier stops a later game's `single { ... }` from overwriting this plugin while `getAll` still collects it. */
val sampleModule: Module = module {
    single(qualifier = qualifier(SampleDefinition.id.value)) { samplePlugin }
    single { FixtureSampleDailySource() } bind DailySource::class
}
