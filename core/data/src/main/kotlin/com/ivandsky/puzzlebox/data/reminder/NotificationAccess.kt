package com.ivandsky.puzzlebox.data.reminder

/** Whether a notification may be posted right now: the player has not switched them off anywhere the app can see. */
interface NotificationAccess {

    fun isAllowed(): Boolean
}
