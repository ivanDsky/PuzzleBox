package com.ivandsky.puzzlebox.data.reminder

import com.ivandsky.puzzlebox.model.ReminderMessage

/** Posts the daily reminder; a second post replaces the first, so at most one is ever showing. */
interface ReminderNotifier {

    fun show(message: ReminderMessage)
}
