package com.ivandsky.puzzlebox.profile.linkedin

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val LINKEDIN_ORIGIN = "https://www.linkedin.com"

/**
 * [cookies] is lazy: `CookieManager.getInstance()` initialises the web view provider, so it runs only on first use, never at Koin resolution.
 *
 * The one place the app reads the login cookies. They are read per call and leave only inside
 * [LinkedInAccountSession], which redacts itself; nothing here logs, stores or formats a cookie value.
 */
internal class WebViewLinkedInSession(
    private val cookies: () -> CookieManager,
    private val userAgent: () -> String,
    private val dispatchers: DispatcherProvider,
) : LinkedInSessionCookies {

    // The web view APIs want the main thread, the user agent lookup included.
    override suspend fun current(): LinkedInAccountSession? = withContext(dispatchers.main) {
        sessionFrom(cookies().getCookie(LINKEDIN_ORIGIN), userAgent())
    }

    override suspend fun clear(): Unit = withContext(dispatchers.main) {
        val store = cookies()
        // Not cancellable on purpose: a wipe that has started also flushes and clears web storage,
        // even when the caller goes away while the cookie store is still answering.
        suspendCoroutine { continuation -> store.removeAllCookies { continuation.resume(Unit) } }
        store.flush()
        WebStorage.getInstance().deleteAllData()
    }
}
