package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.testing.cloud.FakeSyncStateStore
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignedInOwnerScopeTest {

    private val a = Identity(uid = "A", displayName = "Ann", photoUrl = null)
    private val b = Identity(uid = "B", displayName = "Bob", photoUrl = null)

    private suspend fun TestScope.scoped(
        identity: FakeIdentityService,
        store: FakeSyncStateStore,
        block: suspend (OwnerScope) -> Unit,
    ) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        block(SignedInOwnerScope(identity, store, scope))
        scope.cancel()
    }

    @Test
    fun `signing in, out and in as another account moves visible, writer and the last uid`() = runTest {
        val identity = FakeIdentityService()
        val store = FakeSyncStateStore()
        scoped(identity, store) { scope ->
            assertNull(scope.visible.value)
            assertNull(scope.writer())

            identity.currentIdentity = a
            assertEquals("A", scope.visible.value)
            assertEquals("A", scope.writer())
            assertEquals("A", store.lastUid.value)

            identity.currentIdentity = null
            assertEquals("A", scope.visible.value)
            assertNull(scope.writer())
            assertEquals("A", store.lastUid.value)

            identity.currentIdentity = b
            assertEquals("B", scope.visible.value)
            assertEquals("B", scope.writer())
            assertEquals("B", store.lastUid.value)
        }
    }

    @Test
    fun `signed out on a fresh install sees no filter`() = runTest {
        scoped(FakeIdentityService(), FakeSyncStateStore()) { scope ->
            assertNull(scope.visible.value)
            assertNull(scope.writer())
        }
    }

    @Test
    fun `signed out after a restart still sees the last account`() = runTest {
        val store = FakeSyncStateStore(lastUid = "A")
        scoped(FakeIdentityService(), store) { scope ->
            assertEquals("A", scope.visible.value)
            assertNull(scope.writer())
        }
    }

    @Test
    fun `a session restored at start is recorded as the last uid`() = runTest {
        val store = FakeSyncStateStore()
        scoped(FakeIdentityService(initial = b), store) { scope ->
            assertEquals("B", scope.visible.value)
            assertEquals("B", scope.writer())
            assertEquals("B", store.lastUid.value)
        }
    }
}
