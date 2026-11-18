package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.reminder.NotificationAccess

/** Steerable fake: flip [allowed] to play a player who switched notifications off. */
class FakeNotificationAccess(var allowed: Boolean = true) : NotificationAccess {

    override fun isAllowed(): Boolean = allowed
}
