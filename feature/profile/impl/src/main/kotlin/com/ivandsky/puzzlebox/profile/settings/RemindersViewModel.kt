package com.ivandsky.puzzlebox.profile.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.reminder.NotificationAccess
import com.ivandsky.puzzlebox.data.reminder.ReminderRepository
import com.ivandsky.puzzlebox.model.ReminderSettings
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Settings Reminders block. The switch turns on only with notifications allowed:
 * otherwise it asks for the permission first, and a refusal leaves it off with the blocked line.
 */
internal class RemindersViewModel(
    private val repository: ReminderRepository,
    private val access: NotificationAccess,
) : ViewModel() {

    private val local = MutableStateFlow(Local(allowed = access.isAllowed()))

    val uiState: StateFlow<RemindersState> = combine(repository.settings, local) { stored, local ->
        RemindersState(
            on = stored.enabled && local.allowed,
            time = stored.time,
            // A stored "on" that the system no longer lets through reads as off with the reason beside it.
            blocked = local.blocked || (stored.enabled && !local.allowed),
            askPermission = local.askPermission,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RemindersState(on = false, time = ReminderSettings().time, blocked = false, askPermission = false),
    )

    fun onSetEnabled(on: Boolean) {
        if (!on) {
            local.update { it.copy(blocked = false) }
            store(false)
            return
        }
        val allowed = access.isAllowed()
        local.update { it.copy(allowed = allowed, blocked = false, askPermission = !allowed) }
        if (allowed) store(true)
    }

    /** The screen launched the system prompt, so the request is spent. */
    fun onPermissionAsked() {
        local.update { it.copy(askPermission = false) }
    }

    /** The prompt's answer. Access is read again: a grant with notifications still off somewhere is not a grant. */
    fun onPermissionResult(granted: Boolean) {
        val allowed = granted && access.isAllowed()
        local.update { it.copy(allowed = allowed, blocked = !allowed, askPermission = false) }
        if (allowed) store(true)
    }

    /** The player may have come back from system settings; an allowed state clears the blocked line. */
    fun onResume() {
        val allowed = access.isAllowed()
        local.update { it.copy(allowed = allowed, blocked = it.blocked && !allowed) }
    }

    fun onSetTime(time: LocalTime) {
        viewModelScope.launch { repository.setTime(time) }
    }

    private fun store(on: Boolean) {
        viewModelScope.launch { repository.setEnabled(on) }
    }

    private data class Local(
        val allowed: Boolean,
        val blocked: Boolean = false,
        val askPermission: Boolean = false,
    )
}
