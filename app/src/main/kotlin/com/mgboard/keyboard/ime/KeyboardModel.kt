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
        // ── toolbar-project: flags, order, panels, clipboard ─────────────────
        /** Gboard ke APK config flags (research §2). */
        val toolbarFlags: com.mgboard.keyboard.toolbar.ToolbarFlags
            get() = com.mgboard.keyboard.toolbar.ToolbarFlags()

        /** `access_points_showing_order` — toolbar ka semicolon order (null = default). */
        val toolbarOrderRaw: String? get() = null

        /** Clipboard history (Gboard: `enable_clipboard_content_suggestion`). */
        fun clipboardHistory(): List<String> = emptyList()
        fun addClipboardEntry(text: String) {}
        fun removeClipboardEntry(text: String) {}

        /** Symbols panel ka "Recent" (§5.2) aur expression panel ke Recents/Favorites (§5.1). */
        fun recentSymbols(): List<String> = emptyList()
        fun rememberRecentSymbol(sym: String) {}
        fun recentEmoji(): List<String> = emptyList()
        fun rememberRecentEmoji(e: String) {}
        fun favoriteEmoji(): List<String> = emptyList()

        /** Edit menu ke actions (§5.3) — real IME editor par chalata hai. */
        fun editorSelectAll() {}
        fun editorCopy() {}
        fun editorCut() {}
        fun editorPaste() {}

        /** "Show the keyboard toolbar while typing" toggle. */
        fun setToolbarVisible(v: Boolean) {}

        /** IME action button (strip ke right side par) — editor action perform karo. */
        fun performImeAction() {}
        /** 🌐 IME switch access point. */
        fun showImePicker() {}
        /** Editor mein action button hai? (send/search/go/done) */
        val hasImeAction: Boolean get() = true
        /** Undo/redo chips: "when user edits existing text" */
        val isEditingExistingText: Boolean get() = false

        // ── translate (toolbar-project ka TRANSLATE access point) ──────────────
        /**
         * ML Kit on-device translation engine. `null` = engine available nahi
         * (panel gated reason ke saath khulta hai — hide-nothing).
         */
        fun translateEngine(): com.mgboard.keyboard.translate.TranslateEngine? = null

        /** Saved source/target language codes (BCP-47, ML Kit ke codes). */
        fun translateSrcCode(): String? = null
        fun translateTgtCode(): String? = null
        fun saveTranslateLanguages(src: String, tgt: String) {}

        /** Model download sirf Wi-Fi par (default true — ~30 MB per language). */
        val translateWifiOnly: Boolean get() = true

        /** Async debounce ke liye timer (IME `Handler(Looper.getMainLooper())` deta hai). */
        fun scheduleTimer(delayMs: Long, runnable: () -> Unit): Long = 0L
        fun cancelTimer(token: Long) {}

        // ── GIF / Stickers (toolbar-project §5.1 ke expression tabs) ────────────
        /** Editor is MIME ko Commit Content API se accept karta hai kya. */
        fun mediaEditorSupports(mime: String): Boolean = false

        /** Bundled sticker editor ko bhejo. true = editor ne accept kiya. */
        fun commitBundledSticker(sticker: com.mgboard.keyboard.media.BundledSticker): Boolean = false

        /** Network media (Klipy) editor ko bhejo — caller background thread par. */
        fun commitRemoteMedia(item: com.mgboard.keyboard.media.MediaItem): Boolean = false

        /** Klipy API key (BuildConfig/prefs se). Khali = GIF search gated (setup hint). */
        fun klipyAppKey(): String = ""

        /**
         * Klipy search/trending fetch — background thread par, result callback.
         * `kind` response parse ke liye chahiye (har type ke formats alag hain).
         */
        fun klipyFetch(
            url: String,
            kind: com.mgboard.keyboard.media.KlipyApi.Kind,
            onResult: (com.mgboard.keyboard.media.MediaPage?) -> Unit,
        ) {}

        /** Runtime API key (app ke andar 🔑 field se) — prefs mein save hoti hai. */
        fun setKlipyAppKey(key: String) {}

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

    /**
     * Toolbar (suggestion strip) ke pinned access points — grid-menu tiles.
     * Purana path; naya [strip] Gboard ke access-point model se banta hai.
     */
    fun toolbarTiles(): List<GridTile> {
        val cap = GridMenu.capacity(settings.landscape, settings.storedCapacity)
        val ids = settings.pinnedIds.take(cap)
        return ids.mapNotNull { GridMenu.byId(it) }
    }

    // ══════════════════ toolbar-project (Gboard keyboard toolbar) ══════════════════

    /**
     * Abhi khula hua toolbar panel. Compose state isliye taaki panel khulte/band hote
     * hi keyboard screen recompose ho (IME aur preview dono par same behaviour).
     */
    var toolbarPanel: com.mgboard.keyboard.toolbar.ToolbarPanel by mutableStateOf(
        com.mgboard.keyboard.toolbar.ToolbarPanel.NONE
    )
        private set

    fun openToolbarPanel(p: com.mgboard.keyboard.toolbar.ToolbarPanel) { toolbarPanel = p; bump() }

    /** Suggestion strip ka content — capacity/order/overflow/chips sab Gboard rules se. */
    fun strip(): com.mgboard.keyboard.toolbar.StripContent {
        val cap = GridMenu.capacity(settings.landscape, settings.storedCapacity)
        val order = com.mgboard.keyboard.toolbar.SuggestionStrip.parseOrderSemicolon(
            settings.toolbarOrderRaw ?: com.mgboard.keyboard.toolbar.SuggestionStrip.DEFAULT_ORDER_SEMICOLON, cap)
        return com.mgboard.keyboard.toolbar.SuggestionStrip.build(
            pinnedIds = order,
            capacity = cap,
            flags = settings.toolbarFlags,
            editingExistingText = settings.isEditingExistingText || engine.undo.canUndo || engine.undo.canRedo,
            canUndo = engine.undo.canUndo,
            canRedo = engine.undo.canRedo,
            showImeAction = settings.hasImeAction,
        )
    }

    /** Features menu (⊞) ka content: overflow access points + grid tiles. */
    fun featuresMenu(): List<com.mgboard.keyboard.toolbar.AccessPoint> =
        com.mgboard.keyboard.toolbar.SuggestionStrip.featuresMenu(strip().overflow, GridMenu.GRID_TILES)

    /** Access point tap — panel kholo, action chalao, ya gated reason dikhao (hide-nothing). */
    fun onAccessPoint(ap: com.mgboard.keyboard.toolbar.AccessPoint) {
        if (ap.gated) {
            settings.toast(ap.gateReason(settings.uiHindi) ?: "Command not available in this app")
            return
        }
        ap.panel?.let { openToolbarPanel(it) }
        when (ap.action) {
            com.mgboard.keyboard.toolbar.ToolbarAction.VOICE -> voiceRequest?.invoke()
            com.mgboard.keyboard.toolbar.ToolbarAction.UNDO -> { undoPillsVisible = true; engine.performUndo() }
            com.mgboard.keyboard.toolbar.ToolbarAction.REDO -> { undoPillsVisible = true; engine.performRedo() }
            com.mgboard.keyboard.toolbar.ToolbarAction.SETTINGS -> settings.onSettingsChanged()
            com.mgboard.keyboard.toolbar.ToolbarAction.IME_ACTION -> settings.performImeAction()
            com.mgboard.keyboard.toolbar.ToolbarAction.IME_SWITCH -> settings.showImePicker()
            com.mgboard.keyboard.toolbar.ToolbarAction.TRANSLATE -> {
                translateMode = true
                openTranslatePanel()
            }
            com.mgboard.keyboard.toolbar.ToolbarAction.FEATURES_MENU -> gridOpen = !gridOpen
            com.mgboard.keyboard.toolbar.ToolbarAction.MORE_KEYBOARD_OPTIONS ->
                openToolbarPanel(com.mgboard.keyboard.toolbar.ToolbarPanel.MORE_KEYBOARD_OPTIONS)
            com.mgboard.keyboard.toolbar.ToolbarAction.ONE_HANDED -> onGridTile(GridMenu.byId("oneHanded") ?: return)
            com.mgboard.keyboard.toolbar.ToolbarAction.THEME -> onGridTile(GridMenu.byId("theme") ?: return)
            null -> {}
        }
        bump()
    }

    /** Panel band karo (Gboard: "Close X panel"). */
    fun closeToolbarPanel() {
        if (toolbarPanel == com.mgboard.keyboard.toolbar.ToolbarPanel.TRANSLATE) {
            translateMode = false
            translate.release()
        }
        toolbarPanel = com.mgboard.keyboard.toolbar.ToolbarPanel.NONE
        bump()
    }

    /**
     * Voice access point → voice toolbar. IME isko apne `VoiceWidgetController` se
     * wire karta hai; preview mein bhi wahi hota hai. `null` = wire nahi hua.
     */
    var voiceRequest: (() -> Unit)? = null

    /** Emoji/symbol/clipboard pick → text insert (maujooda pipeline). */
    fun insertFromPanel(text: String) {
        if (translateMode) { appendTranslateText(text); return }
        engine.insertCharacter(text)
        bump()
    }

    /** Clipboard panel se paste — text insert + history mein save. */
    fun pasteFromClipboard(text: String) {
        if (translateMode) { appendTranslateText(text); return }
        engine.insertCharacter(text)
        settings.addClipboardEntry(text)
        clipboardRevision++
        bump()
    }

    // ── translate panel (ML Kit on-device, hi ↔ en) ──────────────────────────

    /**
     * Gboard ka translate panel keyboard ke *upar* khulta hai aur keyboard type
     * karte rehta hai — typed text editor mein nahi, translate buffer mein jaata
     * hai. ✓ dabane par translated text editor mein insert hota hai.
     */
    var translateMode: Boolean = false
        private set

    val translate: com.mgboard.keyboard.translate.TranslateController by lazy {
        com.mgboard.keyboard.translate.TranslateController(
            engineProvider = { settings.translateEngine() },
            scheduleTimer = { d, r -> settings.scheduleTimer(d, r) },
            cancelTimer = { settings.cancelTimer(it) },
            uiHindi = settings.uiHindi,
        ).apply {
            onChanged = { bump() }
            onInsert = { text ->
                // ✓ — translated text editor mein (maujooda insert pipeline se)
                translateMode = false
                closeToolbarPanel()
                engine.insertTextBulk(text)
                bump()
            }
        }
    }

    /** Translate panel khula → source text editor se utha lo (Gboard jaisa). */
    fun openTranslatePanel() {
        openToolbarPanel(com.mgboard.keyboard.toolbar.ToolbarPanel.TRANSLATE)
        translateMode = true
        val s = translate.session
        s.src = com.mgboard.keyboard.translate.TranslateLang.fromCode(settings.translateSrcCode())
            ?: com.mgboard.keyboard.translate.TranslateLang.defaultPair(settings.uiHindi).first
        s.tgt = com.mgboard.keyboard.translate.TranslateLang.fromCode(settings.translateTgtCode())
            ?: com.mgboard.keyboard.translate.TranslateLang.defaultPair(settings.uiHindi).second
        translate.refreshModelStatus()
        bump()
    }

    fun closeTranslatePanel() {
        translateMode = false
        translate.release()
        closeToolbarPanel()
    }

    /** Translate mode mein typed text buffer mein jaa. */
    fun appendTranslateText(text: String) {
        translate.onInput(translate.session.input + text)
        bump()
    }

    /** Translate mode mein backspace — buffer ka aakhri code point hatao. */
    fun translateBackspace() {
        val cur = translate.session.input
        if (cur.isEmpty()) { closeTranslatePanel(); return }
        val cps = cur.codePointCount(0, cur.length)
        val cut = cur.offsetByCodePoints(cur.length, -1)
        translate.onInput(cur.substring(0, cut))
        if (cps <= 1) translate.onInput("")
        bump()
    }

    /** Translate buffer ka content ✓ ke bina seedha insert karo (escape hatch). */
    fun insertTranslateInput() {
        val text = translate.session.input
        if (text.isEmpty()) return
        translateMode = false
        closeToolbarPanel()
        engine.insertTextBulk(text)
        bump()
    }

    fun removeClipboardEntry(text: String) {
        settings.removeClipboardEntry(text)
        clipboardRevision++
        bump()
    }

    fun clearClipboardHistory() {
        settings.clipboardHistory().forEach { settings.removeClipboardEntry(it) }
        clipboardRevision++
        bump()
    }

    /** Clipboard panel ko refresh karne wala counter (history plain prefs mein hai). */
    var clipboardRevision: Int by mutableStateOf(0)
        private set

    // ── symbols panel (Gboard ki 8 categories, §5.2) ───────────────────────────

    /**
     * Symbols panel ka grid. Numbers category mein **Gondi digits** (U+11D50–U+11D59)
     * aate hain — wahi jo keyboard ke numbers panel mein hain.
     */
    fun symbolsPanelGrid(
        category: com.mgboard.keyboard.toolbar.SymbolPanelCategory
    ): List<String> {
        val gondiDigits = com.mgboard.keyboard.data.Numbers.ROWS[0].map { it.g }
        return when (category) {
            com.mgboard.keyboard.toolbar.SymbolPanelCategory.RECENT ->
                settings.recentSymbols().ifEmpty { gondiDigits }
            com.mgboard.keyboard.toolbar.SymbolPanelCategory.NUMBERS -> gondiDigits
            com.mgboard.keyboard.toolbar.SymbolPanelCategory.BRACKETS -> com.mgboard.keyboard.voice.VoiceSymbols.BRACKETS
            com.mgboard.keyboard.toolbar.SymbolPanelCategory.ARROWS -> com.mgboard.keyboard.voice.VoiceSymbols.ARROWS
            com.mgboard.keyboard.toolbar.SymbolPanelCategory.MATHEMATICS -> com.mgboard.keyboard.voice.VoiceSymbols.MATHEMATICS
            com.mgboard.keyboard.toolbar.SymbolPanelCategory.LIST -> com.mgboard.keyboard.voice.VoiceSymbols.LIST
            com.mgboard.keyboard.toolbar.SymbolPanelCategory.SHAPES -> com.mgboard.keyboard.toolbar.SymbolPanelData.SHAPES
            com.mgboard.keyboard.toolbar.SymbolPanelCategory.EMOTICONS -> com.mgboard.keyboard.toolbar.SymbolPanelData.EMOTICONS
        }
    }

    /** Symbols panel ke "Recent" category mein symbol yaad karo. */
    fun rememberRecentSymbol(sym: String) { settings.rememberRecentSymbol(sym) }

    // ── expression panel: recents / favorites ──────────────────────────────────

    fun recentEmoji(): List<String> = settings.recentEmoji()
    fun favoriteEmoji(): List<String> = settings.favoriteEmoji()
    fun rememberRecentEmoji(e: String) { settings.rememberRecentEmoji(e) }

    // ── "More keyboard options" popup (§4) ─────────────────────────────────────

    /**
     * Gboard ka "More keyboard options" — keyboard-level toggles. Labels uske APK
     * strings se (§8 settings list).
     */
    fun moreKeyboardOptions(): List<com.mgboard.keyboard.toolbar.ToolbarItem> {
        val st = settings
        return listOf(
            com.mgboard.keyboard.toolbar.ToolbarItem(
                id = "oneHanded", glyph = "◧",
                en = "One-handed mode", hi = "एक हाथ वाला मोड",
                summaryEn = "Cycle: off → right → left", summaryHi = "बदलें: बंद → दायां → बायां",
            ) { st.cycleOneHanded(); bump() },
            com.mgboard.keyboard.toolbar.ToolbarItem(
                id = "theme", glyph = "◐", en = "Theme", hi = "थीम",
                summaryEn = "Current: " + st.theme, summaryHi = "अभी: " + st.theme,
            ) { st.toast((if (st.uiHindi) "थीम: " else "Theme: ") + st.theme) },
            com.mgboard.keyboard.toolbar.ToolbarItem(
                id = "height", glyph = "↕", en = "Keyboard height", hi = "कीबोर्ड की ऊंचाई",
                summaryEn = "Current: " + (st.heightRatio * 100).toInt() + "%",
                summaryHi = "अभी: " + (st.heightRatio * 100).toInt() + "%",
            ) { bump() },
            com.mgboard.keyboard.toolbar.ToolbarItem(
                id = "toolbar", glyph = "▤",
                en = "Show the keyboard toolbar while typing",
                hi = "टाइप करते समय कीबोर्ड टूलबार दिखाएं",
            ) { st.setToolbarVisible(!st.toolbarVisible); bump() },
            com.mgboard.keyboard.toolbar.ToolbarItem(
                id = "editMenu", glyph = "✂", en = "Open edit menu", hi = "एडिट मेन्यू खोलें",
            ) { openToolbarPanel(com.mgboard.keyboard.toolbar.ToolbarPanel.EDIT_MENU) },
            com.mgboard.keyboard.toolbar.ToolbarItem(
                id = "selectMode", glyph = "▣", en = "Enter select mode", hi = "चयन मोड में जाएं",
            ) { openToolbarPanel(com.mgboard.keyboard.toolbar.ToolbarPanel.SELECT_MODE) },
            com.mgboard.keyboard.toolbar.ToolbarItem(
                id = "settings", glyph = "⚙", en = "Settings", hi = "सेटिंग",
            ) { st.onSettingsChanged() },
        )
    }

    /** Edit menu (§5.3) — select all / copy / cut / paste. */
    fun editMenuItems(): List<com.mgboard.keyboard.toolbar.ToolbarItem> = listOf(
        com.mgboard.keyboard.toolbar.ToolbarItem(
            id = "selectAll", glyph = "I", en = "Select all", hi = "सभी चुनें",
        ) { settings.editorSelectAll(); bump() },
        com.mgboard.keyboard.toolbar.ToolbarItem(
            id = "copy", glyph = "⧉", en = "Copy", hi = "कॉपी करें",
        ) { settings.editorCopy(); clipboardRevision++; bump() },
        com.mgboard.keyboard.toolbar.ToolbarItem(
            id = "cut", glyph = "✂", en = "Cut", hi = "काटें",
        ) { settings.editorCut(); clipboardRevision++; bump() },
        com.mgboard.keyboard.toolbar.ToolbarItem(
            id = "paste", glyph = "⎘", en = "Paste", hi = "चिपकाएं",
        ) { settings.editorPaste(); bump() },
    )

    // ── access-point education footer (Gboard flag) ────────────────────────────

    /** *"Access all keyboard features here"* — ek baar dikhne wala hint. */
    var showEducationFooter: Boolean = true
        private set

    fun dismissEducationFooter() { showEducationFooter = false; bump() }

    fun gridTiles(): List<GridTile> = GridMenu.GRID_TILES

    // ── key actions ──────────────────────────────────────────────────────────

    fun onKeyTap(spec: KeySpec) {
        closePopups()
        when (spec.kind) {
            KeyKind.CHAR -> onChar(spec.glyph)
            KeyKind.MATRA -> engine.onMatra(spec.glyph)
            KeyKind.YUKT -> engine.onYukt()
            KeyKind.VOCALIC_R -> engine.onVocalicRTap()
            KeyKind.BACKSPACE ->
                if (translateMode) translateBackspace() else engine.backspace()
            KeyKind.ENTER ->
                if (translateMode) translate.onInput(translate.session.input + "\n") else engine.onEnter()
            KeyKind.SPACE ->
                if (translateMode) appendTranslateText(" ") else engine.onSpace()
            KeyKind.PERIOD -> engine.insertCharacter(spec.glyph)
            KeyKind.TOGGLE_123 -> engine.onTogglePanel()
            KeyKind.EMOJI -> openToolbarPanel(com.mgboard.keyboard.toolbar.ToolbarPanel.EMOJI)
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
        if (translateMode) {
            val g = if (engine.kbMode == KbMode.QWERTY && glyph.length == 1 && glyph[0].isLetter())
                shift.applyTo(glyph) else glyph
            appendTranslateText(g)
            return
        }
        if (engine.kbMode == KbMode.QWERTY && glyph.length == 1 && glyph[0].isLetter()) {
            engine.insertCharacter(shift.applyTo(glyph))
        } else {
            engine.insertCharacter(glyph)
        }
    }

    private fun onGlobe() {
        val mode = engine.onGlobeTap()
        settings.toast(mode.displayName)
        shift.reset()
    }

    /** Long-press par kya hona chahiye: alternate char, multi-popup, ya kuch nahi. */
    fun onKeyLongPress(spec: KeySpec) {
        when {
            spec.longPressMulti.isNotEmpty() -> { popupMulti = spec.longPressMulti; bump() }
            spec.longPress != null -> { popupChar = spec.longPress; bump() }
            spec.kind == KeyKind.GLOBE -> settings.toast("Keyboard switcher: Gondi ↔ English ↔ Hindi")
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
                settings.toast(if (toSymbols) (if (settings.uiHindi) "चिह्न मोड" else "Symbols mode") else (if (settings.uiHindi) "अक्षर कीबोर्ड" else "Back to letter keyboard"))
            }
            "settings" -> settings.onSettingsChanged()
            "oneHanded" -> {
                val next = settings.cycleOneHanded()
                settings.toast(
                    when (next) {
                        "right" -> "Switched to right-handed keyboard"
                        "left" -> "Switched to left-handed keyboard"
                        else -> "↩️ Exit one-handed mode"
                    }
                )
            }
            "theme" -> settings.toast((if (settings.uiHindi) "थीम: " else "Theme: ") + settings.theme)
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
