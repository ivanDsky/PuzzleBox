package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.common.di.commonModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.dsl.koinApplication

/**
 * The app-lifetime [CoroutineScope] must end with its Koin graph, or an eager collector
 * on it (the queens auto-cross flag) can outlive the graph and read a closed scope.
 */
class AppScopeLifetimeTest {

    @Test
    fun `the app scope is cancelled when its Koin graph closes`() {
        val koin = koinApplication { modules(commonModule, appModule) }.koin
        val job = koin.get<CoroutineScope>().coroutineContext[Job]!!
        assertTrue("the scope is live while the graph is open", job.isActive)

        koin.close()

        assertTrue("the scope outlived its graph", job.isCancelled)
        assertFalse(job.isActive)
    }
}
