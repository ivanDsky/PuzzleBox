package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Immutable

/** Where a filter chip shows its options. */
@Immutable
sealed interface FilterPresentation {
    /** Default: the chip opens its own AppModalBottomSheet. For a chip on a plain screen. */
    data object Sheet : FilterPresentation

    /**
     * For a chip that sits inside a sheet already. A click calls [onOpen] and nothing else: no sheet opens, the arrow stays collapsed,
     * onOpenChange is never called. The host swaps its content for the option page (SingleFilterPage, MultiFilterPage) and owns which
     * page is open. Hosts [remember] the instance.
     */
    class Inline(val onOpen: () -> Unit) : FilterPresentation
}
