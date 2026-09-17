package com.mgboard.keyboard.engine

/**
 * Undo / Redo — smart grouping (Gboard-jaisa), web `undoStack/redoStack` ka 1:1 port.
 *
 * Rules (web ke comments se verbatim semantics):
 *  - Lagatar same `actionType` ke edits **ek hi group** mein rehte hain
 *    (ek undo se poora word/chunk wapas aata hai).
 *  - Type badalne par ya group band hone par naya checkpoint banta hai.
 *  - Space / cursor-move / undo / redo ke baad group **force-close** hota hai.
 *  - Naya edit hote hi redoStack clear (standard editor rule).
 *  - Limit: 300 snapshots (web: UNDO_LIMIT).
 *
 * Snapshot mein sirf text + cursor nahi, **combining state bhi** hota hai — warna
 * undo ke baad text sahi dikhega par agla keystroke galat combine karega
 * (matra-row dikhna band, ya yukt adhoora) — yeh web ka [FIX] tha.
 */
class UndoStack(private val limit: Int = UNDO_LIMIT) {

    data class Snapshot(
        val text: String,
        val cursorPos: Int?,
        val selAnchor: Int?,
        val yukt: Yukt?,
        val row1: Row1Mode,
        val vc: VocalicRMode,
        val vcCons: String?,
        val lastCons: String?,
        val lastInput: String?,
    )

    private val undo = ArrayDeque<Snapshot>()
    private val redo = ArrayDeque<Snapshot>()
    private var groupOpen = false
    private var lastActionType: String? = null

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
    val undoDepth: Int get() = undo.size
    val redoDepth: Int get() = redo.size
    val isGroupOpen: Boolean get() = groupOpen

    /** Kisi bhi text-mutation se PEHLE call karo. */
    fun recordCheckpoint(actionType: String, snapshot: () -> Snapshot) {
        val isBoundary = !groupOpen || actionType != lastActionType
        if (isBoundary) {
            undo.addLast(snapshot())
            while (undo.size > limit) undo.removeFirst()
            redo.clear()
            groupOpen = true
            lastActionType = actionType
        }
    }

    /** Group ko jabardasti band karo — agla edit naya group shuru karega. */
    fun closeGroup() {
        groupOpen = false
        lastActionType = null
    }

    fun undo(snapshot: () -> Snapshot, restore: (Snapshot) -> Unit): Boolean {
        if (undo.isEmpty()) return false
        val current = snapshot()
        val prev = undo.removeLast()
        redo.addLast(current)
        restore(prev)
        closeGroup()
        return true
    }

    fun redo(snapshot: () -> Snapshot, restore: (Snapshot) -> Unit): Boolean {
        if (redo.isEmpty()) return false
        val current = snapshot()
        val next = redo.removeLast()
        undo.addLast(current)
        restore(next)
        closeGroup()
        return true
    }

    fun clear() {
        undo.clear(); redo.clear(); closeGroup()
    }

    companion object {
        const val UNDO_LIMIT = 300
    }
}
