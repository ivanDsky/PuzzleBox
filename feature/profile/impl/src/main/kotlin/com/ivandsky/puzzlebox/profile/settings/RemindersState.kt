package com.ivandsky.puzzlebox.profile.settings

import java.time.LocalTime

/**
 * What the Settings Reminders block draws. [on] is the switch: the stored choice and notifications actually allowed.
 * [blocked] shows the "turn them on in system settings" line. [askPermission] is a one-shot request for the
 * notification permission; the screen launches the system prompt and reports back.
 */
internal data class RemindersState(
    val on: Boolean,
    val time: LocalTime,
    val blocked: Boolean,
    val askPermission: Boolean,
)
