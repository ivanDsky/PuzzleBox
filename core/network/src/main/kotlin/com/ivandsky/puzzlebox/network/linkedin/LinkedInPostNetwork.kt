package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInResultPoster
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.EventListener
import retrofit2.Retrofit

/**
 * The production poster of the player's result to LinkedIn over [baseUrl], with a client of its own. Tests
 * build it through here too, so they run the wires the app runs: `updateGameState`, and the voyager save for
 * a play that carries a voyager board. [clock] and [zone] are the instant and the device's timezone the `x-li-track` header reports.
 */
fun linkedInResultPoster(
    baseUrl: String,
    session: LinkedInSessionCookies,
    clock: () -> Instant,
    zone: () -> ZoneId = ZoneId::systemDefault,
): LinkedInResultPoster {
    val client = linkedInSessionClient().newBuilder()
        .eventListenerFactory(EventListener.Factory { RequestWrittenListener })
        .build()
    // No converter: bodies go out and come in raw.
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .build()
    val api = retrofit.create(LinkedInPostApi::class.java)
    val graphql = VoyagerGraphql(retrofit.create(LinkedInAccountApi::class.java), session)
    return TwoWireLinkedInResultPoster(
        sdui = SduiLinkedInResultPoster(api, session, clock, zone),
        voyager = VoyagerGameSave(api, graphql, session, clock, zone),
    )
}

/** Sets the [AtomicBoolean] a call carries as its tag once its whole request body is on the wire. */
private object RequestWrittenListener : EventListener() {
    override fun requestBodyEnd(call: Call, byteCount: Long) {
        call.request().tag(AtomicBoolean::class.java)?.set(true)
    }
}
