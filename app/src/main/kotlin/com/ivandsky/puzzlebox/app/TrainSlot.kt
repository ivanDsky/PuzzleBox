package com.ivandsky.puzzlebox.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.trainer.TrainerRegistry
import com.ivandsky.puzzlebox.play.TrainSlot
import com.ivandsky.puzzlebox.train.tab.trainPane
import com.ivandsky.puzzlebox.train.tab.trainTab
import org.koin.compose.koinInject

/**
 * The Games screen's Train tab from the train module, or null while no game has a trainer so Games lists no Train tab.
 * The tab follows the [TrainerRegistry], not a flag of its own: each game binds its trainer adapter only while its own
 * flag is on. On the two-pane [layout] the tab body is the technique list and the open technique's round is the right
 * [TrainSlot.pane]; on the phone the round covers the list inside the tab body, and the pane is never drawn.
 */
internal fun trainSlotOrNull(registry: TrainerRegistry, layout: PlayLayout): TrainSlot? =
    if (registry.isEmpty) null else TrainSlot(content = trainTab(twoPane = layout == PlayLayout.TwoPane), pane = trainPane())

/** [trainSlotOrNull] for the app's [TrainerRegistry], kept for as long as that registry and [layout] are the same. */
@Composable
internal fun rememberTrainSlot(layout: PlayLayout): TrainSlot? {
    val registry = koinInject<TrainerRegistry>()
    return remember(registry, layout) { trainSlotOrNull(registry, layout) }
}
