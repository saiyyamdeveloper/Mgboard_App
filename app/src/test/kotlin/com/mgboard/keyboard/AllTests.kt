package com.mgboard.keyboard

import com.mgboard.keyboard.data.Cp
import com.mgboard.keyboard.data.EmojiData
import com.mgboard.keyboard.data.Gondi
import com.mgboard.keyboard.data.Hindi
import com.mgboard.keyboard.data.Numbers
import com.mgboard.keyboard.data.Qwerty
import com.mgboard.keyboard.CpText
import com.mgboard.keyboard.engine.BufferInput
import com.mgboard.keyboard.engine.DevToGondi
import com.mgboard.keyboard.engine.KbMode
import com.mgboard.keyboard.engine.Panel
import com.mgboard.keyboard.engine.Row1Mode
import com.mgboard.keyboard.engine.TypingEngine
import com.mgboard.keyboard.engine.VocalicRMode
import com.mgboard.keyboard.engine.YuktType

/**
 * JVM regression suite — engine + data + converter.
 *
 * Har test web (`Mgboard_Web/index.html`) ke documented behaviour se derive hua hai;
 * test ke naam mein web function ka reference hai taaki drift pakda ja sake.
 *
 * Run: ./scripts/run_jvm_tests.sh   (ya ./gradlew test)
 */
object AllTests {

    private fun eng(text: String = "", cursor: Int? = null): Pair<TypingEngine, BufferInput> {
        val b = BufferInput(text)
        if (cursor != null) b.setCursor(cursor)
        return TypingEngine(b) to b
    }

    @JvmStatic
    fun main(args: Array<String>) {
        dataTests()
        engineTests()
        yuktTests()
        undoTests()
        converterTests()
        LayoutTests.run()
        VoiceTests.run()
        ToolbarTests.run()
        System.exit(T.report())
    }

    // ══════════════════ 1. generated data (KeyboardData.kt) ══════════════════

    private fun dataTests() {
        T.section("KeyboardData (generated from web)")

        T.eq("CP map = 84 entries", Cp.MAP.size, 84)
        T.eq("CP.KA = U+11D0C", Cp.KA, str(0x11D0C))
        T.eq("CP.NUKTA = U+11D42", Cp.NUKTA, str(0x11D42))
        T.eq("CP.REPHA = U+11D46", Cp.REPHA, str(0x11D46))
        T.eq("CP.RAKARA = U+11D47", Cp.RAKARA, str(0x11D47))
        T.eq("CP.HALANTA = U+11D44 (word-final)", Cp.HALANTA, str(0x11D44))
        T.eq("CP.VIRAMA = U+11D45 (conjunct)", Cp.VIRAMA, str(0x11D45))
        T.ok("HALANTA != VIRAMA (web ka deep-fix distinction)", Cp.HALANTA != Cp.VIRAMA)
        T.eq("CP digits D0..D9 = U+11D50..11D59",
            (0..9).map { Cp.MAP.getValue("D$it") }, (0..9).map { str(0x11D50 + it) })

        T.eq("CONSONANTS = 37 (34 + KSSA/JNYA/TRA)", Gondi.CONSONANTS.size, 37)
        T.ok("CONSONANTS has KA", Gondi.CONSONANTS.contains(Cp.KA))
        T.ok("CONSONANTS has TRA (conjunct letter)", Gondi.CONSONANTS.contains(Cp.TRA))
        T.ok("CONSONANTS does NOT have RA-only matra", !Gondi.CONSONANTS.contains(Cp.VS_R))
        T.eq("MATRAS = 11 (10 signs + halanta)", Gondi.MATRAS.size, 11)
        T.ok("MATRAS has HALANTA", Gondi.MATRAS.contains(Cp.HALANTA))

        T.eq("Gondi row1 vowels = 10", Gondi.VOWEL_KEYS.size, 10)
        T.eq("Gondi row1 matras = 10", Gondi.MATRA_KEYS.size, 10)
        T.eq("Gondi rows 2-5 = 10 keys each",
            Gondi.LETTER_ROWS.map { it.size }, listOf(10, 10, 10, 10))
        T.eq("ROW5 slot 7 = null (dynamic vocalic-R)", Gondi.ROW5[6], null)
        T.eq("ROW5 last = DEL", Gondi.ROW5[9]?.s, "DEL")
        T.eq("ROW5 yukt key label", Gondi.ROW5[8]?.s, "युक्त")
        T.eq("ROW5 yukt key cls = special", Gondi.ROW5[8]?.cls, "special")
        T.eq("GHA long-press = NGA", Gondi.ROW2[3]?.lp, Cp.NGA)
        T.eq("JHA long-press = NYA", Gondi.ROW2[7]?.lp, Cp.NYA)
        T.eq("LA long-press = LLA", Gondi.ROW4[7]?.lp, Cp.LLA)
        T.eq("Anusvara long-press = Visarga", Gondi.ROW2[9]?.lp, Cp.VISARGA)

        T.eq("Hindi rows 2-5 = 10 keys each", Hindi.LETTER_ROWS.map { it.size }, listOf(10, 10, 10, 10))
        T.eq("Hindi row1 vowels = 10", Hindi.VOWEL_KEYS.size, 10)
        T.eq("Hindi ka glyph = Devanagari label", Hindi.VOWEL_KEYS[0].s, "अ")
        T.eq("Hindi KA long-press = क़ (KA+NUKTA)", Hindi.ROW2[0]?.lp, Cp.KA + Cp.NUKTA)
        T.eq("Hindi GHA long-press = ङ", Hindi.ROW2[3]?.lp, Cp.NGA)
        T.eq("Hindi ROW5 slot 7 = null (dynamic vocalic-R)", Hindi.ROW5[6], null)

        T.eq("QWERTY = 3 rows (10/9/7)", Qwerty.ROWS.map { it.size }, listOf(10, 9, 7))
        T.eq("QWERTY row1", Qwerty.ROWS[0], "qwertyuiop".map { it.toString() })
        T.eq("QWERTY row3", Qwerty.ROWS[2], "zxcvbnm".map { it.toString() })

        T.eq("Numbers = 5 rows × 10", Numbers.ROWS.map { it.size }, listOf(10, 10, 10, 10, 10))
        T.eq("Numbers row1 = Gondi digits 1..9,0",
            Numbers.ROWS[0].map { it.g }, (1..9).map { Cp.MAP.getValue("D$it") } + Cp.D0)
        T.eq("Numbers last key = DEL/danger",
            Numbers.ROWS[4][9].s to Numbers.ROWS[4][9].cls, "DEL" to "danger")
        T.eq("Numbers rupee key", Numbers.ROWS[1][0].g, Cp.RUPEE)
        T.eq("CALC_OPS = 7 operators", Numbers.CALC_OPS, listOf("+", "-", "*", "/", "(", ")", ","))

        T.eq("Emoji = 9 categories", EmojiData.CATEGORIES.size, 9)
        T.eq("Emoji category order preserved",
            EmojiData.CATEGORIES.keys.toList().map { it.substringAfter(' ') },
            listOf("Smileys", "Gestures", "Hearts", "Nature", "Food", "Activity", "Travel", "Objects", "Symbols"))
        T.ok("every category non-empty", EmojiData.CATEGORIES.values.all { it.isNotEmpty() })
    }

    private fun str(cp: Int) = String(Character.toChars(cp))

    // ══════════════════ 2. engine basics ══════════════════

    private fun engineTests() {
        T.section("TypingEngine — cursor, context, backspace, modes")

        // initial state (web: const state = {...})
        run {
            val (e, _) = eng()
            T.eq("initial panel = letters", e.panel, Panel.LETTERS)
            T.eq("initial kbMode = gondi", e.kbMode, KbMode.GONDI)
            T.eq("initial row1 = vowel", e.row1, Row1Mode.VOWEL)
            T.eq("initial vc = default", e.vc, VocalicRMode.DEFAULT)
            T.eq("initial yukt = null", e.yukt, null)
            T.eq("initial numPage = 1", e.numPage, 1)
            T.eq("gondi spacebar label = Gondi word", e.spaceLabel, TypingEngine.GONDI_SPACE_LABEL)
        }

        // codepoint safety — Gondi chars BMP ke bahar hain
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA)
            e.onConsonant(Cp.KHA)
            T.eq("2 Gondi chars → 2 code points (not 4 UTF-16 units)", CpText.len(b.text), 2)
            T.eq("cursor at end = null", b.cursorPos, null)
            T.eq("cursorIndex() = 2", e.cursorIndex(), 2)
            T.ok("isCursorAtEnd()", e.isCursorAtEnd())
        }

        // syncImeContext: row1 vowel ↔ matra (web: cons ? 'matra' : 'vowel')
        run {
            val (e, _) = eng()
            e.onConsonant(Cp.KA)
            T.eq("consonant ke baad row1 = matra", e.row1, Row1Mode.MATRA)
            T.eq("vc = combo", e.vc, VocalicRMode.COMBO)
            T.eq("vcCons = KA", e.vcCons, Cp.KA)
            T.eq("lastCons = KA", e.lastCons, Cp.KA)
            T.eq("vocalic-R glyph = KA + VS_R (combo)", e.vocalicRGlyph, Cp.KA + Cp.VS_R)
            e.onSpace()
            T.eq("space ke baad row1 = vowel", e.row1, Row1Mode.VOWEL)
            T.eq("vc = default", e.vc, VocalicRMode.DEFAULT)
            T.eq("vocalic-R glyph = VS_R", e.vocalicRGlyph, Cp.VS_R)
        }

        // nukta: row1 matra hi rehna chahiye (consonantBeforeCursor nukta skip karta hai)
        run {
            val (e, _) = eng()
            e.onConsonant(Cp.KA)
            e.onConsonant(Cp.NUKTA)
            T.eq("KA+NUKTA ke baad row1 = matra", e.row1, Row1Mode.MATRA)
            T.eq("vcCons = KA+NUKTA", e.vcCons, Cp.KA + Cp.NUKTA)
            T.eq("lastCons = KA (base)", e.lastCons, Cp.KA)
        }

        // rakara skip (allowRakara = true)
        run {
            val (e, _) = eng(Cp.KA + Cp.RAKARA)
            T.eq("cons before cursor skips RAKARA", e.consonantBeforeCursor(true)?.text, Cp.KA + Cp.RAKARA)
            T.eq("  base = KA", e.consonantBeforeCursor(true)?.base, Cp.KA)
            T.eq("without allowRakara → null", e.consonantBeforeCursor(false), null)
        }

        // isConsonantSequence
        run {
            val (e, _) = eng()
            T.ok("KA is consonant sequence", e.isConsonantSequence(Cp.KA))
            T.ok("KA+NUKTA is consonant sequence", e.isConsonantSequence(Cp.KA + Cp.NUKTA))
            T.ok("TRA (conjunct letter) is consonant sequence", e.isConsonantSequence(Cp.TRA))
            T.ok("VS_AA is NOT", !e.isConsonantSequence(Cp.VS_AA))
            T.ok("KA+KA is NOT (3+ chars rule)", !e.isConsonantSequence(Cp.KA + Cp.KA))
            T.ok("KA+NUKTA+NUKTA is NOT", !e.isConsonantSequence(Cp.KA + Cp.NUKTA + Cp.NUKTA))
            T.ok("empty is NOT", !e.isConsonantSequence(""))
        }

        // matra insertion
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA)
            e.onMatra(Cp.VS_AA)
            T.eq("KA + VS_AA", b.text, Cp.KA + Cp.VS_AA)
            T.eq("matra ke baad row1 wapas vowel (matra consonant nahi)", e.row1, Row1Mode.VOWEL)
        }

        // backspace
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA); e.onConsonant(Cp.KHA)
            e.backspace()
            T.eq("backspace removes last code point", b.text, Cp.KA)
            e.backspace()
            T.eq("backspace again → empty", b.text, "")
            e.backspace()
            T.eq("backspace on empty = no crash, still empty", b.text, "")
        }

        // backspace at explicit cursor 0 = no-op (web: idx === 0 → syncImeContext + return)
        run {
            val (e, b) = eng(Cp.KA + Cp.KHA, cursor = 0)
            e.backspace()
            T.eq("backspace at cursor 0 does nothing", b.text, Cp.KA + Cp.KHA)
        }

        // backspace with selection replaces the range
        run {
            val (e, b) = eng("abcd")
            b.setSelection(1, 3)
            T.eq("selRange = [1,3]", e.selRange()?.toList(), listOf(1, 3))
            e.backspace()
            T.eq("selection deleted", b.text, "ad")
        }

        // selRange normalises reversed anchor/cursor
        run {
            val (e, b) = eng("abcd")
            b.setSelection(3, 1)
            T.eq("reversed selection → [1,3]", e.selRange()?.toList(), listOf(1, 3))
            b.setSelection(2, 2)
            T.eq("empty selection → null", e.selRange(), null)
        }

        // insertCharacter replaces selection
        run {
            val (e, b) = eng("abcd")
            b.setSelection(1, 3)
            e.insertCharacter("X")
            T.eq("selection replaced by char", b.text, "aXd")
        }

        // bulk insert
        run {
            val (e, b) = eng()
            e.insertTextBulk("hello")
            T.eq("bulk insert", b.text, "hello")
            T.ok("bulk closes its undo group", !e.undo.isGroupOpen)
        }

        // panel toggle (web: onToggle → numbers par numPage = 1)
        run {
            val (e, _) = eng()
            e.selectNumPage(3)
            e.onTogglePanel()
            T.eq("toggle → numbers", e.panel, Panel.NUMBERS)
            T.eq("numbers khulte hi numPage = 1", e.numPage, 1)
            e.onTogglePanel()
            T.eq("toggle back → letters", e.panel, Panel.LETTERS)
        }

        // globe 3-way cycle (web: KB_MODES order)
        run {
            val (e, _) = eng()
            T.eq("globe 1 → qwerty", e.onGlobeTap(), KbMode.QWERTY)
            T.eq("globe 2 → hindi", e.onGlobeTap(), KbMode.HINDI)
            T.eq("globe 3 → gondi", e.onGlobeTap(), KbMode.GONDI)
            T.eq("qwerty spacebar label", run { e.onGlobeTap(); e.spaceLabel }, "English")
            T.eq("hindi spacebar label", run { e.onGlobeTap(); e.spaceLabel }, "हिंदी")
            e.onTogglePanel(); e.onGlobeTap()
            T.eq("globe tap panel ko letters par reset karta hai", e.panel, Panel.LETTERS)
        }

        // syncImeContext never infers from last key (web comment: paste/cursor-move stale)
        run {
            val (e, b) = eng(Cp.KA)
            T.eq("row1 matra after existing consonant", e.row1, Row1Mode.MATRA)
            b.setCursor(0)
            e.syncImeContext()
            T.eq("cursor move ke baad row1 vowel (text-before-caret authoritative)", e.row1, Row1Mode.VOWEL)
        }
    }

    // ══════════════════ 3. yukt / composition ══════════════════

    private fun yuktTests() {
        T.section("TypingEngine — युक्त (yukt) composition")

        // onYukt pending banana
        run {
            val (e, _) = eng()
            e.onConsonant(Cp.KA)
            e.onYukt()
            T.eq("yukt pending = KA", e.yukt?.cons, Cp.KA)
            T.eq("yukt type = lookahead", e.yukt?.type, YuktType.LOOKAHEAD)
            T.eq("yukt range = [0,1]", e.yukt?.let { listOf(it.start, it.end) }, listOf(0, 1))
        }

        // RA → reph
        run {
            val (e, _) = eng()
            e.onConsonant(Cp.RA)
            e.onYukt()
            T.eq("RA yukt type = reph", e.yukt?.type, YuktType.REPH)
        }

        // yukt + consonant → VIRAMA conjunct
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA); e.onYukt(); e.onConsonant(Cp.KHA)
            T.eq("KA + yukt + KHA = KA VIRAMA KHA", b.text, Cp.KA + Cp.VIRAMA + Cp.KHA)
            T.eq("yukt cleared", e.yukt, null)
        }

        // yukt + RA → RAKARA
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA); e.onYukt(); e.onConsonant(Cp.RA)
            T.eq("KA + yukt + RA = KA RAKARA", b.text, Cp.KA + Cp.RAKARA)
        }

        // yukt after RA + consonant → REPHA
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.RA); e.onYukt(); e.onConsonant(Cp.KA)
            T.eq("RA + yukt + KA = REPHA KA", b.text, Cp.REPHA + Cp.KA)
        }

        // yukt + matra → HALANTA (word-final virama)
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA); e.onYukt(); e.onMatra(Cp.VS_AA)
            T.eq("KA + yukt + matra = KA HALANTA VS_AA", b.text, Cp.KA + Cp.HALANTA + Cp.VS_AA)
        }

        // yukt + space → HALANTA + space (web: commitYuktAndSpace)
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA); e.onYukt(); e.onSpace()
            T.eq("KA + yukt + space = KA HALANTA space", b.text, Cp.KA + Cp.HALANTA + " ")
        }

        // yukt nukta-consonant par
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA); e.onConsonant(Cp.NUKTA); e.onYukt(); e.onConsonant(Cp.KHA)
            T.eq("KA+NUKTA yukt conjunct", b.text, Cp.KA + Cp.NUKTA + Cp.VIRAMA + Cp.KHA)
        }

        // yukt dobara dabane par kuch nahi hota (web: if(state.yukt) return)
        run {
            val (e, _) = eng()
            e.onConsonant(Cp.KA); e.onYukt()
            val before = e.yukt
            e.onYukt()
            T.eq("second yukt ignored", e.yukt, before)
        }

        // selection active ho to yukt nahi banta
        run {
            val (e, b) = eng(Cp.KA + Cp.KHA)
            b.setSelection(0, 2)
            e.onYukt()
            T.eq("yukt with selection → null", e.yukt, null)
        }

        // consonant se pehle yukt nahi banta (vowel par)
        run {
            val (e, _) = eng()
            e.onVowel(Cp.A)
            e.onYukt()
            T.eq("yukt after vowel → null", e.yukt, null)
        }

        // stale composition: cursor move karne ke baad flush nahi hona chahiye
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA); e.onYukt()
            b.setCursor(0)
            e.onConsonant(Cp.KHA)
            T.eq("cursor move ke baad yukt flush nahi hua — normal insert",
                b.text, Cp.KHA + Cp.KA)
        }

        // text badal gaya (paste) to composition invalid
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA); e.onYukt()
            b.replaceRange(0, 1, Cp.GA)      // KA → GA (yukt stale)
            e.onConsonant(Cp.KHA)
            T.eq("stale cons par flush nahi — sirf insert", b.text, Cp.GA + Cp.KHA)
        }
    }

    // ══════════════════ 4. undo / redo ══════════════════

    private fun undoTests() {
        T.section("UndoStack — smart grouping (Gboard-jaisa)")

        // lagatar same-type edits = ek group
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA); e.onConsonant(Cp.KHA); e.onConsonant(Cp.GA)
            T.eq("3 inserts = 1 undo checkpoint", e.undo.undoDepth, 1)
            T.eq("text before undo", CpText.len(b.text), 3)
            e.performUndo()
            T.eq("ek undo se poora group wapas", b.text, "")
            T.eq("redo available", e.undo.canRedo, true)
            e.performRedo()
            T.eq("redo restores", CpText.len(b.text), 3)
        }

        // type badalne par naya group
        run {
            val (e, _) = eng()
            e.onConsonant(Cp.KA); e.onConsonant(Cp.KHA)
            e.backspace()
            T.eq("insert + delete = 2 groups", e.undo.undoDepth, 2)
            e.performUndo()
            T.eq("undo delete → 2 chars", e.undo.undoDepth, 1)
        }

        // space = boundary (group force-close)
        run {
            val (e, _) = eng()
            e.onConsonant(Cp.KA)
            e.onSpace()
            e.onConsonant(Cp.KHA)
            T.ok("space ne naya group banaya", e.undo.undoDepth >= 2)
        }

        // naya edit → redoStack clear
        run {
            val (e, _) = eng()
            e.onConsonant(Cp.KA); e.performUndo()
            T.ok("redo available before new edit", e.undo.canRedo)
            e.onConsonant(Cp.KHA)
            T.eq("new edit clears redo", e.undo.canRedo, false)
        }

        // undo empty stack par safe
        run {
            val (e, _) = eng()
            T.eq("undo on empty = false", e.performUndo(), false)
            T.eq("redo on empty = false", e.performRedo(), false)
        }

        // undo combining state bhi restore karta hai (web ka [FIX])
        run {
            val (e, b) = eng()
            e.onConsonant(Cp.KA)          // row1 = matra
            T.eq("row1 matra before undo", e.row1, Row1Mode.MATRA)
            e.performUndo()
            T.eq("undo ke baad row1 vowel (combining state restore)", e.row1, Row1Mode.VOWEL)
            T.eq("  text empty", b.text, "")
        }

        // undo limit = 300
        run {
            val (e, _) = eng()
            repeat(320) { i -> e.insertTextBulk("x$i") }   // har bulk apna group hai
            T.eq("undo stack capped at 300", e.undo.undoDepth, 300)
        }
    }

    // ══════════════════ 5. Devanagari → Gondi ══════════════════

    private fun converterTests() {
        T.section("DevToGondi — voice-typing converter (deep-fix rules)")

        // maps web ke jaise derive hote hain
        T.ok("GONDI_TO_DEV non-empty", DevToGondi.GONDI_TO_DEV.isNotEmpty())
        T.eq("KA → क", DevToGondi.GONDI_TO_DEV[Cp.KA], "क")
        T.eq("क → KA", DevToGondi.DEV_TO_GONDI["क"], Cp.KA)
        T.eq("ळ → LLA (long-press-only char, explicit add)", DevToGondi.DEV_TO_GONDI["ळ"], Cp.LLA)
        T.eq("ङ → NGA", DevToGondi.DEV_TO_GONDI["ङ"], Cp.NGA)
        T.eq("ञ → NYA", DevToGondi.DEV_TO_GONDI["ञ"], Cp.NYA)
        T.eq("ऋ → VS_R", DevToGondi.DEV_TO_GONDI["ऋ"], Cp.VS_R)
        T.eq("ऑ → O + CANDRA", DevToGondi.DEV_TO_GONDI["ऑ"], Cp.O + Cp.CANDRA)
        T.eq("ॲ → A + CANDRA", DevToGondi.DEV_TO_GONDI["ॲ"], Cp.A + Cp.CANDRA)
        T.eq("क्ष → KSSA (composed token)", DevToGondi.DEV_TO_GONDI["क्ष"], Cp.KSSA)
        T.eq("ज्ञ → JNYA", DevToGondi.DEV_TO_GONDI["ज्ञ"], Cp.JNYA)
        T.eq("त्र → TRA", DevToGondi.DEV_TO_GONDI["त्र"], Cp.TRA)

        // digits — map level
        T.eq("५ → D5", DevToGondi.DEV_TO_GONDI["\u096B"], Cp.MAP.getValue("D5"))
        T.eq("5 → D5", DevToGondi.DEV_TO_GONDI["5"], Cp.MAP.getValue("D5"))

        // ── Devanagari test strings EXPLICIT code points se bane hain ────────
        // (Devanagari conjuncts mein virama ka position critical hai; typed text
        //  se ambiguity aati thi, isliye har string \uXXXX escapes se likhi gayi.)
        val VIR = "\u094D"      // devanagari virama (्)
        val NUK = "\u093C"      // devanagari nukta (़)
        val dKa = "\u0915"; val dKha = "\u0916"; val dGa = "\u0917"; val dJa = "\u091C"
        val dNya = "\u091E"; val dTa = "\u0924"; val dDa = "\u0921"; val dDdha = "\u0922"
        val dNa = "\u0928"; val dPa = "\u092A"; val dPha = "\u092B"; val dBa = "\u092C"
        val dMa = "\u092E"; val dYa = "\u092F"; val dRa = "\u0930"; val dLa = "\u0932"
        val dVa = "\u0935"; val dSha = "\u0936"; val dSsa = "\u0937"; val dSa = "\u0938"
        val dHa = "\u0939"; val dLla = "\u0933"; val dNna = "\u0923"; val dDa2 = "\u0926"
        val dA = "\u0905"; val dAa = "\u0906"; val dRi = "\u090B"
        val dAA = "\u093E"; val dI = "\u093F"; val dCand = "\u0945"; val dO = "\u0913"
        val dMarathiA = "\u0972"; val dRra = "\u0931"; val dNnna = "\u0929"; val dLlla = "\u0934"
        val dKsa = "\u0915\u094D\u0937"   // क + ् + ष
        val dJna = "\u091C\u094D\u091E"   // ज + ् + ञ
        val dTra = "\u0924\u094D\u0930"   // त + ् + र
        val dKsha = "\u0915\u094D\u0937\u093E" // क्ष + ा
        val dKsaYa = dKsa + "\u092F"        // क्षय
        val dSraVa = dSa + VIR + dRa + VIR + dVa   // स्र्व  (स ् र ् व)
        val dKraMa = dKa + VIR + dRa + dMa         // क्‍रम = क ् र म
        val dRda = dRa + VIR + dDa2                // र्द
        val dRma = dRa + VIR + dMa                 // र्म
        val dRrMa = dRa + VIR + dRa + VIR + dMa    // र्र्म
        val dKra = dKa + VIR + dRa                 // क्र
        val dNtra = dNa + VIR + dTra               // न्त्र
        val dKma = dKa + VIR + dMa                 // क्म
        val dKmaHal = dKma + VIR                   // क्म + word-final ्

        // ── nukta variants (convert() pehle NFD karta hai, web jaisa) ────────
        // NFD precomposed nukta letters ko tod deta hai: 'क़' → क+़, 'ऱ' → र+़.
        // Natija base + Gondi NUKTA — yeh web ka exact behaviour hai, isliye
        // DEV_NUKTA_FALLBACK ke ऱ/ऩ/ऴ entries NFD path par hit nahi hote
        // (map mein parity ke liye maujood hain).
        T.eq("क़ = KA+NUKTA", DevToGondi.convert("\u0958"), Cp.KA + Cp.NUKTA)
        T.eq("क़ (typed form) = KA+NUKTA", DevToGondi.convert("\u0915\u093C"), Cp.KA + Cp.NUKTA)
        T.eq("ख़ = KHA+NUKTA", DevToGondi.convert("\u0959"), Cp.KHA + Cp.NUKTA)
        T.eq("ग़ = GA+NUKTA", DevToGondi.convert("\u095A"), Cp.GA + Cp.NUKTA)
        T.eq("ज़ = JA+NUKTA", DevToGondi.convert("\u095B"), Cp.JA + Cp.NUKTA)
        T.eq("ड़ = DDA+NUKTA", DevToGondi.convert("\u095C"), Cp.DDA + Cp.NUKTA)
        T.eq("ढ़ = DDHA+NUKTA", DevToGondi.convert("\u095D"), Cp.DDHA + Cp.NUKTA)
        T.eq("फ़ = PHA+NUKTA", DevToGondi.convert("\u095E"), Cp.PHA + Cp.NUKTA)
        T.eq("य़ = YA+NUKTA", DevToGondi.convert("\u095F"), Cp.YA + Cp.NUKTA)
        T.eq("ऱ (NFD → र+़) = RA+NUKTA", DevToGondi.convert("\u0931"), Cp.RA + Cp.NUKTA)
        T.eq("ऩ (NFD → न+़) = NA+NUKTA", DevToGondi.convert("\u0929"), Cp.NA + Cp.NUKTA)
        T.eq("ऴ (NFD → ळ+़) = LLA+NUKTA", DevToGondi.convert("\u0934"), Cp.LLA + Cp.NUKTA)
        T.eq("DEV_NUKTA_FALLBACK map में ऱ→RA मौजूद (web parity)",
            DevToGondi.DEV_NUKTA_FALLBACK["\u0931"], Cp.RA)

        // ── single chars / matras ───────────────────────────────────────────
        T.eq("क = KA", DevToGondi.convert(dKa), Cp.KA)
        T.eq("अ = A", DevToGondi.convert(dA), Cp.A)
        T.eq("आ = AA", DevToGondi.convert(dAa), Cp.AA)
        T.eq("ऋ = VS_R", DevToGondi.convert(dRi), Cp.VS_R)
        T.eq("का = KA + VS_AA", DevToGondi.convert(dKa + dAA), Cp.KA + Cp.VS_AA)
        T.eq("कि = KA + VS_I", DevToGondi.convert(dKa + dI), Cp.KA + Cp.VS_I)
        T.eq("ङ = NGA", DevToGondi.convert("\u0919"), Cp.NGA)
        T.eq("ञ = NYA", DevToGondi.convert(dNya), Cp.NYA)
        T.eq("ळ = LLA", DevToGondi.convert(dLla), Cp.LLA)
        // precomposed candra vowels map hote hain; decomposed form passthrough
        // (web ka DEV_TO_GONDI mein sirf precomposed keys hain)
        T.eq("ऑ (precomposed U+0913... actually U+0913+U+0945) — decomposed passthrough",
            DevToGondi.convert("\u0913\u0945"), Cp.O + "\u0945")
        T.eq("ऑ (precomposed U+0913\u0945 NFC) = O + CANDRA",
            DevToGondi.convert("\u0911"), Cp.O + Cp.CANDRA)
        T.eq("ॲ (precomposed U+0972) = A + CANDRA", DevToGondi.convert("\u0972"), Cp.A + Cp.CANDRA)

        // ── bina virama: har व्यंजन alag (conjunct nahi banta) ───────────────
        T.eq("कमल = KA MA LA", DevToGondi.convert(dKa + dMa + dLa), Cp.KA + Cp.MA + Cp.LA)

        // ── explicit virama → conjunct (VIRAMA se join) ─────────────────────
        T.eq("क्म = KA VIRAMA MA", DevToGondi.convert(dKma), Cp.KA + Cp.VIRAMA + Cp.MA)
        T.eq("स्र्व = SA VIRAMA RA VIRAMA VA", DevToGondi.convert(dSraVa),
            Cp.SA + Cp.VIRAMA + Cp.RA + Cp.VIRAMA + Cp.VA)
        // web ka cluster-boundary quirk: 4-token "क ् र म" mein cluster [क, र] hi
        // banta hai (JS `tokens[i+2]` undefined → isCons false), isliye र cluster-final
        // maan kar RAKARA lagta hai aur म alag unit banta hai. Yeh web-faithful hai.
        T.eq("क्रम (web quirk) = KA + RAKARA + MA",
            DevToGondi.convert(dKraMa), Cp.KA + Cp.RAKARA + Cp.MA)
        T.eq("5-token क्‍र्म = KA VIRAMA RA VIRAMA MA",
            DevToGondi.convert(dKa + VIR + dRa + VIR + dMa),
            Cp.KA + Cp.VIRAMA + Cp.RA + Cp.VIRAMA + Cp.MA)

        // ── REPHA (cluster-initial RA) ──────────────────────────────────────
        T.eq("र्द = REPHA DA (prefix + units)", DevToGondi.convert(dRda), Cp.REPHA + Cp.DA)
        T.eq("र्म = REPHA MA", DevToGondi.convert(dRma), Cp.REPHA + Cp.MA)
        T.eq("र्र्म = REPHA RA VIRAMA MA", DevToGondi.convert(dRrMa),
            Cp.REPHA + Cp.RA + Cp.VIRAMA + Cp.MA)
        T.eq("र अकेला = RA (cluster nahi)", DevToGondi.convert(dRa), Cp.RA)

        // ── RAKARA (cluster-final RA) ───────────────────────────────────────
        T.eq("क्र = KA + RAKARA", DevToGondi.convert(dKra), Cp.KA + Cp.RAKARA)

        // ── dedicated conjunct letters (REPHA/RAKARA rules se PEHLE) ────────
        T.eq("क्ष = KSSA", DevToGondi.convert(dKsa), Cp.KSSA)
        T.eq("ज्ञ = JNYA", DevToGondi.convert(dJna), Cp.JNYA)
        T.eq("त्र = TRA", DevToGondi.convert(dTra), Cp.TRA)
        T.eq("क्ष + ा = TRA… nahi: KSSA + VS_AA", DevToGondi.convert(dKsha), Cp.KSSA + Cp.VS_AA)
        // wahi quirk: "क ् ष य" → cluster [क, ष] = KSSA, य alag → KSSA + YA
        T.eq("क्षय (web quirk) = KSSA YA", DevToGondi.convert(dKsaYa), Cp.KSSA + Cp.YA)
        T.eq("न्त्र = NA VIRAMA TRA", DevToGondi.convert(dNtra), Cp.NA + Cp.VIRAMA + Cp.TRA)

        // ── HALANTA vs VIRAMA (web ka deep-fix distinction) ─────────────────
        T.eq("conjunct VIRAMA, word-final HALANTA", DevToGondi.convert(dKmaHal),
            Cp.KA + Cp.VIRAMA + Cp.MA + Cp.HALANTA)
        T.eq("akela word-final ् = HALANTA", DevToGondi.convert(VIR), Cp.HALANTA)
        // web-faithful: mapToken ka nukta branch drop-branch se PEHLE aata hai,
        // isliye akela nukta bhi base+NUKTA rule se U+093C + Gondi NUKTA banata hai.
        T.eq("standalone nukta → U+093C + Gondi NUKTA (web jaisa)",
            DevToGondi.convert(NUK), NUK + Cp.NUKTA)

        // ── digits: Devanagari + Latin dono → Gondi digits ──────────────────
        T.eq("२०२४ → Gondi digits", DevToGondi.convert("\u0968\u0966\u0968\u096A"),
            Cp.MAP.getValue("D2") + Cp.MAP.getValue("D0") + Cp.MAP.getValue("D2") + Cp.MAP.getValue("D4"))
        T.eq("latin 2024 → Gondi digits", DevToGondi.convert("2024"),
            Cp.MAP.getValue("D2") + Cp.MAP.getValue("D0") + Cp.MAP.getValue("D2") + Cp.MAP.getValue("D4"))
        T.eq("०-९ sab map hain", (0..9).map { DevToGondi.DEV_TO_GONDI[String(Character.toChars(0x0966 + it))] },
            (0..9).map { Cp.MAP.getValue("D$it") })

        // ── passthrough ─────────────────────────────────────────────────────
        T.eq("latin passthrough", DevToGondi.convert("abc"), "abc")
        T.eq("space passthrough", DevToGondi.convert(dKa + " " + dKha), Cp.KA + " " + Cp.KHA)
        T.eq("empty string", DevToGondi.convert(""), "")

        // vocalic-R combo label (web: renderVocalicRH)
        T.eq("vocalic-R label default", DevToGondi.vocalicRComboLabel(null), DevToGondi.DEV_VS_R)
        T.eq("vocalic-R label combo KA", DevToGondi.vocalicRComboLabel(Cp.KA), "क" + DevToGondi.DEV_VS_R)
        T.eq("vocalic-R label combo KA+NUKTA", DevToGondi.vocalicRComboLabel(Cp.KA + Cp.NUKTA),
            "क\u093C" + DevToGondi.DEV_VS_R)

        // KeyDef se typing: Hindi panel glyph insert karta hai, label nahi
        T.eq("Hindi key glyph = Gondi char", DevToGondi.keyGlyph(Hindi.ROW2[0]!!), Cp.KA)
        T.ok("Hindi panel ka har non-null key Gondi glyph deta hai",
            Hindi.LETTER_ROWS.flatten().filterNotNull().all { it.g.isNotEmpty() })
    }
}
