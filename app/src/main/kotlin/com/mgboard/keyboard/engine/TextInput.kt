package com.mgboard.keyboard.engine

import com.mgboard.keyboard.CpText

/**
 * Engine ka text surface.
 *
 * Web demo mein engine ek in-page textarea (`output`) par chalta tha. Android par
 * wahi engine do jagah reuse hota hai:
 *  1. [BufferInput]  — pure in-memory buffer → **JVM unit tests** (device ki zaroorat nahi)
 *  2. IME adapter    — `InputConnection` ke composing region par (app/src/main/.../ime)
 *
 * Indices hamesha **code point** based hote hain (see [CpText]).
 */
interface TextInput {
    /** Poora text jis par engine kaam kar raha hai. */
    val text: String

    /** null = cursor end par (web: `state.cursorPos === null`). */
    val cursorPos: Int?

    /** Selection anchor, null = koi selection nahi (web: `state.selAnchor`). */
    val selAnchor: Int?

    /** Text ke ek range ko replace karo aur cursor `start + inserted` par le jao. */
    fun replaceRange(start: Int, end: Int, replacement: String)

    /** Undo/redo ke liye cursor (aur selection) wapas set karo. `cursor == null` = end. */
    fun restoreCursor(cursor: Int?, anchor: Int?) {
        // default: selection support optional hai — sirf cursor end/start par
    }
}

/** In-memory implementation — tests aur offline demo ke liye. */
class BufferInput(initial: String = "") : TextInput {
    private var buf: String = initial
    private var cursor: Int? = null
    private var anchor: Int? = null

    override val text: String get() = buf
    override val cursorPos: Int? get() = cursor
    override val selAnchor: Int? get() = anchor

    override fun replaceRange(start: Int, end: Int, replacement: String) {
        val cps = CpText.cps(buf)
        val ins = CpText.cps(replacement)
        val s = start.coerceIn(0, cps.size)
        val e = end.coerceIn(s, cps.size)
        val next = IntArray(s + ins.size + (cps.size - e))
        System.arraycopy(cps, 0, next, 0, s)
        System.arraycopy(ins, 0, next, s, ins.size)
        System.arraycopy(cps, e, next, s + ins.size, cps.size - e)
        buf = CpText.join(next)
        val idx = s + ins.size
        cursor = if (idx >= next.size) null else idx
        anchor = null
    }

    fun setCursor(cp: Int?) { cursor = cp; anchor = null }
    fun setSelection(anchorCp: Int, cursorCp: Int) { anchor = anchorCp; cursor = cursorCp }
    fun clearSelection() { anchor = null }

    override fun restoreCursor(cursor: Int?, anchor: Int?) {
        if (anchor != null) setSelection(anchor, cursor ?: CpText.len(buf)) else setCursor(cursor)
    }
    override fun toString(): String = buf
}
