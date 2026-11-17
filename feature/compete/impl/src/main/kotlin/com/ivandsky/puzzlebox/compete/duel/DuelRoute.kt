package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

private const val MILLIS_PER_MINUTE = 60_000L

/** The duel at [code] as a screen of its own: a "Duel" bar with Back over [DuelContent]. */
@Composable
internal fun DuelRoute(code: String, navigator: Navigator, layout: PlayLayout) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CompactTopBar(
            title = "Duel",
            navigationIcon = {
                IconButton(onClick = navigator::pop, modifier = Modifier.testTag("duel.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back")
                }
            },
        )
        DuelContent(code, navigator, layout, onClose = navigator::pop, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/** The time for the countdown text, read from [time]'s clock and refreshed whenever the minutes left change, until [closesAt] has passed. */
@Composable
internal fun rememberCountdownNow(time: CompeteTime, closesAt: Instant): State<Instant> = produceState(time.now(), time, closesAt) {
    while (true) {
        val current = time.now()
        value = current
        val left = Duration.between(current, closesAt).toMillis()
        if (left <= 0L) break
        delay(left % MILLIS_PER_MINUTE + 1)
    }
}
