package com.mgboard.keyboard.layout

/**
 * QWERTY shift state machine — web ke `qwertyShiftMode` / `qwertyManualLock` /
 * `onQwertyShift()` / `onQwertyLetterTap()` / `updateQwertyAutoCap()` ka port.
 *
 * Rules (web se verbatim):
 *  - auto-cap: text khali ho, ya caret ke pehle '\n' ho, ya "space after . ! ?"
 *    ho → oneshot (upper). Warna lower.
 *  - manual lock set ho to auto-cap override hota hai.
 *  - shift tap: double-tap (< 350ms) → caps toggle; caps se single tap → lower;
 *    oneshot se single tap → lower; lower se tap → oneshot.
 *  - letter tap: lower ho to jaisa hai, warna uppercase. caps lock ke alawa
 *    har letter ke baad manual lock clear.
 */
class QwertyShift {

    enum class Mode { LOWER, ONESHOT, CAPS }

    var mode: Mode = Mode.ONESHOT
        private set

    /** null = auto-cap chalega; warna yeh mode force hota hai. */
    var manualLock: Mode? = null
        private set

    var lastShiftTapTime: Long = 0L
        private set

    val isUpper: Boolean get() = mode != Mode.LOWER

    /** web: updateQwertyAutoCap() — `textBeforeCursor` code points ki list nahi, string hai. */
    fun updateAutoCap(textBeforeCursor: String) {
        manualLock?.let { mode = it; return }
        val shouldCap = when {
            textBeforeCursor.isEmpty() -> true
            textBeforeCursor.endsWith("\n") -> true
            textBeforeCursor.endsWith(" ") -> {
                val trimmed = textBeforeCursor.dropLast(1)
                trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")
            }
            else -> false
        }
        mode = if (shouldCap) Mode.ONESHOT else Mode.LOWER
    }

    /** web: onQwertyShift() — `now` inject karke testable banaya gaya hai. */
    fun onShiftTap(now: Long = System.currentTimeMillis()) {
        val isDoubleTap = (now - lastShiftTapTime) < KeyTiming.SHIFT_DOUBLE_TAP_MS
        lastShiftTapTime = now
        manualLock = when {
            isDoubleTap -> if (mode == Mode.CAPS) null else Mode.CAPS
            mode == Mode.CAPS -> Mode.LOWER
            mode == Mode.ONESHOT -> Mode.LOWER
            else -> Mode.ONESHOT
        }
    }

    /** web: onQwertyLetterTap(ch) — returns cased char; lock clear bhi karta hai. */
    fun applyTo(ch: String): String {
        val cased = if (mode == Mode.LOWER) ch else ch.uppercase()
        if (manualLock != Mode.CAPS) manualLock = null
        return cased
    }

    fun reset() {
        mode = Mode.ONESHOT
        manualLock = null
        lastShiftTapTime = 0L
    }
}
