package com.ivandsky.puzzlebox.app.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ticket 24: the provider must keep the failure CLASS alive — the profile copy is
 * chosen per class, and only NETWORK may claim a connection problem.
 */
class GoogleIdTokenProviderReasonTest {

    @Test
    fun `cancellation maps to CANCELLED`() {
        assertEquals(
            GoogleIdToken.Unavailable.Reason.CANCELLED,
            unavailableReason(GetCredentialCancellationException()),
        )
    }

    @Test
    fun `no credential maps to NO_CREDENTIAL`() {
        assertEquals(
            GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL,
            unavailableReason(NoCredentialException()),
        )
    }

    @Test
    fun `api availability failure maps to PROVIDER_UNAVAILABLE`() {
        assertEquals(
            GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE,
            unavailableReason(GetCredentialUnsupportedException()),
        )
    }

    @Test
    fun `parsing failure maps to MALFORMED`() {
        assertEquals(
            GoogleIdToken.Unavailable.Reason.MALFORMED,
            unavailableReason(GoogleIdTokenParsingException(IllegalStateException("bad payload"))),
        )
    }

    @Test
    fun `io failure maps to NETWORK`() {
        assertEquals(
            GoogleIdToken.Unavailable.Reason.NETWORK,
            unavailableReason(IOException("offline")),
        )
    }

    @Test
    fun `anything else maps to OTHER`() {
        assertEquals(
            GoogleIdToken.Unavailable.Reason.OTHER,
            unavailableReason(IllegalStateException("no provider")),
        )
    }

    @Test
    fun `other GetCredentialException subclass falls through to PROVIDER_UNAVAILABLE`() {
        // CANCELLED and NO_CREDENTIAL extend GetCredentialException; an unrelated
        // public subclass must not land in either of those more specific buckets.
        assertEquals(
            GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE,
            unavailableReason(GetCredentialInterruptedException()),
        )
    }
}
