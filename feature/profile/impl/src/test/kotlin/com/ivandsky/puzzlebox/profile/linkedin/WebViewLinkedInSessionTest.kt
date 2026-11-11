package com.ivandsky.puzzlebox.profile.linkedin

import android.app.Application
import android.webkit.CookieManager
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The adapter over Robolectric's cookie store: a session only with both cookies, none after a clear. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebViewLinkedInSessionTest {
    private val dispatcher = StandardTestDispatcher()

    private val lanes = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private val session = WebViewLinkedInSession({ CookieManager.getInstance() }, { USER_AGENT }, lanes)

    @Test
    fun `both cookies in the store give a session`() = runTest(dispatcher) {
        signIn()

        val current = session.current()

        assertNotNull(current)
        assertEquals("ajax:42", current!!.csrfToken)
        assertEquals(USER_AGENT, current.userAgent)
    }

    @Test
    fun `an empty store gives no session`() = runTest(dispatcher) {
        assertNull(session.current())
    }

    @Test
    fun `li_at alone in the store gives no session`() = runTest(dispatcher) {
        CookieManager.getInstance().setCookie(ORIGIN, "li_at=AQEDtoken")

        assertNull(session.current())
    }

    @Test
    fun `clear leaves no session`() = runTest(dispatcher) {
        signIn()
        assertNotNull(session.current())

        session.clear()

        assertNull(session.current())
    }

    private fun signIn() {
        CookieManager.getInstance().setCookie(ORIGIN, "li_at=AQEDtoken")
        CookieManager.getInstance().setCookie(ORIGIN, "JSESSIONID=\"ajax:42\"")
    }

    private companion object {
        const val ORIGIN = "https://www.linkedin.com"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Test"
    }
}
