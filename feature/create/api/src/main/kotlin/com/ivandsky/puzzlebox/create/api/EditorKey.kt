package com.ivandsky.puzzlebox.create.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The Studio editing the stored draft [draftId] (a [com.ivandsky.puzzlebox.data.model.Draft] id). */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Editor")
data class EditorKey(val draftId: String) : NavKey
