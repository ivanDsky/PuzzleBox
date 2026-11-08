package com.ivandsky.puzzlebox.create.importtext

/**
 * What the [ImportSheet] reports, as one value so a screen hands the sheet its holder in one
 * argument ([of]) rather than six lambdas. [onEdit] and [onSubmit] carry the box's text,
 * [onClipboard] the clipboard's text (null when it holds none). The defaults do nothing.
 */
class ImportSheetActions(
    val onEdit: (String) -> Unit = {},
    val onSubmit: (String) -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onClipboard: (String?) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onDismissClipboard: () -> Unit = {},
) {
    companion object {
        /** The sheet's actions as [state] handles them. */
        fun of(state: ImportTextState) = ImportSheetActions(
            onEdit = state::edit,
            onSubmit = state::submit,
            onDismiss = state::dismiss,
            onClipboard = state::offerClipboard,
            onPaste = state::pasteClipboard,
            onDismissClipboard = state::dismissClipboard,
        )
    }
}
