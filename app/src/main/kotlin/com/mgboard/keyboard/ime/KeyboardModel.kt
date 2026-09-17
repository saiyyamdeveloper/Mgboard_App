package com.mgboard.keyboard.ime

import com.mgboard.keyboard.engine.KbMode
import com.mgboard.keyboard.engine.Panel
import com.mgboard.keyboard.engine.TypingEngine
import com.mgboard.keyboard.grid.GridMenu
import com.mgboard.keyboard.grid.GridTile
import com.mgboard.keyboard.layout.KeyKind
import com.mgboard.keyboard.layout.KeySpec
import com.mgboard.keyboard.layout.KeyboardLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mgboard.keyboard.CpText
import com.mgboard.keyboard.layout.QwertyShift

/**
 * Keyboard ka **platform-agnostic** model.
 *
 * Ismein koi Android class nahi hai (sirf engine + data), isliye plain JVM par
 * unit-test hota hai. [MgBoardIme] isko drive karta hai aur Compose view isi ke
 * `rows()` ko render karta hai.
 *
 * Web ke `state` + `render()` ka equivalent: har action ke baad `rev` badhta hai
 * (Compose isi se recompose karta hai) aur `toast` callback se user feedback.
 */
class KeyboardModel(
    val engine: TypingEngine,
    /** Settings provider — interface isliye taaki test mein fake diya ja sake. */
    val settings: SettingsSource,
) {

    interface SettingsSource {
        val landscape: Boolean
        /** stored capacity override (null = orientation default). */
        val storedCapacity: Int?
        val heightRatio: Double
        val oneHanded: String          // "" | "left" | "right"
        val toolbarVisible: Boolean
        val theme: String              // "system" | "dark" | "light" | "amoled"
        val hapticEnabled: Boolean
        val pinnedIds: List<String>
        val uiHindi: Boolean
        fun setPinnedIds(ids: List<String>)
        /** off → right → left → off (web: cycleOneHanded). Naya mode return karta hai. */
        fun cycleOneHanded(): String
        /** Seedha left/right/off set karo (one-handed exit bar ke arrows). */
        fun setOneHanded(mode: String)
        fun toast(message: String)
        fun onSettingsChanged()        // Settings kholne ka request
    }

    val shift = QwertyShift()

    /**
     * Compose ke liye revision counter — har change par ++.
     * `mutableStateOf` isliye, taaki keyboard screen khud recompose ho jaye
     * (IME aur launcher-preview dono isi model ko use karte hain).
     */
    var rev: Int by mutableStateOf(0)
        private set

    /** External listeners (IME service apna tick badhane ke liye). */
    var onChange: (() -> Unit)? = null

    /** Grid popup khula hai ya nahi. */
    var gridOpen: Boolean = false
        private set

    fun toggleGrid() { gridOpen = !gridOpen; bump() }

    /** Undo/Redo pills visible (web: showUndoRedoPills). */
    var undoPillsVisible: Boolean = false
        private set

    /** Long-press alternate popup (single char) — null = band. */
    var popupChar: String? = null
        private set

    /** Long-press multi popup (period key) — empty = band. */
    var popupMulti: List<String> = emptyList()
        private set

    init {
        engine.onChange = { bump() }   // engine ke har text/state change par UI refresh
    }

    fun bump() { rev++; onChange?.invoke() }

    // ── visible rows ─────────────────────────────────────────────────────────

    fun rows(): List<List<KeySpec?>> {
        val mode = engine.kbMode
        val built = KeyboardLayout.build(
            mode = mode,
            panel = engine.panel,
            row1Mode = engine.row1,
            numPage = engine.numPage,
            shiftUpper = shift.isUpper,
        )
        return built
    }

    /** Toolbar (suggestion strip) ke pinned access points. */
    fun toolbarTiles(): List<GridTile> {
        val cap = GridMenu.capacity(settings.landscape, settings.storedCapacity)
        val ids = settings.pinnedIds.take(cap)
        return ids.mapNotNull { GridMenu.byId(it) }
    }

    fun gridTiles(): List<GridTile> = GridMenu.GRID_TILES

    // ── key actions ──────────────────────────────────────────────────────────

    fun onKeyTap(spec: KeySpec) {
        closePopups()
        when (spec.kind) {
            KeyKind.CHAR -> onChar(spec.glyph)
            KeyKind.MATRA -> engine.onMatra(spec.glyph)
            KeyKind.YUKT -> engine.onYukt()
            KeyKind.VOCALIC_R -> engine.onVocalicRTap()
            KeyKind.BACKSPACE -> engine.backspace()
            KeyKind.ENTER -> engine.onEnter()
            KeyKind.SPACE -> engine.onSpace()
            KeyKind.PERIOD -> engine.insertCharacter(spec.glyph)
            KeyKind.TOGGLE_123 -> engine.onTogglePanel()
            KeyKind.EMOJI -> settings.toast("🙂 Emoji panel — web app mein available")
            KeyKind.GLOBE -> onGlobe()
            KeyKind.SHIFT -> { shift.onShiftTap(); syncShift(); bump() }
            KeyKind.SYMBOL_PAGE -> engine.selectNumPage(engine.numPage % KeyboardLayout.NUM_PAGES + 1)
            KeyKind.BACK_TO_LETTERS -> engine.onTogglePanel()
            KeyKind.GRID_MENU -> gridOpen = !gridOpen
            KeyKind.NONE -> {}
        }
        syncShift()
        bump()
    }

    /** QWERTY letter: shift machine se case decide hota hai (web: onQwertyLetterTap). */
    private fun onChar(glyph: String) {
        if (engine.kbMode == KbMode.QWERTY && glyph.length == 1 && glyph[0].isLetter()) {
            engine.insertCharacter(shift.applyTo(glyph))
        } else {
            engine.insertCharacter(glyph)
        }
    }

    private fun onGlobe() {
        val mode = engine.onGlobeTap()
        settings.toast("🌐 " + mode.displayName)
        shift.reset()
    }

    /** Long-press par kya hona chahiye: alternate char, multi-popup, ya kuch nahi. */
    fun onKeyLongPress(spec: KeySpec) {
        when {
            spec.longPressMulti.isNotEmpty() -> { popupMulti = spec.longPressMulti; bump() }
            spec.longPress != null -> { popupChar = spec.longPress; bump() }
            spec.kind == KeyKind.GLOBE -> settings.toast("🌐 Keyboard switcher: Gondi ↔ English ↔ Hindi")
            else -> {}
        }
    }

    /** Release par alternate commit (web: "Commit alternates only on release"). */
    fun onKeyRelease(spec: KeySpec) {
        val single = popupChar
        val multi = popupMulti
        closePopups()
        when {
            multi.isNotEmpty() -> {}                 // drag-select popup apna choice deta hai
            single != null -> engine.insertCharacter(single)
        }
        bump()
    }

    fun onPopupPick(ch: String) {
        closePopups()
        engine.insertCharacter(ch)
        bump()
    }

    fun closePopups() {
        popupChar = null
        popupMulti = emptyList()
    }

    fun closeGrid() { gridOpen = false; bump() }

    fun onGridTile(tile: GridTile) {
        gridOpen = false
        if (!tile.enabled) {
            // hide-nothing principle: gated tile par Gboard ka verbatim reason
            settings.toast(tile.gateReason ?: "Command not available in this app")
            bump()
            return
        }
        when (tile.id) {
            "undo" -> { undoPillsVisible = true; engine.performUndo() }
            "redo" -> { undoPillsVisible = true; engine.performRedo() }
            "nextLang" -> onGlobe()
            "symbols" -> {
                val toSymbols = engine.panel != Panel.NUMBERS
                engine.onTogglePanel()
                settings.toast(if (toSymbols) "🔣 Symbols mode" else "🔤 Back to letter keyboard")
            }
            "settings" -> settings.onSettingsChanged()
            "oneHanded" -> {
                val next = settings.cycleOneHanded()
                settings.toast(
                    when (next) {
                        "right" -> "🫱 Switched to right-handed keyboard"
                        "left" -> "🫲 Switched to left-handed keyboard"
                        else -> "↩️ Exit one-handed mode"
                    }
                )
            }
            "theme" -> settings.toast("🎨 Theme: " + settings.theme)
            else -> settings.toast(tile.label)
        }
        bump()
    }

    /** Editor text badla (cursor move / paste) → context text se hi re-derive ho. */
    fun onEditorSynced() {
        engine.syncImeContext()
        syncShift()
        bump()
    }

    private fun syncShift() {
        if (engine.kbMode != KbMode.QWERTY) return
        val t = engine.input.text
        val idx = engine.cursorIndex()
        val before = CpText.cpsAsStrings(t)
            .take(idx).joinToString("")
        shift.updateAutoCap(before)
    }

    /** Voice/clipboard jaise bulk input ke liye (Devanagari → Gondi conversion IME layer karta hai). */
    fun insertBulk(text: String) {
        val converted = if (engine.kbMode == KbMode.GONDI)
            com.mgboard.keyboard.engine.DevToGondi.convert(text) else text
        engine.insertTextBulk(converted)
    }

    fun label(tile: GridTile): String =
        if (settings.uiHindi) (tile.labelHi ?: tile.label) else tile.label

    /** Gboard ka verbatim header (hide-nothing: gated tile par bhi yahi title). */
    fun moreLabel(): String = if (settings.uiHindi) "ज़्यादा सुविधाएं" else "More features"

    /** Voice typing abhi backend ke bina hai — honest message (hide-nothing principle). */
    fun voiceHint(): String = if (settings.uiHindi)
        "Voice typing ke liye microphone permission aur speech model chahiye — abhi unavailable"
    else
        "Voice typing needs microphone permission and a speech model — not available yet"
}
