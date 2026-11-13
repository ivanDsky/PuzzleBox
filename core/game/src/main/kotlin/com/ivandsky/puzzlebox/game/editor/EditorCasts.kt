package com.ivandsky.puzzlebox.game.editor

import kotlin.reflect.KClass

/**
 * The editor as capability [C] (a [DraftResize], [DraftExport], [EditorShareCodec] or
 * [EditorNotation]), or null when it does not implement it. Erasure hides [D] from the check:
 * a game's editor implements every capability over its own [D], so [C] must be spelled over the
 * same [D]. A caller holding a `GameEditor<*>` spells [C] over [Any].
 */
// The only erased step: C may carry D (DraftResize<D>), which `as?` cannot check. It is sound
// because each editor implements its capability interfaces over its own D.
@Suppress("UNCHECKED_CAST")
inline fun <D : Any, reified C : Any> GameEditor<D>.capability(): C? = this as? C

/** This handle as a draft of [editor]'s type; null when it holds another editor (identity check). */
fun <D : Any> EditorDraft<*>.narrowTo(editor: GameEditor<D>): EditorDraft<D>? =
    if (this.editor === editor) uncheckedDraft() else null

/** This handle as a draft of [editorType]'s draft type; null when its editor is not an instance of it. */
fun <D : Any> EditorDraft<*>.narrowTo(editorType: KClass<out GameEditor<D>>): EditorDraft<D>? =
    if (editorType.isInstance(editor)) uncheckedDraft() else null

// Erased: the caller has just proven the editor is a GameEditor<D>, and an EditorDraft's state is
// always of its editor's draft type, so the state is a D too.
@Suppress("UNCHECKED_CAST")
private fun <D : Any> EditorDraft<*>.uncheckedDraft(): EditorDraft<D> = this as EditorDraft<D>
