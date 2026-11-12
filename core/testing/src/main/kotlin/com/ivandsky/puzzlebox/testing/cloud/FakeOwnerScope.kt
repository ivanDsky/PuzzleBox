package com.ivandsky.puzzlebox.testing.cloud

import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Settable [OwnerScope]: tests set [visibleOwner] and [writerUid] directly. */
class FakeOwnerScope(visible: String? = null, writer: String? = null) : OwnerScope {

    private val owner = MutableStateFlow(visible)

    /** What screens see; setting it re-emits [visible]. */
    var visibleOwner: String?
        get() = owner.value
        set(value) {
            owner.value = value
        }

    /** What [writer] returns. */
    @Volatile
    var writerUid: String? = writer

    override val visible: StateFlow<String?> = owner

    override fun writer(): String? = writerUid
}
