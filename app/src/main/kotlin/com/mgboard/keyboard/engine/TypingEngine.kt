package com.mgboard.keyboard.engine

import com.mgboard.keyboard.CpText
import com.mgboard.keyboard.data.Cp
import com.mgboard.keyboard.data.Gondi

/** Row 1 ka mode — web: `state.row1`. */
enum class Row1Mode { VOWEL, MATRA }

/** Vocalic-R key ka mode — web: `state.vc`. */
enum class VocalicRMode { DEFAULT, COMBO }

/** Panel — web: `state.panel`. */
enum class Panel { LETTERS, NUMBERS }

/** 3-way Globe cycle — web: `KB_MODES = ['gondi','qwerty','hindi']`. */
enum class KbMode(val displayName: String) {
    GONDI("Masaram Gondi (native)"),
    QWERTY("English (QWERTY)"),
    HINDI("Hindi → Gondi");

    companion object {
        /** Web ka order: gondi → qwerty → hindi → gondi */
        val CYCLE: List<KbMode> = listOf(GONDI, QWERTY, HINDI)
        fun next(m: KbMode): KbMode = CYCLE[(CYCLE.indexOf(m) + 1) % CYCLE.size]
    }
}

/**
 * Pending composition — web: `state.yukt = {type, cons, start, end}`.
 *
 * "युक्त" (virama) key dabane par consonant **pending** ho jaata hai; agla
 * keystroke decide karta hai ki kya bane:
 *  - consonant → reph (RA ke liye) / rakara (aage RA ho) / conjunct (VIRAMA se)
 *  - matra/vowel/other → HALANTA
 */
data class Yukt(
    val type: YuktType,
    val cons: String,
    val start: Int,
    val end: Int,
)

enum class YuktType { REPH, LOOKAHEAD }

/**
 * MgBoard typing engine — `Mgboard_Web/index.html` ke engine ka faithful Kotlin port.
 *
 * **Pure logic**: ismein koi Android class nahi hai, isliye plain JVM par unit-test
 * hota hai (`app/src/test/.../TypingEngineTest.kt`). Text surface [TextInput] ke
 * through aata hai — demo/test mein [BufferInput], real IME mein composing region.
 *
 * Indices hamesha code points mein (Masaram Gondi BMP ke bahar hai — surrogate
 * pairs todna nahi). See [CpText].
 */
class TypingEngine(val input: TextInput) {

    // ── web `state` ke fields ────────────────────────────────────────────────
    var panel: Panel = Panel.LETTERS
        private set
    var kbMode: KbMode = KbMode.GONDI
        private set
    var row1: Row1Mode = Row1Mode.VOWEL
        private set
    var vc: VocalicRMode = VocalicRMode.DEFAULT
        private set
    var vcCons: String? = null
        private set
    var lastCons: String? = null
        private set
    var lastInput: String? = null
        private set
    var yukt: Yukt? = null
        private set

    /** Numbers/symbols panel ka page — web: `state.numPage` (1 = A, 2 = B, 3 = Calc). */
    var numPage: Int = 1

    val undo = UndoStack()

    init {
        // Engine ko existing text ke saath banaya ja sakta hai (IME: field mein
        // pehle se text). Web mein har mutation ke baad syncImeContext() chalta
        // tha, isliye yahan bhi shuruaat mein context text se hi derive karte hain.
        syncImeContext()
    }

    /** UI ko batata hai ki kuch badla (keyboard re-render / labels update). */
    var onChange: (() -> Unit)? = null

    // ── cursor helpers (web: getCursorIndex / isCursorAtEnd / getSelRange) ────

    private fun cps(): IntArray = CpText.cps(input.text)

    fun cursorIndex(): Int {
        val len = cps().size
        val c = input.cursorPos ?: return len
        return c.coerceIn(0, len)
    }

    fun isCursorAtEnd(): Boolean {
        val c = input.cursorPos ?: return true
        return c >= cps().size
    }

    /** [start, end] (start < end) ya null jab selection na ho. */
    fun selRange(): IntArray? {
        val a0 = input.selAnchor ?: return null
        val len = cps().size
        val a = a0.coerceIn(0, len)
        val b = cursorIndex()
        if (a == b) return null
        return if (a < b) intArrayOf(a, b) else intArrayOf(b, a)
    }

    // ── consonant analysis (web: isConsonantSequence / consonantBeforeCursor) ─

    /**
     * `CONSONANTS.has(chars[0]) && (len === 1 || (len === 2 && chars[1] === NUKTA))`
     */
    fun isConsonantSequence(text: String): Boolean {
        val chars = CpText.cpsAsStrings(text)
        if (chars.isEmpty()) return false
        if (!Gondi.CONSONANTS.contains(chars[0])) return false
        return chars.size == 1 || (chars.size == 2 && chars[1] == Cp.NUKTA)
    }

    data class ConsBeforeCursor(val start: Int, val end: Int, val text: String, val base: String)

    fun consonantBeforeCursor(allowRakara: Boolean = false): ConsBeforeCursor? {
        val chars = CpText.cpsAsStrings(input.text)
        val end = cursorIndex()
        var i = end - 1
        if (i < 0) return null
        if (allowRakara && chars[i] == Cp.RAKARA) i--
        if (i < 0) return null
        if (chars[i] == Cp.NUKTA) i--
        if (i < 0) return null
        if (!Gondi.CONSONANTS.contains(chars[i])) return null
        return ConsBeforeCursor(i, end, chars.subList(i, end).joinToString(""), chars[i])
    }

    /**
     * Composition state **hamesha caret ke aas-paas ke text se** infer hota hai —
     * last-pressed-key se nahi (paste/cut/cursor-move use stale bana dete hain).
     * Web ka yeh comment-as-rule yahan bhi utna hi zaroori hai.
     */
    fun syncImeContext() {
        yukt = null
        val chars = CpText.cpsAsStrings(input.text)
        val idx = cursorIndex()
        val cons = consonantBeforeCursor(allowRakara = true)
        lastInput = if (idx - 1 in chars.indices) chars[idx - 1] else null
        lastCons = cons?.base
        row1 = if (cons != null) Row1Mode.MATRA else Row1Mode.VOWEL
        vc = if (cons != null) VocalicRMode.COMBO else VocalicRMode.DEFAULT
        vcCons = cons?.text
    }

    // ── mutation core (web: replaceTextRange) ────────────────────────────────

    private fun replaceTextRange(start: Int, end: Int, text: String) {
        input.replaceRange(start, end, text)
        syncImeContext()
    }

    // ── composition resolution (web: flushLookahead) ─────────────────────────

    /**
     * Pending yukt ko `nextChar` ke saath resolve karta hai.
     * @return true agar replacement ho gaya (caller ko alag se insert nahi karna).
     */
    fun flushLookahead(nextChar: String): Boolean {
        val pending = yukt ?: return false
        yukt = null
        val chars = CpText.cpsAsStrings(input.text)
        // text/cursor badal gaya ho (paste, cursor move) to composition invalid
        if (selRange() != null) return false
        if (cursorIndex() != pending.end) return false
        if (chars.subList(pending.start, pending.end).joinToString("") != pending.cons) return false

        val replacement = if (isConsonantSequence(nextChar)) {
            when {
                pending.type == YuktType.REPH -> Cp.REPHA + nextChar
                nextChar == Cp.RA -> pending.cons + Cp.RAKARA
                else -> pending.cons + Cp.VIRAMA + nextChar
            }
        } else {
            pending.cons + Cp.HALANTA + nextChar
        }
        replaceTextRange(pending.start, pending.end, replacement)
        return true
    }

    // ── public actions (web ke key handlers) ─────────────────────────────────

    fun insertCharacter(ch: String, actionType: String = "insert") {
        if (ch.isEmpty()) return
        checkpoint(actionType)
        if (!flushLookahead(ch)) {
            val r = selRange()
            val start = r?.get(0) ?: cursorIndex()
            val end = r?.get(1) ?: cursorIndex()
            replaceTextRange(start, end, ch)
        }
        onChange?.invoke()
    }

    fun backspace() {
        val range = selRange()
        val idx = cursorIndex()
        if (range == null && idx == 0) { syncImeContext(); onChange?.invoke(); return }
        checkpoint("delete")
        replaceTextRange(
            if (range != null) range[0] else idx - 1,
            if (range != null) range[1] else idx,
            "",
        )
        onChange?.invoke()
    }

    /** Bulk insert (paste / voice transcript / clipboard) — apna group. */
    fun insertTextBulk(str: String) {
        if (str.isEmpty()) return
        undo.closeGroup()
        checkpoint("bulk")
        val r = selRange()
        replaceTextRange(r?.get(0) ?: cursorIndex(), r?.get(1) ?: cursorIndex(), str)
        undo.closeGroup()
        onChange?.invoke()
    }

    /** Space / Enter: pehle pending yukt commit (HALANTA), phir boundary char. */
    fun commitYuktAndSpace(withNewline: Boolean) {
        undo.closeGroup()
        insertCharacter(if (withNewline) "\n" else " ", "boundary")
        undo.closeGroup()
    }

    fun onSpace() = commitYuktAndSpace(false)
    fun onEnter() = commitYuktAndSpace(true)
    fun onDelete() = backspace()

    fun onConsonant(ch: String) = insertCharacter(ch)
    fun onVowel(ch: String) = insertCharacter(ch)
    fun onMatra(ch: String) = insertCharacter(ch)
    fun onVocalicRTap() = insertCharacter(Cp.VS_R)

    /**
     * "युक्त" key — web: `onYukt()`.
     * Selection active ho ya yukt pehle se pending ho to kuch nahi hota.
     */
    fun onYukt() {
        if (yukt != null || selRange() != null) return
        val cons = consonantBeforeCursor() ?: return
        if (!isConsonantSequence(cons.text)) return
        yukt = Yukt(
            type = if (cons.text == Cp.RA) YuktType.REPH else YuktType.LOOKAHEAD,
            cons = cons.text,
            start = cons.start,
            end = cons.end,
        )
        onChange?.invoke()
    }

    /** '?123' toggle — web: `onToggle()` (mic force-stop IME layer karta hai). */
    fun onTogglePanel() {
        panel = if (panel == Panel.LETTERS) Panel.NUMBERS else Panel.LETTERS
        if (panel == Panel.NUMBERS) numPage = 1
        onChange?.invoke()
    }

    /** Globe tap — 3-way cycle gondi → qwerty → hindi. */
    fun onGlobeTap(): KbMode {
        kbMode = KbMode.next(kbMode)
        panel = Panel.LETTERS
        onChange?.invoke()
        return kbMode
    }

    fun selectNumPage(p: Int) { numPage = p.coerceIn(1, 3); onChange?.invoke() }

    // ── undo / redo ──────────────────────────────────────────────────────────

    private fun snapshot() = UndoStack.Snapshot(
        text = input.text,
        cursorPos = input.cursorPos,
        selAnchor = input.selAnchor,
        yukt = yukt,
        row1 = row1,
        vc = vc,
        vcCons = vcCons,
        lastCons = lastCons,
        lastInput = lastInput,
    )

    private fun restore(s: UndoStack.Snapshot) {
        // Web bhi snapshot se poora `output` likhta tha; yahan text badla ho tabhi
        // replace karte hain (warna cursor/selection bekar mein reset hota hai).
        if (input.text != s.text) input.replaceRange(0, CpText.len(input.text), s.text)
        input.restoreCursor(s.cursorPos, s.selAnchor)
        yukt = s.yukt
        row1 = s.row1
        vc = s.vc
        vcCons = s.vcCons
        lastCons = s.lastCons
        lastInput = s.lastInput
    }

    private fun checkpoint(actionType: String) = undo.recordCheckpoint(actionType) { snapshot() }

    fun performUndo(): Boolean {
        val done = undo.undo({ snapshot() }, { restore(it) })
        if (done) onChange?.invoke()
        return done
    }

    fun performRedo(): Boolean {
        val done = undo.redo({ snapshot() }, { restore(it) })
        if (done) onChange?.invoke()
        return done
    }

    // ── derived UI state (render helpers ko chahiye) ─────────────────────────

    /** Spacebar label — web: mode ke hisaab se '𑴎𑴽𑵀𑴘𑴳' / 'English' / 'हिंदी'. */
    val spaceLabel: String
        get() = when (kbMode) {
            KbMode.HINDI -> "हिंदी"
            KbMode.QWERTY -> "English"
            KbMode.GONDI -> GONDI_SPACE_LABEL
        }

    /** Vocalic-R key par jo glyph dikhega (combo mein consonant + VS_R). */
    val vocalicRGlyph: String
        get() = if (vc == VocalicRMode.COMBO && vcCons != null) vcCons!! + Cp.VS_R else Cp.VS_R

    companion object {
        /**
         * web: gondi mode mein spacebar par yahi Gondi shabd dikhta hai
         * ('𑴎𑴽𑵀𑴘𑴳' = GA + VS_O + D0 + VA + SA). Kotlin mein supplementary
         * code points ke liye `\u{...}` escape nahi chalta, isliye chars se banaya.
         */
        val GONDI_SPACE_LABEL: String = String(intArrayOf(0x11D0E, 0x11D3D, 0x11D50, 0x11D28, 0x11D2B), 0, 5)
    }
}
