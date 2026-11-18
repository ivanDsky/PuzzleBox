package com.ivandsky.puzzlebox.app.reminder

import com.ivandsky.puzzlebox.data.reminder.NotificationAccess
import com.ivandsky.puzzlebox.data.reminder.ReminderAlarm
import com.ivandsky.puzzlebox.data.reminder.ReminderNotifier
import com.ivandsky.puzzlebox.data.reminder.ReminderRepository
import com.ivandsky.puzzlebox.domain.ComputeNextReminder
import com.ivandsky.puzzlebox.domain.ComputeReminderMessage
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.model.ReminderSettings
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The only orchestrator of the daily reminder: it keeps one alarm armed for the next time the stored choice says, or none
 * while the choice is off or notifications are not allowed. [start] follows the stored choice, [refresh] reconciles once
 * (the app came to the foreground, the device booted, the app was updated) and [fire] is the alarm's wake-up: post if
 * today's puzzles are still open, then arm the next one whatever happened. [now] and [zone] are read on every compute so a
 * moved clock or time zone is never stale. [enabled] is the Reminders flag read once in DI: off, every entry point
 * returns at once, so the boot receiver costs a flag-off install nothing.
 */
class ReminderRunner(
    val enabled: Boolean,
    private val repository: ReminderRepository,
    private val access: NotificationAccess,
    private val alarm: ReminderAlarm,
    private val notifier: ReminderNotifier,
    private val todaySet: suspend () -> List<TodayEntry>,
    private val computeNext: ComputeNextReminder,
    private val computeMessage: ComputeReminderMessage,
    private val now: () -> Instant,
    private val zone: () -> ZoneId,
    private val scope: CoroutineScope,
) {

    /** Reconciles on every change of the stored choice, for the life of [scope]. */
    fun start() {
        if (!enabled) return
        scope.launch { repository.settings.collect { reconcile(it) } }
    }

    /** One reconcile, then [done]; [done] runs at once when disabled. */
    fun refresh(done: () -> Unit = {}) {
        if (!enabled) return done()
        scope.launch {
            try {
                reconcile(repository.settings.first())
            } finally {
                done()
            }
        }
    }

    /** The alarm fired: post when the choice is on and allowed and today's puzzles are open, then arm tomorrow; then [done]. */
    fun fire(done: () -> Unit = {}) {
        if (!enabled) return done()
        scope.launch {
            try {
                post()
            } finally {
                // Tomorrow's alarm is armed even when the post was cancelled, the read hung or the post threw.
                withContext(NonCancellable) {
                    try {
                        reconcile(repository.settings.first())
                    } finally {
                        done()
                    }
                }
            }
        }
    }

    private fun reconcile(settings: ReminderSettings) {
        if (settings.enabled && access.isAllowed()) {
            alarm.schedule(computeNext(now(), zone(), settings.time))
        } else {
            alarm.cancel()
        }
    }

    /** A hung or throwing read or post is a skipped day, never a crash of the receiver; only cancellation propagates. */
    private suspend fun post() {
        try {
            if (!repository.settings.first().let { it.enabled && access.isAllowed() }) return
            val entries = withTimeoutOrNull(READ_LIMIT_MS) { todaySet() } ?: return
            computeMessage(entries)?.let(notifier::show)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Skip the day: a missed reminder is cheaper than a wrong one.
        }
    }

    private companion object {
        /** The receiver has about 10 seconds; the rest is for posting and arming tomorrow. */
        const val READ_LIMIT_MS = 8_000L
    }
}
