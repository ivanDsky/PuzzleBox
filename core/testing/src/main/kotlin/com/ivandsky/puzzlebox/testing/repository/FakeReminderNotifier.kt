package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.reminder.ReminderNotifier
import com.ivandsky.puzzlebox.model.ReminderMessage

/** Records every message posted, oldest first. */
class FakeReminderNotifier : ReminderNotifier {

    val shown: MutableList<ReminderMessage> = mutableListOf()

    override fun show(message: ReminderMessage) {
        shown += message
    }
}
