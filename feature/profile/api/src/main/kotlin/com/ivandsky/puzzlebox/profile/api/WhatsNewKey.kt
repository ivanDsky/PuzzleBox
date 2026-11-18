package com.ivandsky.puzzlebox.profile.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What's new, a shell screen opened from Settings' About (flag WhatsNew). Serial name pinned for saved state. */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.WhatsNew")
data object WhatsNewKey : NavKey
