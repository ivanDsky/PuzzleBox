package com.ivandsky.puzzlebox.profile.settings

import com.ivandsky.puzzlebox.model.ReminderSettings
import com.ivandsky.puzzlebox.testing.repository.FakeNotificationAccess
import com.ivandsky.puzzlebox.testing.repository.FakeReminderRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Reminders ViewModel: the switch and the notification permission. */
class RemindersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private var repository = FakeReminderRepository()
    private val access = FakeNotificationAccess(allowed = true)

    private fun TestScope.viewModel(stored: ReminderSettings = ReminderSettings()): RemindersViewModel {
        repository = FakeReminderRepository(stored)
        val vm = RemindersViewModel(repository = repository, access = access)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `switching on while notifications are allowed stores on`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onSetEnabled(true)
        advanceUntilIdle()

        assertTrue(repository.current.enabled)
        assertEquals(RemindersState(on = true, time = LocalTime.of(19, 0), blocked = false, askPermission = false), vm.uiState.value)
    }

    @Test
    fun `switching on while not allowed asks for the permission and stores nothing`() = runTest(dispatcher) {
        access.allowed = false
        val vm = viewModel()

        vm.onSetEnabled(true)
        advanceUntilIdle()

        assertFalse(repository.current.enabled)
        assertTrue(vm.uiState.value.askPermission)
        assertFalse(vm.uiState.value.on)
    }

    @Test
    fun `the request is spent once the prompt is launched`() = runTest(dispatcher) {
        access.allowed = false
        val vm = viewModel()
        vm.onSetEnabled(true)
        advanceUntilIdle()

        vm.onPermissionAsked()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.askPermission)
    }

    @Test
    fun `a granted permission stores on`() = runTest(dispatcher) {
        access.allowed = false
        val vm = viewModel()
        vm.onSetEnabled(true)
        access.allowed = true

        vm.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertTrue(repository.current.enabled)
        assertEquals(RemindersState(on = true, time = LocalTime.of(19, 0), blocked = false, askPermission = false), vm.uiState.value)
    }

    @Test
    fun `a denied permission leaves the switch off with the blocked line`() = runTest(dispatcher) {
        access.allowed = false
        val vm = viewModel()
        vm.onSetEnabled(true)

        vm.onPermissionResult(granted = false)
        advanceUntilIdle()

        assertFalse(repository.current.enabled)
        assertEquals(RemindersState(on = false, time = LocalTime.of(19, 0), blocked = true, askPermission = false), vm.uiState.value)
    }

    @Test
    fun `a withdrawn permission reads as off with the blocked line`() = runTest(dispatcher) {
        val vm = viewModel(ReminderSettings(enabled = true))
        assertTrue(vm.uiState.value.on)

        access.allowed = false
        vm.onResume()
        advanceUntilIdle()

        assertEquals(RemindersState(on = false, time = LocalTime.of(19, 0), blocked = true, askPermission = false), vm.uiState.value)
        assertTrue("the stored choice is kept", repository.current.enabled)
    }

    @Test
    fun `coming back with notifications allowed clears the blocked line`() = runTest(dispatcher) {
        access.allowed = false
        val vm = viewModel()
        vm.onSetEnabled(true)
        vm.onPermissionResult(granted = false)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.blocked)

        access.allowed = true
        vm.onResume()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.blocked)
        assertFalse("one tap turns it on, the switch does not flip by itself", vm.uiState.value.on)
    }

    @Test
    fun `switching off stores off and drops the blocked line`() = runTest(dispatcher) {
        val vm = viewModel(ReminderSettings(enabled = true))

        vm.onSetEnabled(false)
        advanceUntilIdle()

        assertFalse(repository.current.enabled)
        assertFalse(vm.uiState.value.on)
        assertFalse(vm.uiState.value.blocked)
    }

    @Test
    fun `a picked time is stored`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onSetTime(LocalTime.of(8, 30))
        advanceUntilIdle()

        assertEquals(LocalTime.of(8, 30), repository.current.time)
        assertEquals(LocalTime.of(8, 30), vm.uiState.value.time)
    }
}
