package com.mgboard.keyboard.layout

import com.mgboard.keyboard.data.Cp
import com.mgboard.keyboard.data.Gondi
import com.mgboard.keyboard.data.Hindi
import com.mgboard.keyboard.data.KeyDef
import com.mgboard.keyboard.data.Numbers
import com.mgboard.keyboard.data.Qwerty
import com.mgboard.keyboard.engine.KbMode
import com.mgboard.keyboard.engine.Panel
import com.mgboard.keyboard.engine.Row1Mode

/** Ek key ka semantic type — IME isse action decide karta hai. */
enum class KeyKind {
    CHAR,           // glyph insert (consonant/vowel/matra/symbol)
    MATRA,          // dependent vowel sign (row1 matra mode)
    YUKT,           // युक्त — composition pending
    VOCALIC_R,      // dynamic 𑶳 key (combo mein consonant + VS_R)
    BACKSPACE,      // repeat 400ms/70ms
    ENTER,
    SPACE,          // drag = cursor move
    PERIOD,         // long-press = 15 symbol alternates
    TOGGLE_123,     // letters ↔ numbers
    EMOJI,
    GLOBE,          // 3-way language cycle
    SHIFT,          // QWERTY
    SYMBOL_PAGE,    // numbers panel page 1/2/3
    BACK_TO_LETTERS,
    GRID_MENU,      // access points popup
    NONE,
}

data class KeySpec(
    val kind: KeyKind,
    val glyph: String = "",
    val label: String = "",
    /** Long-press alternate (single) — web: `lp` + `lpAction`. */
    val longPress: String? = null,
    val longPressLabel: String? = null,
    /** Long-press multi-popup — web: `lpMulti` (period key ke 15 symbols). */
    val longPressMulti: List<String> = emptyList(),
    val weight: Float = 1f,
    val semantic: String? = null,   // "special" | "danger" | "ctrl" | "toggle"
)

/**
 * Keyboard layout model — web ke `buildLettersPanel` / `buildHindiPanel` /
 * `buildQwertyPanel` / numbers panel ka Kotlin equivalent.
 *
 * Row weights web ke measured flex ratios se aate hain (control row):
 * `1.57 / 1.0 / 1.0 / 4.4 / 1.0 / 1.58`.
 */
object KeyboardLayout {

    // ── web ke exact flex ratios (control row) ───────────────────────────────
    const val W_TOGGLE = 1.57f
    const val W_EMOJI = 1.0f
    const val W_GLOBE = 1.0f
    const val W_SPACE = 4.4f
    const val W_PERIOD = 1.0f
    const val W_ENTER = 1.58f
    const val W_QWERTY_SHIFT = 1.6f
    const val W_QWERTY_BACKSPACE = 1.6f

    /** Period key ka long-press popup — web ke 15 symbols, same order. */
    val PERIOD_ALTERNATES = listOf("&", "%", "+", "\"", "-", ":", "'", "@", ";", "/", "(", ")", "#", "!", ",", "?")

    /** Numbers panel ke 3 pages (web: numPage 1 = A, 2 = B, 3 = Calc). */
    const val NUM_PAGES = 3

    /** Grid popup ek screen par kitne tiles dikhata hai (web: EXT_PER_PAGE). */
    val PER_PAGE_GRID: Int get() = com.mgboard.keyboard.grid.GridMenu.PER_PAGE

    private fun key(def: KeyDef?, kind: KeyKind = KeyKind.CHAR): KeySpec? =
        def?.let {
            KeySpec(
                kind = kind,
                glyph = it.g,
                label = it.s,
                longPress = it.lp,
                longPressLabel = it.lps,
                semantic = it.cls,
            )
        }

    /** Row 1 — dynamic: vowel ↔ matra (web: renderRow1 / renderRow1H). */
    fun row1(mode: KbMode, row1: Row1Mode): List<KeySpec> {
        val defs = when (mode) {
            KbMode.HINDI -> if (row1 == Row1Mode.MATRA) Hindi.MATRA_KEYS else Hindi.VOWEL_KEYS
            else -> if (row1 == Row1Mode.MATRA) Gondi.MATRA_KEYS else Gondi.VOWEL_KEYS
        }
        return defs.map { KeySpec(kind = KeyKind.MATRA, glyph = it.g, label = it.s) }
    }

    /** Rows 2–5 — Gondi ya Hindi panel (web: [ROW2..ROW5] / [DEV_ROW2..DEV_ROW5]). */
    fun letterRows(mode: KbMode): List<List<KeySpec?>> {
        val rows = if (mode == KbMode.HINDI) Hindi.LETTER_ROWS else Gondi.LETTER_ROWS
        return rows.map { row ->
            row.map { def ->
                when {
                    def == null -> KeySpec(KeyKind.VOCALIC_R, glyph = Cp.VS_R)   // dynamic slot
                    def.s == "DEL" -> KeySpec(KeyKind.BACKSPACE, label = "DEL", semantic = "danger")
                    def.s == "युक्त" || def.g == Cp.VIRAMA ->
                        KeySpec(KeyKind.YUKT, glyph = def.g, label = def.s, semantic = def.cls)
                    else -> key(def)
                }
            }
        }
    }

    /** Control row — teeno modes mein same structure, sirf toggle glyph alag. */
    fun controlRow(mode: KbMode, showGridMenu: Boolean = false): List<KeySpec> {
        val toggleGlyph = when (mode) {
            KbMode.GONDI -> GONDI_TOGGLE_GLYPH      // web: '?𑵑𑵒𑵓'
            else -> "?123"
        }
        val row = mutableListOf(
            KeySpec(KeyKind.TOGGLE_123, glyph = toggleGlyph, semantic = "toggle", weight = W_TOGGLE),
            KeySpec(KeyKind.EMOJI, glyph = "🙂", weight = W_EMOJI),
            KeySpec(KeyKind.GLOBE, glyph = "🌐", weight = W_GLOBE),
        )
        if (showGridMenu) row += KeySpec(KeyKind.GRID_MENU, glyph = "⋯", weight = W_EMOJI)
        row += KeySpec(KeyKind.SPACE, label = "", weight = W_SPACE)
        row += KeySpec(KeyKind.PERIOD, glyph = ".", label = ".", semantic = "ctrl",
            longPressMulti = PERIOD_ALTERNATES, weight = W_PERIOD)
        row += KeySpec(KeyKind.ENTER, glyph = "⏎", weight = W_ENTER)
        return row
    }

    /** QWERTY letters — 3 rows + shift/backspace (web: buildQwertyPanel). */
    fun qwertyRows(upper: Boolean): List<List<KeySpec>> {
        val rows = Qwerty.ROWS.mapIndexed { ri, row ->
            val keys = row.map { ch ->
                KeySpec(KeyKind.CHAR, glyph = if (upper) ch.uppercase() else ch.lowercase())
            }.toMutableList()
            if (ri == 2) {
                keys.add(0, KeySpec(KeyKind.SHIFT, glyph = "⇧", semantic = "ctrl", weight = W_QWERTY_SHIFT))
                keys += KeySpec(KeyKind.BACKSPACE, glyph = "⌫", semantic = "danger", weight = W_QWERTY_BACKSPACE)
            }
            keys.toList()
        }
        return rows
    }

    /**
     * Numbers / symbols panel (Gondi + Hindi share; web: #kbN).
     * Web mein 3 pages hain: 1 = digits+symbols (NUM_ROWS), 2 = extra symbols,
     * 3 = calculator (CALC_OPS). Yahan page 1/2 NUM_ROWS se bante hain aur
     * page 3 calculator row add karta hai.
     */
    fun numberRows(page: Int, mode: KbMode): List<List<KeySpec>> {
        val base = Numbers.ROWS.map { row ->
            row.map { def ->
                if (def.s == "DEL") KeySpec(KeyKind.BACKSPACE, glyph = "⌫", semantic = "danger")
                else KeySpec(KeyKind.CHAR, glyph = def.g, label = def.s, semantic = def.cls)
            }
        }
        if (page != 3) return base
        val calc = Numbers.CALC_OPS.map { KeySpec(KeyKind.CHAR, glyph = it, label = it) }
        return base + listOf(calc)
    }

    @Suppress("unused")
    private fun legacyNumberRows(page: Int, mode: KbMode): List<List<KeySpec>> {
        val rows = Numbers.ROWS
        return rows.map { row ->
            row.map { def ->
                if (def.s == "DEL") KeySpec(KeyKind.BACKSPACE, glyph = "⌫", semantic = "danger")
                else KeySpec(KeyKind.CHAR, glyph = def.g, label = def.s, semantic = def.cls)
            }
        }
    }

    /** Numbers panel ka "wapas letters par" glyph (web: numBackToggle). */
    fun numbersBackGlyph(mode: KbMode): String = when (mode) {
        KbMode.GONDI -> GONDI_BACK_GLYPH     // web: '𑴌𑴍𑴎'
        KbMode.HINDI -> "कखग"
        KbMode.QWERTY -> "abc"
    }

    /** Poora visible keyboard — IME/preview dono isi ko render karte hain. */
    fun build(mode: KbMode, panel: Panel, row1Mode: Row1Mode, numPage: Int, shiftUpper: Boolean): List<List<KeySpec?>> =
        if (panel == Panel.NUMBERS) numberRows(numPage, mode)
        else when (mode) {
            KbMode.QWERTY -> qwertyRows(shiftUpper)
            else -> listOf(row1(mode, row1Mode)) + letterRows(mode) + listOf(controlRow(mode))
        }

    // web: '?𑵑𑵒𑵓' — supplementary chars Kotlin escape se nahi likhe ja sakte
    val GONDI_TOGGLE_GLYPH: String = "?" + String(intArrayOf(0x11D51, 0x11D52, 0x11D53), 0, 3)  // D1 D2 D3
    /** web: '𑴌𑴍𑴎' */
    val GONDI_BACK_GLYPH: String = String(intArrayOf(0x11D0C, 0x11D0D, 0x11D0E), 0, 3)
}

/**
 * Key behaviour timings — web ke exact values (`attachKey`).
 * Inhe badalna nahi: user constraint hai ki existing repeat timing reuse ho.
 */
object KeyTiming {
    /** Backspace hold: pehla repeat 400ms baad, phir har 70ms. */
    const val REPEAT_INITIAL_DELAY_MS = 400L
    const val REPEAT_INTERVAL_MS = 70L

    /** Long-press popup / alternate: 300ms. */
    const val LONG_PRESS_MS = 300L

    /** Multi-select popup mein drag ke liye deadzone (web: DEADZONE_PX). */
    const val MULTI_POP_DEADZONE_DP = 14

    /** Spacebar drag → cursor move (web: SPACE_DRAG_THRESHOLD / SPACE_PX_PER_CHAR). */
    const val SPACE_DRAG_THRESHOLD_DP = 6
    const val SPACE_DP_PER_CHAR = 16

    /** QWERTY shift double-tap window (web: 350ms → caps lock). */
    const val SHIFT_DOUBLE_TAP_MS = 350L
}
