package com.ivandsky.puzzlebox.profile.linkedin

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

private const val LOGIN_URL = "https://www.linkedin.com/login"

/**
 * LinkedIn's own login page in a web view; the player types the password into LinkedIn, not
 * into the app. [onPageFinished] fires after every finished page and carries nothing: no URL,
 * no cookie. The page is only shown. No JavaScript interface is added and no script is run in it.
 */
@Composable
internal fun LinkedInLoginWebView(
    onPageFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageFinished by rememberUpdatedState(onPageFinished)
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // AndroidView leaves WRAP_CONTENT, which puts the web view in content-height mode
                // where CSS 100dvh resolves to 0, and LinkedIn's login page then paints blank.
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // The login page does not work without its own scripts and storage.
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                val view = this
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(view, true)
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) = pageFinished()
                }
                loadUrl(LOGIN_URL)
            }
        },
        modifier = modifier.testTag("linkedin.login.webview"),
        onRelease = { it.destroy() },
    )
}
