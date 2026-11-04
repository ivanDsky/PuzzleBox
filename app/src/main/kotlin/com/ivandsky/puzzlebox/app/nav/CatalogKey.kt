package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Detail route under Profile: the design-system catalog (app-owned — the browser is the
 * app's debug source set). Serial name pinned to the pre-split class name (saved state).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Catalog")
data object CatalogKey : NavKey
