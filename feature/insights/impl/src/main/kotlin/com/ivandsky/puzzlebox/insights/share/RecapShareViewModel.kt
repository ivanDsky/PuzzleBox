package com.ivandsky.puzzlebox.insights.share

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.restoring
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Sharing one recap: it names the image ([RecapShareState.title], A22), writes the captured picture to the share
 * cache and hands the screen a chooser [requests] to start. While the first cloud restore is pulling the history, Share
 * does nothing (A23): the image would show a partial month. The check is made again when the tap arrives, not only
 * when the button was drawn.
 */
internal class RecapShareViewModel(
    private val period: RecapPeriod,
    identity: IdentityService,
    private val cloudSync: CloudSync,
    puzzleDay: GetPuzzleDay,
    private val renderer: RecapShareRenderer,
    private val uriOf: (File) -> Uri,
) : ViewModel() {

    private val title = period.shareTitle(puzzleDay())
    private val attempt = MutableStateFlow(Attempt.Idle)
    private val chooser = Channel<Intent>(Channel.BUFFERED)

    val state: StateFlow<RecapShareState> = combine(cloudSync.restoring, identity.current, attempt) { restoring, who, attempt ->
        RecapShareState(title, who?.displayName, restoring, sharing = attempt == Attempt.Sharing, failed = attempt == Attempt.Failed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RecapShareState(title, identity.current.value?.displayName, restoring = false, sharing = false, failed = false))

    /** The chooser to start, once per successful [share]. */
    val requests: Flow<Intent> = chooser.receiveAsFlow()

    /** Writes [image] for [content] and offers it; does nothing while the history is restoring or a share is under way. */
    fun share(image: ImageBitmap, content: RecapShareContent) {
        if (attempt.value == Attempt.Sharing) return
        viewModelScope.launch {
            if (cloudSync.restoring.first()) return@launch
            attempt.value = Attempt.Sharing
            attempt.value = try {
                val file = renderer.render(image, "recap-${period.id}")
                chooser.send(RecapShareIntent.create(uriOf(file), content.shareText()))
                Attempt.Idle
            } catch (_: IOException) {
                Attempt.Failed
            }
        }
    }

    private enum class Attempt { Idle, Sharing, Failed }
}
