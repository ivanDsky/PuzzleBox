package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInResultPoster
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.EventListener
import retrofit2.Retrofit

/**
 * The production poster of the player's result to LinkedIn over [baseUrl], with a client of its own. Tests
 * build it through here too, so they run the wire the app runs. [zone] is the device's timezone, which the
 * `x-li-track` header reports.
 */
fun linkedInResultPoster(
    baseUrl: String,
    session: LinkedInSessionCookies,
    zone: () -> ZoneId = ZoneId::systemDefault,
): LinkedInResultPoster {
    val client = linkedInSessionClient().newBuilder()
        .eventListenerFactory(EventListener.Factory { RequestWrittenListener })
        .build()
    // No converter: bodies go out and come in raw.
    val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .build()
        .create(LinkedInPostApi::class.java)
    return SduiLinkedInResultPoster(api, session, zone)
}

/** Sets the [AtomicBoolean] a call carries as its tag once its whole request body is on the wire. */
private object RequestWrittenListener : EventListener() {
    override fun requestBodyEnd(call: Call, byteCount: Long) {
        call.request().tag(AtomicBoolean::class.java)?.set(true)
    }
}
