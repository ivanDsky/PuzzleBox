package com.ivandsky.puzzlebox.data.reminder

import com.ivandsky.puzzlebox.datastore.ReminderPrefsDataSource
import com.ivandsky.puzzlebox.model.ReminderSettings
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/** [ReminderRepository] on the reminder preferences file. */
class OfflineReminderRepository(
    private val source: ReminderPrefsDataSource,
) : ReminderRepository {

    override val settings: Flow<ReminderSettings> = source.settings

    override suspend fun setEnabled(enabled: Boolean) = source.setEnabled(enabled)

    override suspend fun setTime(time: LocalTime) = source.setTime(time)
}
