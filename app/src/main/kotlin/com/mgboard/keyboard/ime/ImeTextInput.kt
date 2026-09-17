package com.mgboard.keyboard.ime

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.mgboard.keyboard.CpText
import com.mgboard.keyboard.engine.TextInput

/**
 * [TextInput] ka real-IME adapter.
 *
 * Web demo mein engine ek in-page textarea par chalta tha. Android par wahi engine
 * editor ke text par chalta hai:
 *  - text/cursor/selection `InputConnection.getExtractedText()` se aata hai
 *  - mutation **composing region** par hota hai, taaki bade documents mein poora
 *    text baar-baar rewrite na ho
 *
 * Indices [TextInput] ke contract ke hisaab se **code point** based hain, aur
 * `replaceRange` composing-region-relative indices leta hai (engine hamesha usi
 * text par kaam karta hai jo [text] ne diya).
 */
class ImeTextInput(private val ic: () -> InputConnection?) : TextInput {

    /** Composing window ka absolute char start (code point nahi — IC absolute chars leta hai). */
    var regionStartCp: Int = 0
        private set
    var regionText: String = ""
        private set
    var cursorCp: Int? = null
        private set
    var anchorCp: Int? = null
        private set

    /** Kitne code points ka window caret ke around chahiye. */
    var windowChars: Int = 512

    override val text: String get() = regionText
    override val cursorPos: Int? get() = cursorCp
    override val selAnchor: Int? get() = anchorCp

    /** Editor se fresh state kheencho. Har key-event se pehle call hota hai. */
    fun sync(): Boolean {
        val ic = ic() ?: return false
        val et = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val full = et.text?.toString() ?: ""
        val selStartChar = et.selectionStart.coerceAtLeast(0)
        val selEndChar = et.selectionEnd.coerceAtLeast(selStartChar)

        // caret ke around window (code points mein)
        val totalCp = CpText.len(full)
        val caretCp = CpText.cps(full.substring(0, minOf(selEndChar, full.length))).size
        val from = (caretCp - windowChars / 2).coerceIn(0, totalCp)
        val to = (from + windowChars).coerceAtMost(totalCp)

        // window ke code-point indices ko absolute char indices banao
        val cps = CpText.cps(full)
        var charIdx = 0; var cpIdx = 0
        var startChar = 0; var endChar = full.length
        while (cpIdx < from && charIdx < full.length) { charIdx += Character.charCount(cps[cpIdx]); cpIdx++ }
        startChar = charIdx
        while (cpIdx < to && charIdx < full.length) { charIdx += Character.charCount(cps[cpIdx]); cpIdx++ }
        endChar = charIdx

        regionStartCp = from
        regionStartCharCache = startChar
        regionText = full.substring(startChar, endChar)
        val anchorAbs = CpText.cps(full.substring(0, minOf(selStartChar, full.length))).size
        anchorCp = if (anchorAbs == caretCp) null else (anchorAbs - from).coerceIn(0, CpText.len(regionText))
        cursorCp = (caretCp - from).coerceIn(0, CpText.len(regionText))
            .let { if (it >= CpText.len(regionText)) null else it }
        return true
    }

    override fun replaceRange(start: Int, end: Int, replacement: String) {
        val ic = ic() ?: return
        val regionCps = CpText.cps(regionText)
        val s = start.coerceIn(0, regionCps.size)
        val e = end.coerceIn(s, regionCps.size)

        // region-relative code points → absolute char indices
        var charPos = 0; var i = 0
        while (i < s) { charPos += Character.charCount(regionCps[i]); i++ }
        val startChar = regionStartChar() + charPos
        var delChars = 0
        for (k in s until e) delChars += Character.charCount(regionCps[k])

        ic.beginBatchEdit()
        try {
            // composing region isliye ki apps ko pata rahe ki abhi composition chal rahi hai
            ic.setComposingRegion(startChar, startChar + delChars, 0)
            ic.commitText(replacement, 1)
            val newCursor = startChar + replacement.length
            ic.setSelection(newCursor, newCursor)
        } finally {
            ic.endBatchEdit()
        }
        sync()
    }

    override fun restoreCursor(cursor: Int?, anchor: Int?) {
        val ic = ic() ?: return
        val regionCps = CpText.cps(regionText)
        fun charAt(cp: Int): Int {
            var c = 0; var i = 0
            val target = cp.coerceIn(0, regionCps.size)
            while (i < target) { c += Character.charCount(regionCps[i]); i++ }
            return regionStartChar() + c
        }
        val cur = charAt(cursor ?: regionCps.size)
        val anc = if (anchor != null) charAt(anchor) else cur
        ic.setSelection(anc, cur)
        sync()
    }

    private fun regionStartChar(): Int {
        // regionText ka absolute start char index — sync() mein nikala gaya tha
        return regionStartCharCache
    }

    private var regionStartCharCache: Int = 0

    /** sync() ke dauraan hi absolute char start yaad rakh lo. */
    internal fun setRegionStartChar(v: Int) { regionStartCharCache = v }
}
