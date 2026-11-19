package com.ivandsky.puzzlebox.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.gameui.trainer.TrainerRegistry
import com.ivandsky.puzzlebox.play.TrainSlot
import com.ivandsky.puzzlebox.train.tab.TrainTab
import org.koin.compose.koinInject

/**
 * The Games screen's Train tab from the train module, or null while no game has a trainer so Games lists no Train tab.
 * The tab follows the [TrainerRegistry], not a flag of its own: each game binds its trainer adapter only while its own
 * flag is on.
 */
internal fun trainSlotOrNull(registry: TrainerRegistry): TrainSlot? =
    if (registry.isEmpty) null else TrainSlot(content = { TrainTab() })

/** [trainSlotOrNull] for the app's [TrainerRegistry], kept for as long as that registry is the same one. */
@Composable
internal fun rememberTrainSlot(): TrainSlot? {
    val registry = koinInject<TrainerRegistry>()
    return remember(registry) { trainSlotOrNull(registry) }
}
