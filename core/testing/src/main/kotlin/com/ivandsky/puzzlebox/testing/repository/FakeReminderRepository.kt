package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.reminder.ReminderRepository
import com.ivandsky.puzzlebox.model.ReminderSettings
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [ReminderRepository]; [current] is the stored choice, and a time is truncated to the minute like the real store. */
class FakeReminderRepository(initial: ReminderSettings = ReminderSettings()) : ReminderRepository {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<ReminderSettings> = state.asStateFlow()

    val current: ReminderSettings get() = state.value

    override suspend fun setEnabled(enabled: Boolean) = state.update { it.copy(enabled = enabled) }

    override suspend fun setTime(time: LocalTime) = state.update { it.copy(time = time.withSecond(0).withNano(0)) }
}
