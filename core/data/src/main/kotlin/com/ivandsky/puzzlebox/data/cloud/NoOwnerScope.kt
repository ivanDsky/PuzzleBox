package com.ivandsky.puzzlebox.data.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The flag-off scope: no filter and no stamp, so every reader behaves as it did before accounts. */
internal object NoOwnerScope : OwnerScope {
    override val visible: StateFlow<String?> = MutableStateFlow(null)

    override fun writer(): String? = null
}
