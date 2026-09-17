package com.mgboard.keyboard

import com.mgboard.keyboard.grid.GridMenu
import com.mgboard.keyboard.toolbar.AccessPoint
import com.mgboard.keyboard.toolbar.AccessPoints
import com.mgboard.keyboard.toolbar.StripContent
import com.mgboard.keyboard.toolbar.SuggestionStrip
import com.mgboard.keyboard.toolbar.SymbolPanelCategory
import com.mgboard.keyboard.toolbar.SymbolPanelData
import com.mgboard.keyboard.toolbar.ToolbarAction
import com.mgboard.keyboard.toolbar.ToolbarFlags
import com.mgboard.keyboard.toolbar.ToolbarPanel

/**
 * Toolbar project (Gboard keyboard toolbar / suggestion strip) — research ke
 * verbatim rules: capacity 3–8 (5/6), semicolon order, overflow → features menu,
 * undo/redo chips, access-point inventory, panels ke open/close labels.
 */
object ToolbarTests {

    fun run() {
        vocabulary()
        accessPointInventory()
        panels()
        stripCapacityAndOverflow()
        flags()
        chips()
        symbolsPanel()
        expressionTabs()
    }

    private fun build(
        pinned: List<String> = emptyList(),
        capacity: Int = 5,
        flags: ToolbarFlags = ToolbarFlags(),
        editing: Boolean = false,
        canUndo: Boolean = false,
        canRedo: Boolean = false,
        showImeAction: Boolean = true,
    ): StripContent = SuggestionStrip.build(
        pinnedIds = pinned, capacity = capacity, flags = flags,
        editingExistingText = editing, canUndo = canUndo, canRedo = canRedo,
        showImeAction = showImeAction,
    )

    // ══════════════════════════ Gboard vocabulary ══════════════════════════

    private fun vocabulary() {
        T.section("Toolbar — Gboard ki apni vocabulary (§0/§10)")

        // Gboard ki definition: toolbar = suggestions + features access
        T.eq("default pinned = research ka minimum-viable set + symbols (landscape 6 bharne ke liye)",
            SuggestionStrip.DEFAULT_PINNED.map { it.id },
            listOf("voice", "emoji", "clipboard", "translate", "symbols", "settings"))
        T.eq("minimum-viable 5 research se hi hain (§10.3)",
            SuggestionStrip.DEFAULT_PINNED.map { it.id }.filter { it != "symbols" },
            listOf("voice", "emoji", "clipboard", "translate", "settings"))
        T.eq("default order semicolon-separated (access_points_showing_order pattern)",
            SuggestionStrip.DEFAULT_ORDER_SEMICOLON, "voice;emoji;clipboard;translate;symbols;settings")

        // capacity rules grid-menu se shared (Gboard ka ek hi rule)
        T.eq("capacity default 5 portrait", GridMenu.capacity(false, null), 5)
        T.eq("capacity default 6 landscape", GridMenu.capacity(true, null), 6)
        T.eq("capacity range 3–8", GridMenu.MIN_PINNED to GridMenu.MAX_PINNED, 3 to 8)

        // features menu = fixed (Gboard §0.2)
        T.eq("features menu fixed (hataaya nahi ja sakta)", AccessPoints.FEATURES_MENU.fixed, true)
        T.eq("features menu EN = Gboard verbatim", AccessPoints.FEATURES_MENU.en, "More features")
        T.eq("features menu HI = Gboard verbatim", AccessPoints.FEATURES_MENU.hi, "ज़्यादा सुविधाएं")
        T.eq("voice access point fixed", AccessPoints.VOICE.fixed, true)
    }

    // ══════════════════════════ access point inventory ══════════════════════════

    private fun accessPointInventory() {
        T.section("Access points — RESEARCH.md §3 ka inventory (APK evidence)")

        // research ke 12 access points sab maujood
        val ids = AccessPoints.ALL.map { it.id }
        listOf("voice", "writingTools", "proofread", "quickInsert", "emoji", "clipboard",
               "translate", "settings", "more", "undo", "redo", "imeAction", "imeSwitch")
            .forEach { T.eq("access point '" + it + "' maujood", ids.contains(it), true) }

        T.eq("pinnable = 11", AccessPoints.PINNABLE.size, 11)
        T.eq("total access points = 17", AccessPoints.ALL.size, 17)
        T.eq("ids unique", ids.distinct().size, ids.size)
        T.eq("byId lookup", AccessPoints.byId("emoji")?.en, "Emoji")
        T.eq("byId unknown → null", AccessPoints.byId("nope"), null)

        // verbatim labels (APK permission strings se proof)
        T.eq("Voice Typing EN", AccessPoints.VOICE.en, "Voice Typing")
        T.eq("Voice Typing HI (APK verbatim)", AccessPoints.VOICE.hi, "बोली को लिखाई में बदलने की आसान सुविधा")
        T.eq("Writing Tools HI", AccessPoints.WRITING_TOOLS.hi, "लेखन टूल")
        T.eq("Proofread HI", AccessPoints.PROOFREAD.hi, "प्रूफ़रीड")
        T.eq("Clipboard HI", AccessPoints.CLIPBOARD.hi, "क्लिपबोर्ड")
        T.eq("Translate HI", AccessPoints.TRANSLATE.hi, "अनुवाद")
        T.eq("Emoji HI", AccessPoints.EMOJI.hi, "इमोजी")
        T.eq("Settings HI", AccessPoints.SETTINGS.hi, "सेटिंग")

        // permission prompts — Gboard ke exact patterns
        T.eq("voice permission prompt (APK verbatim)",
            AccessPoints.permissionPrompt(AccessPoints.VOICE, false),
            "Allow Voice Typing to access your microphone and voice recordings?")
        T.eq("quickInsert permission prompt (APK verbatim)",
            AccessPoints.permissionPrompt(AccessPoints.QUICK_INSERT, false),
            "Allow Quick Insert to access your contacts?")
        T.eq("clipboard access label",
            AccessPoints.permissionPrompt(AccessPoints.CLIPBOARD, false), "Access clipboard")
        T.eq("settings access label",
            AccessPoints.permissionPrompt(AccessPoints.SETTINGS, false), "Access Gboard settings")
        T.eq("emoji ka koi permission prompt nahi",
            AccessPoints.permissionPrompt(AccessPoints.EMOJI, false), null)
        T.eq("voice prompt HI", AccessPoints.permissionPrompt(AccessPoints.VOICE, true)?.contains("माइक्रोफ़ोन"), true)

        // hide-nothing: gated access points bhi inventory mein hain + verbatim reason
        val gated = AccessPoints.ALL.filter { it.gated }
        T.eq("gated access points = 4 (translate/writingTools/proofread/quickInsert)",
            gated.map { it.id }, listOf("translate", "writingTools", "proofread", "quickInsert"))
        T.eq("har gated par reason", gated.all { !it.gateReasonEn.isNullOrBlank() }, true)
        T.eq("Proofread reason (Gboard verbatim)", AccessPoints.PROOFREAD.gateReasonEn,
            "Can't proofread text in this field")
        T.eq("Quick Insert reason (Gboard verbatim)", AccessPoints.QUICK_INSERT.gateReasonEn,
            "Disabled because opt-in is disabled")
        T.eq("Translate/Writing Tools reason (Gboard verbatim)", AccessPoints.TRANSLATE.gateReasonEn,
            "Can't use this tool at the moment. Please try again later.")

        // actions vs panels
        T.eq("voice = action (panel nahi)", AccessPoints.VOICE.action, ToolbarAction.VOICE)
        T.eq("emoji = panel", AccessPoints.EMOJI.panel, ToolbarPanel.EMOJI)
        T.eq("undo/redo = action", AccessPoints.UNDO.action to AccessPoints.REDO.action,
            ToolbarAction.UNDO to ToolbarAction.REDO)
        T.eq("IME action right side par", AccessPoints.IME_ACTION.rightSide, true)
        T.eq("IME switch right side par", AccessPoints.IME_SWITCH.rightSide, true)
        T.eq("features menu right side par", AccessPoints.FEATURES_MENU.rightSide, true)
    }

    // ══════════════════════════ panels ══════════════════════════

    private fun panels() {
        T.section("Panels — open/close labels (§5, §10.5)")

        T.eq("panels = 11 (NONE samet)", ToolbarPanel.entries.size, 11)
        // Gboard-verbatim panel names
        T.eq("Emoji panel EN", ToolbarPanel.EMOJI.en, "Emoji panel")
        T.eq("Emoji panel HI (APK verbatim)", ToolbarPanel.EMOJI.hi, "इमोजी वाला पैनल")
        T.eq("Symbols HI (APK verbatim: सिंबल वाला पैनल)", ToolbarPanel.SYMBOLS.hi, "सिंबल वाला पैनल")
        T.eq("More keyboard options EN", ToolbarPanel.MORE_KEYBOARD_OPTIONS.en, "More keyboard options")
        T.eq("More keyboard options HI", ToolbarPanel.MORE_KEYBOARD_OPTIONS.hi, "कीबोर्ड के ज़्यादा विकल्प")
        T.eq("Features menu HI", ToolbarPanel.FEATURES_MENU.hi, "सुविधा मेन्यू")
        T.eq("Select mode EN (§5.3)", ToolbarPanel.SELECT_MODE.en, "Select mode")

        // Gboard pattern: "Open X" / "Close X" — TalkBack ke liye
        T.eq("Open emoji panel", ToolbarPanel.EMOJI.openLabel(false), "Open Emoji panel")
        T.eq("Close emoji panel", ToolbarPanel.EMOJI.closeLabel(false), "Close Emoji panel")
        T.eq("इमोजी वाला पैनल खोलें", ToolbarPanel.EMOJI.openLabel(true), "इमोजी वाला पैनल खोलें")
        T.eq("इमोजी वाला पैनल बंद करें", ToolbarPanel.EMOJI.closeLabel(true), "इमोजी वाला पैनल बंद करें")
        T.eq("NONE ka label khali", ToolbarPanel.NONE.openLabel(false) to ToolbarPanel.NONE.closeLabel(true), "" to "")
    }

    // ══════════════════════════ capacity + overflow ══════════════════════════

    private fun stripCapacityAndOverflow() {
        T.section("Suggestion strip — capacity 3–8 + overflow → features menu")

        // default: 6 pinned (portrait capacity 5 → 1 overflow)
        run {
            val s = build()
            T.eq("portrait default strip = 5 access points (capacity 5)", s.left.size, 5)
            T.eq("default order research wala + symbols", s.left.map { it.id },
                listOf("voice", "emoji", "clipboard", "translate", "symbols"))
            T.eq("6th item overflow mein (Gboard overflow rule)", s.overflow.map { it.id },
                listOf("settings"))
        }

        // landscape default 6 — tab poora default set fit hota hai
        run {
            val s = build(capacity = GridMenu.capacity(landscape = true, stored = null))
            T.eq("landscape par 6 access points", s.left.size, 6)
            T.eq("landscape par overflow khali", s.overflow.isEmpty(), true)
        }

        // capacity 3 → overflow
        run {
            val s = build(capacity = 3)
            T.eq("capacity 3 par strip = 3", s.left.size, 3)
            T.eq("baaki 3 overflow mein", s.overflow.size, 3)
            T.eq("overflow = capacity ke aage wale", s.overflow.map { it.id },
                listOf("translate", "symbols", "settings"))
        }

        // capacity clamp (falsy-zero bug fix + 8 max)
        run {
            T.eq("capacity 0 → clamp 3", build(capacity = 0).left.size, 3)
            T.eq("capacity 99 → clamp 8, sab 6 default pinned fit", build(capacity = 99).left.size, 6)
            T.eq("capacity negative → clamp 3", build(capacity = -4).left.size, 3)
        }

        // user ka custom order (semicolon list se) — toolbar ka APNA parser,
        // kyunki GridMenu wala sirf grid-tile ids rakhta hai (voice/imeAction usmein nahi hain)
        run {
            val raw = "settings;emoji;voice;clipboard;translate"
            val order = SuggestionStrip.parseOrderSemicolon(raw, 5)
            T.eq("toolbar parser voice jaise grid-bahar ids bhi rakhta hai", order,
                listOf("settings", "emoji", "voice", "clipboard", "translate"))
            T.eq("grid parser wahi string chhaant deta tha (comparison)",
                GridMenu.parseOrderSemicolon(raw, 5).size, 3)
            val s = build(pinned = order, capacity = 5)
            T.eq("custom order strip par dikha", s.left.map { it.id }, order)
        }

        // toolbar parser ke rules
        run {
            T.eq("null → empty", SuggestionStrip.parseOrderSemicolon(null, 5), emptyList<String>())
            T.eq("khali → empty", SuggestionStrip.parseOrderSemicolon("", 5), emptyList<String>())
            T.eq("unknown ids drop", SuggestionStrip.parseOrderSemicolon("voice;nope;emoji", 5),
                listOf("voice", "emoji"))
            T.eq("dedupe", SuggestionStrip.parseOrderSemicolon("voice;voice;emoji", 5),
                listOf("voice", "emoji"))
            T.eq("spaces trim", SuggestionStrip.parseOrderSemicolon(" voice ; emoji ", 5),
                listOf("voice", "emoji"))
            T.eq("capacity tak limit", SuggestionStrip.parseOrderSemicolon(
                "voice;emoji;clipboard;translate;symbols;settings", 3).size, 3)
            T.eq("capacity clamp (0 → 3)", SuggestionStrip.parseOrderSemicolon(
                "voice;emoji;clipboard;translate", 0).size, 3)
            T.eq("export wapas same string",
                SuggestionStrip.toOrderSemicolon(listOf("voice", "emoji")), "voice;emoji")
        }

        // custom order chhota ho to default pinned se bhar jaata hai
        run {
            val s = build(pinned = listOf("settings"), capacity = 5)
            T.eq("user ka pehla item sabse aage", s.left.first().id, "settings")
            T.eq("baaki default pinned se bhare", s.left.map { it.id },
                listOf("settings", "voice", "emoji", "clipboard", "translate"))
            T.eq("  settings user ne pehle pin kiya isliye dobara overflow mein nahi jaata",
                s.overflow.map { it.id }, listOf("symbols"))
            T.eq("duplicate nahi", s.left.map { it.id }.distinct().size, s.left.size)
        }

        // unknown ids ignore
        run {
            val s = build(pinned = listOf("nope", "settings", "bhi-nahi"), capacity = 5)
            T.eq("unknown ids strip par nahi aate", s.left.map { it.id }.contains("nope"), false)
            T.eq("valid id sabse aage", s.left.first().id, "settings")
        }

        // features menu = overflow + grid tiles
        run {
            val s = build(capacity = 3)
            val menu = SuggestionStrip.featuresMenu(s.overflow, GridMenu.GRID_TILES)
            T.eq("features menu mein overflow sabse pehle", menu.take(2).map { it.id },
                listOf("translate", "symbols"))
            T.eq("features menu mein grid tiles bhi", menu.size > s.overflow.size, true)
            T.eq("features menu mein duplicate nahi", menu.map { it.id }.distinct().size, menu.size)
        }

        // right side ke fixed access points
        run {
            val s = build()
            T.eq("right side par features menu", s.right.map { it.id }, listOf("imeAction", "more"))
            val withSwitch = build(flags = ToolbarFlags(imeSwitchAccessPoint = true))
            T.eq("IME switch flag ON par 🌐 right side par",
                withSwitch.right.map { it.id }, listOf("imeAction", "imeSwitch", "more"))
            val noAction = build(showImeAction = false)
            T.eq("editor mein action na ho to IME action button nahi",
                noAction.right.map { it.id }, listOf("more"))
            val flagOff = build(flags = ToolbarFlags(imeActionAccessPoint = false))
            T.eq("flag OFF par IME action button nahi", flagOff.right.map { it.id }, listOf("more"))
        }
    }

    // ══════════════════════════ flags ══════════════════════════

    private fun flags() {
        T.section("ToolbarFlags — Gboard ke APK config keys (§2)")

        val d = ToolbarFlags()
        T.eq("show_toolbar default ON", d.showToolbar, true)
        T.eq("enable_ime_action_access_point default ON", d.imeActionAccessPoint, true)
        T.eq("enable_ime_switch_access_point default OFF", d.imeSwitchAccessPoint, false)
        T.eq("enable_undo_redo_via_access_point default ON", d.undoRedoViaAccessPoint, true)
        T.eq("enable_writing_tools_icon_in_suggestion_strip default OFF", d.writingToolsInStrip, false)
        T.eq("enable_show_emoji_key_in_horizontal_toolbar default ON", d.emojiKeyInToolbar, true)
        T.eq("enable_access_point_customization default ON", d.accessPointCustomization, true)
        T.eq("enable_access_point_education_footer default ON", d.accessPointEducationFooter, true)
        T.eq("enable_auto_hide_keyboard_header default OFF", d.autoHideKeyboardHeader, false)
        T.eq("enable_suggestion_strip_popup_menu default ON", d.suggestionStripPopupMenu, true)
        T.eq("enable_clipboard_content_suggestion default ON", d.clipboardContentSuggestion, true)
        T.eq("enable_strip_trailing_space_divider default ON", d.stripTrailingSpaceDivider, true)
        T.eq("enable_quick_insert default OFF", d.quickInsert, false)

        // flag OFF → access point strip se hat jaata hai (par features menu mein rehta hai)
        run {
            val s = build(flags = ToolbarFlags(emojiKeyInToolbar = false))
            T.eq("emoji flag OFF → strip par nahi", s.left.map { it.id }.contains("emoji"), false)
            T.eq("  par features menu ke overflow mein chala gaya (hide-nothing)",
                s.overflow.map { it.id }.contains("emoji"), true)
            T.eq("  strip par baaki 5 bache", s.left.size, 5)
        }
        run {
            val s = build(flags = ToolbarFlags(writingToolsInStrip = true))
            T.eq("writing tools flag ON → strip par dikhta hai",
                SuggestionStrip.build(
                    pinnedIds = listOf("writingTools"), capacity = 5,
                    flags = ToolbarFlags(writingToolsInStrip = true),
                    editingExistingText = false, canUndo = false, canRedo = false,
                ).left.first().id, "writingTools")
            val off = build(pinned = listOf("writingTools"), flags = ToolbarFlags(writingToolsInStrip = false))
            T.eq("writing tools flag OFF → strip se filter", off.left.map { it.id }.contains("writingTools"), false)
            T.eq("  par overflow (features menu) mein rehta hai", off.overflow.map { it.id }.contains("writingTools"), true)
        }
        run {
            val on = build(pinned = listOf("quickInsert"), flags = ToolbarFlags(quickInsert = true))
            T.eq("quick insert flag ON → strip par", on.left.map { it.id }.contains("quickInsert"), true)
            val off = build(pinned = listOf("quickInsert"), flags = ToolbarFlags(quickInsert = false))
            T.eq("quick insert flag OFF → strip par nahi", off.left.map { it.id }.contains("quickInsert"), false)
        }
    }

    // ══════════════════════════ undo/redo chips ══════════════════════════

    private fun chips() {
        T.section("Undo/Redo chips — 'when user edits existing text' (APK verbatim)")

        T.eq("naya text edit nahi ho raha → chips nahi",
            build(editing = false, canUndo = true, canRedo = true).let { it.undoChip || it.redoChip }, false)
        T.eq("existing text edit + undo available → undo chip",
            build(editing = true, canUndo = true, canRedo = false).undoChip, true)
        T.eq("existing text edit + redo available → redo chip",
            build(editing = true, canUndo = false, canRedo = true).redoChip, true)
        T.eq("dono available → dono chips",
            build(editing = true, canUndo = true, canRedo = true).let { it.undoChip && it.redoChip }, true)
        T.eq("undo available na ho to chip nahi (editing ke bawajood)",
            build(editing = true, canUndo = false, canRedo = false).let { it.undoChip || it.redoChip }, false)
        T.eq("flag OFF → chips kabhi nahi",
            build(editing = true, canUndo = true, canRedo = true,
                flags = ToolbarFlags(undoRedoViaAccessPoint = false)).let { it.undoChip || it.redoChip }, false)
    }

    // ══════════════════════════ symbols panel ══════════════════════════

    private fun symbolsPanel() {
        T.section("Symbols panel — Gboard ki 8 categories (§5.2, APK se exact)")

        T.eq("8 categories", SymbolPanelCategory.entries.size, 8)
        T.eq("category names (APK order)",
            SymbolPanelCategory.entries.map { it.en },
            listOf("Recent", "Numbers", "Brackets", "Arrows", "Mathematics", "List", "Shapes", "Emoticons"))
        T.eq("Recent HI", SymbolPanelCategory.RECENT.hi, "हाल के")
        T.eq("Shapes HI", SymbolPanelCategory.SHAPES.hi, "आकृतियां")
        T.eq("Emoticons HI", SymbolPanelCategory.EMOTICONS.hi, "इमोटिकॉन")
        T.eq("Shapes grid bhara hua", SymbolPanelData.SHAPES.size >= 40, true)
        T.eq("Emoticons grid bhara hua", SymbolPanelData.EMOTICONS.size >= 24, true)
        T.eq("Shapes mein unicode shapes hain", SymbolPanelData.SHAPES.contains("★"), true)
        T.eq("Emoticons mein classic emoticons hain", SymbolPanelData.EMOTICONS.contains(":-)"), true)
    }

    // ══════════════════════════ expression panel ══════════════════════════

    private fun expressionTabs() {
        T.section("Expression panel — tabs + gating (§5.1)")

        T.eq("5 tabs (APK se)", SymbolPanelData.EXPRESSION_TABS,
            listOf("Emoji", "GIF", "Stickers", "Favorites", "Recents"))
        T.eq("Emoji tab chalta hai (gate nahi)", SymbolPanelData.tabGateReason("Emoji", false), null)
        T.eq("Favorites tab chalta hai", SymbolPanelData.tabGateReason("Favorites", true), null)
        T.eq("Recents tab chalta hai", SymbolPanelData.tabGateReason("Recents", false), null)
        T.eq("GIF gated (hide-nothing)", SymbolPanelData.tabGateReason("GIF", false),
            "Command not available in this app")
        T.eq("Stickers gated", SymbolPanelData.tabGateReason("Stickers", false),
            "Command not available in this app")
        T.eq("GIF gate reason HI", SymbolPanelData.tabGateReason("GIF", true),
            "इस ऐप में यह कमांड उपलब्ध नहीं है")

        // emoji data KeyboardData mein already hai (web se generated)
        T.eq("emoji categories = 9", com.mgboard.keyboard.data.EmojiData.CATEGORIES.size, 9)
        T.eq("har category mein emoji hain",
            com.mgboard.keyboard.data.EmojiData.CATEGORIES.values.all { it.isNotEmpty() }, true)
    }
}
