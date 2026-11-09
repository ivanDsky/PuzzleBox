package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.domain.ObserveMyDuels
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/**
 * How many duels need me (my move, a reveal ready, an invite), for the Play tab badge; 0 while signed out,
 * which [ObserveMyDuels] already reports as an empty list. Reads the same use case as the rows, so the badge
 * and the list never disagree.
 */
@Composable
fun rememberDuelsNeedYou(): Int {
    val observeMyDuels = koinInject<ObserveMyDuels>()
    val needYou = remember(observeMyDuels) { observeMyDuels().map { it.needYou } }
    val count by needYou.collectAsStateWithLifecycle(initialValue = 0)
    return count
}
