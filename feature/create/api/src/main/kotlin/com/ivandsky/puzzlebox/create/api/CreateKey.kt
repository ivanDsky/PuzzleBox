package com.ivandsky.puzzlebox.create.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The Create tab's root: the author's drafts and the New puzzle action. */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Create")
data object CreateKey : NavKey
