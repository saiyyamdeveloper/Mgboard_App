package com.mgboard.keyboard

import com.mgboard.keyboard.data.Cp
import com.mgboard.keyboard.data.Numbers
import com.mgboard.keyboard.data.Qwerty
import com.mgboard.keyboard.engine.KbMode
import com.mgboard.keyboard.engine.Panel
import com.mgboard.keyboard.engine.Row1Mode
import com.mgboard.keyboard.grid.GridIcons
import com.mgboard.keyboard.grid.GridMenu
import com.mgboard.keyboard.layout.KeyKind
import com.mgboard.keyboard.layout.KeyTiming
import com.mgboard.keyboard.layout.KeyboardLayout
import com.mgboard.keyboard.layout.QwertyShift

/** Layout model · QWERTY shift machine · key timings · grid menu (Gboard parity). */
object LayoutTests {

    fun run() {
        timings()
        layouts()
        shift()
        gridMenu()
    }

    // ── user constraint: existing backspace repeat timing (400/70ms) reuse ho ──
    private fun timings() {
        T.section("KeyTiming — web ke exact values")
        T.eq("repeat initial delay = 400ms", KeyTiming.REPEAT_INITIAL_DELAY_MS, 400L)
        T.eq("repeat interval = 70ms", KeyTiming.REPEAT_INTERVAL_MS, 70L)
        T.eq("long-press = 300ms", KeyTiming.LONG_PRESS_MS, 300L)
        T.eq("multi-pop deadzone = 14", KeyTiming.MULTI_POP_DEADZONE_DP, 14)
        T.eq("space drag threshold = 6", KeyTiming.SPACE_DRAG_THRESHOLD_DP, 6)
        T.eq("space px per char = 16", KeyTiming.SPACE_DP_PER_CHAR, 16)
        T.eq("shift double-tap window = 350ms", KeyTiming.SHIFT_DOUBLE_TAP_MS, 350L)
    }

    private fun layouts() {
        T.section("KeyboardLayout — Gondi / Hindi / QWERTY / numbers")

        // row1 dynamic vowel ↔ matra
        T.eq("Gondi row1 vowel = 10 keys", KeyboardLayout.row1(KbMode.GONDI, Row1Mode.VOWEL).size, 10)
        T.eq("Gondi row1 matra = 10 keys", KeyboardLayout.row1(KbMode.GONDI, Row1Mode.MATRA).size, 10)
        T.eq("row1 vowel glyph = independent vowel",
            KeyboardLayout.row1(KbMode.GONDI, Row1Mode.VOWEL)[0].glyph, Cp.A)
        T.eq("row1 matra glyph = dependent sign",
            KeyboardLayout.row1(KbMode.GONDI, Row1Mode.MATRA)[0].glyph, Cp.VS_AA)
        T.eq("row1 kind = MATRA", KeyboardLayout.row1(KbMode.GONDI, Row1Mode.VOWEL)[0].kind, KeyKind.MATRA)
        T.eq("Hindi row1 label = Devanagari",
            KeyboardLayout.row1(KbMode.HINDI, Row1Mode.VOWEL)[0].label, "अ")

        // rows 2-5
        val gondi = KeyboardLayout.letterRows(KbMode.GONDI)
        T.eq("Gondi 4 rows (2-5)", gondi.size, 4)
        T.eq("har row mein 10 keys", gondi.map { it.size }, listOf(10, 10, 10, 10))
        T.eq("ROW5 ka null slot → VOCALIC_R key", gondi[3][6]?.kind, KeyKind.VOCALIC_R)
        T.eq("VOCALIC_R glyph = VS_R", gondi[3][6]?.glyph, Cp.VS_R)
        T.eq("ROW5 DEL → BACKSPACE", gondi[3][9]?.kind, KeyKind.BACKSPACE)
        T.eq("ROW5 युक्त → YUKT", gondi[3][8]?.kind, KeyKind.YUKT)
        T.eq("युक्त glyph = VIRAMA", gondi[3][8]?.glyph, Cp.VIRAMA)
        T.eq("युक्त semantic = special", gondi[3][8]?.semantic, "special")
        T.eq("GHA ka long-press = NGA (ROW2[3])", gondi[0][3]?.longPress, Cp.NGA)
        T.eq("LA ka long-press = LLA (ROW4[7])", gondi[2][7]?.longPress, Cp.LLA)

        val hindi = KeyboardLayout.letterRows(KbMode.HINDI)
        T.eq("Hindi युक्त key bhi YUKT kind deta hai (g == VIRAMA)",
            hindi[3][8]?.kind, KeyKind.YUKT)
        T.eq("Hindi ROW5 DEL → BACKSPACE", hindi[3][9]?.kind, KeyKind.BACKSPACE)
        T.eq("Hindi ka long-press alternate क़ (ROW2[0])", hindi[0][0]?.longPress, Cp.KA + Cp.NUKTA)
        T.eq("  uska label Devanagari", hindi[0][0]?.longPressLabel, "\u0915\u093C")

        // control row (web ke measured flex ratios)
        val ctrl = KeyboardLayout.controlRow(KbMode.GONDI)
        T.eq("control row = 6 keys", ctrl.size, 6)
        T.eq("control row kinds",
            ctrl.map { it.kind },
            listOf(KeyKind.TOGGLE_123, KeyKind.EMOJI, KeyKind.GLOBE, KeyKind.SPACE, KeyKind.PERIOD, KeyKind.ENTER))
        T.eq("weights = web flex 1.57/1/1/4.4/1/1.58",
            ctrl.map { it.weight }, listOf(1.57f, 1.0f, 1.0f, 4.4f, 1.0f, 1.58f))
        T.eq("period ke 16 long-press alternates (web lpMulti)", ctrl[4].longPressMulti.size, 16)
        T.eq("period alternates web-order",
            ctrl[4].longPressMulti.take(5), listOf("&", "%", "+", "\"", "-"))
        T.eq("Gondi toggle glyph = ? + Gondi digits 1-2-3",
            ctrl[0].glyph, "?" + Cp.MAP.getValue("D1") + Cp.MAP.getValue("D2") + Cp.MAP.getValue("D3"))
        T.eq("QWERTY/Hindi toggle glyph = ?123",
            KeyboardLayout.controlRow(KbMode.QWERTY)[0].glyph, "?123")
        T.eq("grid menu key optional (web control row mein nahi hota)",
            KeyboardLayout.controlRow(KbMode.GONDI, showGridMenu = true).size, 7)

        // QWERTY
        val q = KeyboardLayout.qwertyRows(upper = false)
        T.eq("QWERTY 3 rows", q.size, 3)
        T.eq("row1 = 10 letters", q[0].size, 10)
        T.eq("row2 = 9 letters", q[1].size, 9)
        T.eq("row3 = shift + 7 + backspace = 9", q[2].size, 9)
        T.eq("row3 pehla = SHIFT", q[2][0].kind, KeyKind.SHIFT)
        T.eq("row3 aakhri = BACKSPACE", q[2][8].kind, KeyKind.BACKSPACE)
        T.eq("shift weight 1.6", q[2][0].weight, 1.6f)
        T.eq("lowercase glyphs", q[0].map { it.glyph }, Qwerty.ROWS[0])
        T.eq("uppercase glyphs", KeyboardLayout.qwertyRows(true)[0].map { it.glyph },
            Qwerty.ROWS[0].map { it.uppercase() })

        // numbers panel
        val n1 = KeyboardLayout.numberRows(1, KbMode.GONDI)
        T.eq("numbers page1 = 5 rows", n1.size, 5)
        T.eq("numbers page1 last key = BACKSPACE", n1[4][9].kind, KeyKind.BACKSPACE)
        T.eq("numbers page1 digits", n1[0].map { it.glyph },
            (1..9).map { Cp.MAP.getValue("D$it") } + Cp.D0)
        T.eq("numbers page3 = 6 rows (calc row add)", KeyboardLayout.numberRows(3, KbMode.GONDI).size, 6)
        T.eq("calc row = CALC_OPS", KeyboardLayout.numberRows(3, KbMode.GONDI)[5].map { it.glyph },
            Numbers.CALC_OPS)
        T.eq("numbers back glyph Gondi = KA KHA GA",
            KeyboardLayout.numbersBackGlyph(KbMode.GONDI), Cp.KA + Cp.KHA + Cp.GA)
        T.eq("numbers back glyph Hindi = कखग", KeyboardLayout.numbersBackGlyph(KbMode.HINDI), "कखग")
        T.eq("numbers back glyph QWERTY = abc", KeyboardLayout.numbersBackGlyph(KbMode.QWERTY), "abc")

        // build()
        T.eq("build(gondi, letters) = row1 + 4 rows + control = 6",
            KeyboardLayout.build(KbMode.GONDI, Panel.LETTERS, Row1Mode.VOWEL, 1, false).size, 6)
        T.eq("build(qwerty, letters) = 3 rows",
            KeyboardLayout.build(KbMode.QWERTY, Panel.LETTERS, Row1Mode.VOWEL, 1, false).size, 3)
        T.eq("build(gondi, numbers) = 5 rows",
            KeyboardLayout.build(KbMode.GONDI, Panel.NUMBERS, Row1Mode.VOWEL, 1, false).size, 5)
        T.eq("build(hindi, letters) = 6 rows",
            KeyboardLayout.build(KbMode.HINDI, Panel.LETTERS, Row1Mode.MATRA, 1, false).size, 6)
    }

    private fun shift() {
        T.section("QwertyShift — auto-cap + double-tap caps lock")

        // default + auto cap
        run {
            val s = QwertyShift()
            T.eq("default mode = oneshot (web)", s.mode, QwertyShift.Mode.ONESHOT)
            T.eq("manualLock default = null", s.manualLock, null)
            s.updateAutoCap("")
            T.eq("empty text → upper", s.isUpper, true)
            s.updateAutoCap("hello")
            T.eq("mid-word → lower", s.mode, QwertyShift.Mode.LOWER)
            s.updateAutoCap("hello\n")
            T.eq("newline ke baad → upper", s.isUpper, true)
            s.updateAutoCap("hi. ")
            T.eq("'. ' ke baad → upper", s.isUpper, true)
            s.updateAutoCap("hi! ")
            T.eq("'! ' ke baad → upper", s.isUpper, true)
            s.updateAutoCap("hi? ")
            T.eq("'? ' ke baad → upper", s.isUpper, true)
            s.updateAutoCap("hi, ")
            T.eq("', ' ke baad → lower", s.mode, QwertyShift.Mode.LOWER)
            s.updateAutoCap("hi. x")
            T.eq("'. x' → lower", s.mode, QwertyShift.Mode.LOWER)
        }

        // letter tap cased + lock clear
        run {
            val s = QwertyShift()
            s.updateAutoCap("")
            T.eq("upper letter", s.applyTo("a"), "A")
            T.eq("letter ke baad auto-cap (lock null tha)", s.applyTo("b"), "B")
            s.updateAutoCap("Ab")
            T.eq("mid-word lower", s.applyTo("c"), "c")
        }

        // shift tap cycle (single taps)
        run {
            val s = QwertyShift()
            s.updateAutoCap("")                       // oneshot
            s.onShiftTap(1000)                        // oneshot → lower (lock)
            s.updateAutoCap("")                       // web: tap ke baad autoCap apply
            T.eq("oneshot + tap → lower", s.mode, QwertyShift.Mode.LOWER)
            s.onShiftTap(2000)                        // lower → oneshot
            s.updateAutoCap("")
            T.eq("lower + tap → oneshot", s.mode, QwertyShift.Mode.ONESHOT)
        }

        // double tap → caps lock
        // NOTE: web ke jaise `onQwertyShift()` manualLock set karta hai aur
        // `updateQwertyAutoCap()` use mode mein apply karta hai — isliye tap ke
        // baad autoCap call zaroori hai (web mein har tap par hoti hai).
        run {
            val s = QwertyShift()
            s.updateAutoCap("")
            s.onShiftTap(1000)
            s.updateAutoCap("")
            s.onShiftTap(1000 + KeyTiming.SHIFT_DOUBLE_TAP_MS - 1)   // within window
            s.updateAutoCap("hello")
            T.eq("double-tap → caps", s.mode, QwertyShift.Mode.CAPS)
            T.eq("caps mein letter uppercase", s.applyTo("a"), "A")
            T.eq("caps lock letter ke baad bhi rehta hai", s.applyTo("b"), "B")
            s.onShiftTap(5000)                        // caps + single tap → lower
            s.updateAutoCap("hello")
            T.eq("caps + tap → lower", s.mode, QwertyShift.Mode.LOWER)
        }

        // double-tap window ke bahar → caps nahi
        run {
            val s = QwertyShift()
            s.updateAutoCap("")
            s.onShiftTap(1000)
            s.updateAutoCap("")
            s.onShiftTap(1000 + KeyTiming.SHIFT_DOUBLE_TAP_MS + 50)
            s.updateAutoCap("")
            T.eq("350ms ke baad double-tap count nahi hota", s.mode != QwertyShift.Mode.CAPS, true)
        }

        // manual lock auto-cap ko override karta hai
        run {
            val s = QwertyShift()
            s.updateAutoCap("")
            s.onShiftTap(1000)
            s.updateAutoCap("")
            s.onShiftTap(1100)                        // caps lock
            s.updateAutoCap("hello")                  // mid-word, par caps locked
            T.eq("caps lock auto-cap ko override karta hai", s.mode, QwertyShift.Mode.CAPS)
        }

        run {
            val s = QwertyShift()
            s.reset()
            T.eq("reset → oneshot", s.mode, QwertyShift.Mode.ONESHOT)
            T.eq("reset → lock null", s.manualLock, null)
        }
    }

    private fun gridMenu() {
        T.section("GridMenu — 21 tiles, capacity 3–8, semicolon order")

        T.eq("21 tiles (20 grid + fixed mic)", GridMenu.TILES.size, 21)
        T.eq("grid tiles = 20", GridMenu.GRID_TILES.size, 20)
        T.eq("enabled (draggable) = 12", GridMenu.ENABLED_TILES.size, 12)
        T.eq("gated = 8", GridMenu.GATED_TILES.size, 8)
        T.eq("mic tile maujood (fixed slot)", GridMenu.byId(GridMenu.MIC_ID) != null, true)
        T.eq("har gated tile par Gboard ka verbatim reason",
            GridMenu.GATED_TILES.all { !it.gateReason.isNullOrBlank() }, true)
        T.eq("koi enabled tile par reason nahi",
            GridMenu.ENABLED_TILES.all { it.gateReason == null }, true)
        T.eq("unique tile ids", GridMenu.TILES.map { it.id }.distinct().size, 21)
        T.eq("har tile par icon hai", GridIcons.requireIcons(), emptyList<String>())

        // Gboard gate reasons (round-2 audit se verbatim)
        T.eq("Translate/Writing Tools reason",
            GridMenu.byId("translate")?.gateReason, "Can't use this tool at the moment. Please try again later.")
        T.eq("Proofread reason", GridMenu.byId("proofread")?.gateReason, "Can't proofread text in this field")
        T.eq("GIF/Sticker/Emoji Kitchen reason", GridMenu.byId("gif")?.gateReason, "Command not available in this app")
        T.eq("Quick Insert reason", GridMenu.byId("quickInsert")?.gateReason, "Disabled because opt-in is disabled")
        T.eq("Scan text reason", GridMenu.byId("scanText")?.gateReason,
            "Gboard needs access to the camera in order to enable Scan text.")

        // capacity rules
        T.eq("MIN_PINNED = 3", GridMenu.MIN_PINNED, 3)
        T.eq("MAX_PINNED = 8", GridMenu.MAX_PINNED, 8)
        T.eq("portrait default = 5", GridMenu.capacity(landscape = false, stored = null), 5)
        T.eq("landscape default = 6", GridMenu.capacity(landscape = true, stored = null), 6)
        T.eq("stored override (landscape par bhi)", GridMenu.capacity(true, 4), 4)
        T.eq("stored 0 → clamp 3 (falsy-zero bug fix)", GridMenu.capacity(false, 0), 3)
        T.eq("stored 99 → clamp 8", GridMenu.capacity(false, 99), 8)
        T.eq("normalizeCapacity(null) = 5", GridMenu.normalizeCapacity(null), 5)
        T.eq("normalizeCapacity(0) = 3", GridMenu.normalizeCapacity(0), 3)
        T.eq("PER_PAGE = 6", GridMenu.PER_PAGE, 6)
        T.eq("storage keys web ke same",
            listOf(GridMenu.KEY_CAPACITY, GridMenu.KEY_PINNED),
            listOf("mg_access_points_count_on_bar", "mg_pinned_ext_ids"))

        // semicolon order (Gboard access_points_showing_order)
        T.eq("order export", GridMenu.toOrderSemicolon(listOf("clipboard", "emoji")), "clipboard;emoji")
        T.eq("order parse", GridMenu.parseOrderSemicolon("clipboard;emoji;theme", 5),
            listOf("clipboard", "emoji", "theme"))
        T.eq("order drops mic", GridMenu.parseOrderSemicolon("mic;clipboard", 5), listOf("clipboard"))
        T.eq("order drops gated ids", GridMenu.parseOrderSemicolon("clipboard;translate;emoji", 5),
            listOf("clipboard", "emoji"))
        T.eq("order drops unknown ids", GridMenu.parseOrderSemicolon("clipboard;nope", 5), listOf("clipboard"))
        T.eq("order dedupes", GridMenu.parseOrderSemicolon("clipboard;clipboard;emoji", 5),
            listOf("clipboard", "emoji"))
        T.eq("order trims to capacity", GridMenu.parseOrderSemicolon("clipboard;emoji;theme;undo;redo", 3).size, 3)
        T.eq("order tolerates spaces", GridMenu.parseOrderSemicolon(" clipboard ; emoji ", 5),
            listOf("clipboard", "emoji"))
        T.eq("null order → empty", GridMenu.parseOrderSemicolon(null, 5), emptyList<String>())
        T.eq("empty order → empty", GridMenu.parseOrderSemicolon("", 5), emptyList<String>())
    }
}
