package com.mgboard.keyboard.toolbar

import com.mgboard.keyboard.grid.GridMenu
import com.mgboard.keyboard.grid.GridTile

/**
 * Keyboard toolbar / suggestion strip ka **pure-Kotlin** model.
 *
 * Research: `docs/research/toolbar-project/RESEARCH.md` (Gboard 18.3.1-beta APK evidence).
 * Gboard ki apni vocabulary use ki gayi hai (§10 conclusions):
 *
 *  - toolbar            → [KeyboardToolbar] / **suggestion strip**
 *  - toolbar ke icons   → **access points**
 *  - "More" overflow    → **features menu**
 *  - neeche wala keys   → keyboard body
 *
 * Gboard ki definition (§9): toolbar ke **3 kaam** hain —
 *  1. suggestions dikhana
 *  2. features ka access dena (access points)
 *  3. contextual chips (undo/redo, quick insert, emoji suggestions)
 *
 * Rules jo verbatim APK strings se aaye (§2):
 *  - default capacity **5 portrait / 6 landscape**, valid range **3–8**
 *  - order **semicolon-separated** (`access_points_showing_order`)
 *  - jo icons fit nahi hote → **features menu** (overflow)
 *  - *"The Undo and Redo chips appear in the suggestion strip via access point,
 *     when user edits existing text."*
 *
 * Yeh file Android-free hai, isliye poori logic JVM par test hoti hai.
 */

/** Toolbar se khulne wale panels (§5) — har ek ka Gboard-verbatim open/close label hai. */
enum class ToolbarPanel(val en: String, val hi: String) {
    NONE("", ""),
    EMOJI("Emoji panel", "इमोजी वाला पैनल"),
    SYMBOLS("Symbols panel", "सिंबल वाला पैनल"),
    CLIPBOARD("Clipboard panel", "क्लिपबोर्ड पैनल"),
    TRANSLATE("Translate panel", "अनुवाद पैनल"),
    WRITING_TOOLS("Writing Tools", "लेखन टूल"),
    PROOFREAD("Proofread", "प्रूफ़रीड"),
    EDIT_MENU("Edit menu", "एडिट मेन्यू"),
    FEATURES_MENU("Features menu", "सुविधा मेन्यू"),
    MORE_KEYBOARD_OPTIONS("More keyboard options", "कीबोर्ड के ज़्यादा विकल्प"),
    SELECT_MODE("Select mode", "चयन मोड"),
    ;

    fun label(hindi: Boolean): String = if (hindi) hi else en

    /** Gboard pattern: "Open X" / "Close X" (TalkBack ke liye zaroori — §10.5). */
    fun openLabel(hindi: Boolean): String =
        if (this == NONE) "" else if (hindi) "$hi खोलें" else "Open $en"

    fun closeLabel(hindi: Boolean): String =
        if (this == NONE) "" else if (hindi) "$hi बंद करें" else "Close $en"
}

/**
 * Ek access point (toolbar icon).
 *
 * `panel` = tap par kaun sa panel khulta hai (null = koi action, jaise voice/undo).
 * `gated` = backend/permission ke bina — **hide-nothing** rule ke mutabik icon dikhta
 * hai aur Gboard ka verbatim reason deta hai.
 */
data class AccessPoint(
    val id: String,
    val glyph: String,
    val en: String,
    val hi: String,
    val panel: ToolbarPanel? = null,
    val action: ToolbarAction? = null,
    val gated: Boolean = false,
    val gateReasonEn: String? = null,
    val gateReasonHi: String? = null,
    /** Fixed access point hataaya/pin nahi kiya ja sakta (Gboard §0.2: grid icon fixed). */
    val fixed: Boolean = false,
    /** Strip ke right side par hota hai (IME action / IME switch). */
    val rightSide: Boolean = false,
) {
    fun label(hindi: Boolean): String = if (hindi) hi else en
    fun gateReason(hindi: Boolean): String? = if (hindi) gateReasonHi ?: gateReasonEn else gateReasonEn
}

/** Access point ka non-panel action. */
enum class ToolbarAction {
    VOICE,          // voice toolbar kholo (voice-pill-project se wire)
    UNDO,
    REDO,
    SETTINGS,
    IME_ACTION,     // editor action: send/search/go/next/done
    IME_SWITCH,     // 🌐 keyboard switch
    FEATURES_MENU,
    MORE_KEYBOARD_OPTIONS,
    ONE_HANDED,
    THEME,
    TRANSLATE,      // Gboard ka translate panel — MgBoard mein ML Kit on-device se live
}

/**
 * Toolbar ke access points — RESEARCH.md §3 ka inventory.
 *
 * Gboard-exact labels (permission dialogs se proof: *"Allow <name> to access …"*
 * sirf access points ke liye banti hain).
 */
object AccessPoints {

    val VOICE = AccessPoint(
        id = "voice", glyph = "◉",
        en = "Voice Typing", hi = "बोली को लिखाई में बदलने की आसान सुविधा",
        action = ToolbarAction.VOICE, fixed = true,
    )

    val EMOJI = AccessPoint(
        id = "emoji", glyph = "☺", en = "Emoji", hi = "इमोजी",
        panel = ToolbarPanel.EMOJI,
    )

    val CLIPBOARD = AccessPoint(
        id = "clipboard", glyph = "⎘", en = "Clipboard", hi = "क्लिपबोर्ड",
        panel = ToolbarPanel.CLIPBOARD,
    )

    /**
     * Translate — research mein Gboard ke liye "gated" tha (backend chahiye tha).
     * Ab **ML Kit on-device translation** (`hi` ↔ `en` officially supported) se yeh
     * live hai, isliye gate hata diya. Engine unavailable hone par panel khud
     * Gboard ka verbatim reason dikhata hai (`TranslateError.NO_ENGINE`) — hide-nothing.
     * Gboard ke UI rules: source picker / ⇄ swap / target picker / ✓ insert.
     */
    val TRANSLATE = AccessPoint(
        id = "translate", glyph = "अ", en = "Translate", hi = "अनुवाद",
        panel = ToolbarPanel.TRANSLATE,
        action = ToolbarAction.TRANSLATE,
    )

    val WRITING_TOOLS = AccessPoint(
        id = "writingTools", glyph = "✒", en = "Writing Tools", hi = "लेखन टूल",
        panel = ToolbarPanel.WRITING_TOOLS,
        gated = true,
        gateReasonEn = "Can't use this tool at the moment. Please try again later.",
        gateReasonHi = "इस समय इस टूल का इस्तेमाल नहीं किया जा सकता. कृपया बाद में फिर कोशिश करें.",
    )

    val PROOFREAD = AccessPoint(
        id = "proofread", glyph = "✓", en = "Proofread", hi = "प्रूफ़रीड",
        panel = ToolbarPanel.PROOFREAD,
        gated = true,
        gateReasonEn = "Can't proofread text in this field",
        gateReasonHi = "इस फ़ील्ड में टेक्स्ट का प्रूफ़रीड नहीं किया जा सकता",
    )

    val QUICK_INSERT = AccessPoint(
        id = "quickInsert", glyph = "+", en = "Quick Insert", hi = "क्विक इंसर्ट",
        gated = true,
        gateReasonEn = "Disabled because opt-in is disabled",
        gateReasonHi = "ऑप्ट-इन बंद होने की वजह से अक्षम",
    )

    val SETTINGS = AccessPoint(
        id = "settings", glyph = "⚙", en = "Gboard settings", hi = "सेटिंग",
        action = ToolbarAction.SETTINGS,
    )

    val THEME = AccessPoint(
        id = "theme", glyph = "◐", en = "Theme", hi = "थीम",
        action = ToolbarAction.THEME,
    )

    val SYMBOLS = AccessPoint(
        id = "symbols", glyph = "?123", en = "Symbols", hi = "चिह्न",
        panel = ToolbarPanel.SYMBOLS,
    )

    val ONE_HANDED = AccessPoint(
        id = "oneHanded", glyph = "◧", en = "One-handed mode", hi = "एक हाथ वाला मोड",
        action = ToolbarAction.ONE_HANDED,
    )

    val UNDO = AccessPoint(
        id = "undo", glyph = "↶", en = "Undo", hi = "पहले जैसा करें",
        action = ToolbarAction.UNDO,
    )

    val REDO = AccessPoint(
        id = "redo", glyph = "↷", en = "Redo", hi = "दोबारा करें",
        action = ToolbarAction.REDO,
    )

    /** IME action button (↵/send/search/go) — `enable_ime_action_access_point`. */
    val IME_ACTION = AccessPoint(
        id = "imeAction", glyph = "↵", en = "Keyboard action", hi = "कीबोर्ड कार्रवाई",
        action = ToolbarAction.IME_ACTION, fixed = true, rightSide = true,
    )

    /** IME switch (🌐) — `enable_ime_switch_access_point`. */
    val IME_SWITCH = AccessPoint(
        id = "imeSwitch", glyph = "🌐", en = "Switch keyboard", hi = "कीबोर्ड बदलें",
        action = ToolbarAction.IME_SWITCH, fixed = true, rightSide = true,
    )

    /**
     * Features menu (⊞) — Gboard ka "More features". **Fixed**: hataaya ya drag nahi
     * kiya ja sakta (§0.2), aur jo access points capacity mein fit nahi hote woh isi
     * ke andar chale jaate hain (§2 overflow rule).
     */
    val FEATURES_MENU = AccessPoint(
        id = "more", glyph = "⊞", en = "More features", hi = "ज़्यादा सुविधाएं",
        action = ToolbarAction.FEATURES_MENU, fixed = true, rightSide = true,
    )

    /** "More keyboard options" popup (§4 ki 4 popup-menu families mein se ek). */
    val MORE_KEYBOARD_OPTIONS = AccessPoint(
        id = "moreKeyboardOptions", glyph = "⋮", en = "More keyboard options",
        hi = "कीबोर्ड के ज़्यादा विकल्प",
        action = ToolbarAction.MORE_KEYBOARD_OPTIONS, fixed = true, rightSide = true,
    )

    /** Pin/customize karne layak access points (strip par order mein). */
    val PINNABLE: List<AccessPoint> = listOf(
        VOICE, EMOJI, CLIPBOARD, TRANSLATE, WRITING_TOOLS, PROOFREAD,
        QUICK_INSERT, SYMBOLS, THEME, ONE_HANDED, SETTINGS,
    )

    val ALL: List<AccessPoint> = PINNABLE + listOf(
        UNDO, REDO, IME_ACTION, IME_SWITCH, FEATURES_MENU, MORE_KEYBOARD_OPTIONS,
    )

    fun byId(id: String): AccessPoint? = ALL.firstOrNull { it.id == id }

    /** Gboard ke access-point permission strings (APK evidence) — sirf inhi ke liye banti hain. */
    fun permissionPrompt(ap: AccessPoint, hindi: Boolean): String? = when (ap.id) {
        "voice" -> if (hindi) "बोली को लिखाई में बदलने की आसान सुविधा को आपका माइक्रोफ़ोन और वॉइस रिकॉर्डिंग एक्सेस करने दें?"
                   else "Allow Voice Typing to access your microphone and voice recordings?"
        "writingTools" -> if (hindi) "लेखन टूल को एक्सेस करें" else "Access Writing Tools"
        "proofread" -> if (hindi) "प्रूफ़रीड को एक्सेस करें" else "Access Proofread"
        "quickInsert" -> if (hindi) "क्विक इंसर्ट को आपका संपर्क एक्सेस करने दें?"
                        else "Allow Quick Insert to access your contacts?"
        "clipboard" -> if (hindi) "क्लिपबोर्ड एक्सेस करें" else "Access clipboard"
        "translate" -> if (hindi) "अनुवाद एक्सेस करें" else "Access translate"
        "settings" -> if (hindi) "सेटिंग एक्सेस करें" else "Access Gboard settings"
        else -> null
    }
}

/**
 * Toolbar ke behaviour flags — Gboard ke APK config keys ke 1:1 naam.
 * Defaults wahi hain jo Gboard ke flags ke hain (research §2).
 */
data class ToolbarFlags(
    /** `show_toolbar` — "Show the keyboard toolbar while typing". */
    val showToolbar: Boolean = true,
    /** `enable_ime_action_access_point` — strip par editor action button. */
    val imeActionAccessPoint: Boolean = true,
    /** `enable_ime_switch_access_point` — strip par 🌐. */
    val imeSwitchAccessPoint: Boolean = false,
    /** `enable_undo_redo_via_access_point` — undo/redo chips strip par. */
    val undoRedoViaAccessPoint: Boolean = true,
    /** `enable_writing_tools_icon_in_suggestion_strip`. */
    val writingToolsInStrip: Boolean = false,
    /** `enable_show_emoji_key_in_horizontal_toolbar`. */
    val emojiKeyInToolbar: Boolean = true,
    /** `enable_access_point_customization` — drag/pin customize. */
    val accessPointCustomization: Boolean = true,
    /** `enable_access_point_education_footer` — ek baar dikhne wala hint. */
    val accessPointEducationFooter: Boolean = true,
    /** `enable_auto_hide_keyboard_header`. */
    val autoHideKeyboardHeader: Boolean = false,
    /** `enable_suggestion_strip_popup_menu`. */
    val suggestionStripPopupMenu: Boolean = true,
    /** `enable_clipboard_content_suggestion`. */
    val clipboardContentSuggestion: Boolean = true,
    /** `enable_strip_trailing_space_divider`. */
    val stripTrailingSpaceDivider: Boolean = true,
    /** `force_enable_horizontal_toolbar_on_foldables`. */
    val forceHorizontalOnFoldables: Boolean = false,
    /** `enable_quick_insert`. */
    val quickInsert: Boolean = false,
)

/** Strip par kya dikhega — [SuggestionStrip.build] ka output. */
data class StripContent(
    /** Capacity ke andar aane wale access points (order mein). */
    val left: List<AccessPoint>,
    /** Right side ke fixed access points (IME action / switch / features menu). */
    val right: List<AccessPoint>,
    /** Capacity se bahar → features menu ke andar (§2 overflow rule). */
    val overflow: List<AccessPoint>,
    /** "The Undo and Redo chips appear … when user edits existing text." */
    val undoChip: Boolean,
    val redoChip: Boolean,
)

/** Suggestion strip banane ka logic — capacity, order, overflow, chips. */
object SuggestionStrip {

    /**
     * @param pinnedIds      user ka order (semicolon-list se aata hai — [GridMenu.parseOrderSemicolon])
     * @param capacity       3–8, orientation ke hisaab se ([GridMenu.capacity])
     * @param flags          Gboard ke config flags
     * @param editingExistingText undo/redo chips dikhane ke liye
     * @param foldable       foldable device par alag capacity/order keys hote hain
     *                       (`foldable_access_points_count_on_bar`)
     */
    fun build(
        pinnedIds: List<String>,
        capacity: Int,
        flags: ToolbarFlags,
        editingExistingText: Boolean,
        canUndo: Boolean,
        canRedo: Boolean,
        showImeAction: Boolean = true,
        foldable: Boolean = false,
    ): StripContent {
        val cap = capacity.coerceIn(GridMenu.MIN_PINNED, GridMenu.MAX_PINNED)

        // order: user ki list, phir default pinned jo list mein nahi hain
        val ordered = (pinnedIds.mapNotNull { AccessPoints.byId(it) } + DEFAULT_PINNED)
            .distinctBy { it.id }

        // Flag OFF wale access point strip se hat kar **features menu** mein chale
        // jaate hain (feature chhupta nahi — hide-nothing principle).
        val stripEligible = mutableListOf<AccessPoint>()
        val flagHidden = mutableListOf<AccessPoint>()
        ordered.forEach { ap ->
            val onStrip = when (ap.id) {
                "emoji" -> flags.emojiKeyInToolbar
                "writingTools" -> flags.writingToolsInStrip
                "quickInsert" -> flags.quickInsert
                else -> true
            }
            if (onStrip) stripEligible += ap else flagHidden += ap
        }

        val left = stripEligible.take(cap)
        val overflow = stripEligible.drop(cap) + flagHidden

        val right = buildList {
            if (flags.imeActionAccessPoint && showImeAction) add(AccessPoints.IME_ACTION)
            if (flags.imeSwitchAccessPoint) add(AccessPoints.IME_SWITCH)
            add(AccessPoints.FEATURES_MENU)
        }

        val chips = flags.undoRedoViaAccessPoint && editingExistingText
        return StripContent(
            left = left,
            right = right,
            overflow = overflow,
            undoChip = chips && canUndo,
            redoChip = chips && canRedo,
        )
    }

    /**
     * Default pinned access points.
     *
     * Research §10.3 ka minimum-viable set (Voice, Emoji, Clipboard, Translate,
     * Settings) + **Symbols** — kyunki Gboard ki default landscape capacity 6 hai aur
     * 5 items se strip adhi dikhti. "More" (features menu) fixed right-side access
     * point hai, isliye woh capacity count mein nahi aata.
     */
    val DEFAULT_PINNED: List<AccessPoint> = listOf(
        AccessPoints.VOICE,
        AccessPoints.EMOJI,
        AccessPoints.CLIPBOARD,
        AccessPoints.TRANSLATE,
        AccessPoints.SYMBOLS,
        AccessPoints.SETTINGS,
    )

    val DEFAULT_ORDER_SEMICOLON: String =
        DEFAULT_PINNED.joinToString(";") { it.id }

    /**
     * Toolbar ki semicolon order list parse karo (Gboard key: `access_points_showing_order`).
     *
     * [GridMenu.parseOrderSemicolon] **grid tiles** ke ids filter karta hai — toolbar ke
     * access points alag inventory hain (`voice`, `imeAction`, `writingTools` grid mein
     * nahi hote), isliye yahan apna parser hai. Rules same: unknown ids drop, dedupe,
     * spaces trim, capacity tak limit.
     */
    fun parseOrderSemicolon(raw: String?, capacity: Int): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val cap = capacity.coerceIn(GridMenu.MIN_PINNED, GridMenu.MAX_PINNED)
        return raw.split(";")
            .map { it.trim() }
            .filter { AccessPoints.byId(it) != null }
            .distinct()
            .take(cap)
    }

    /** Access points ki list → semicolon order string (settings mein save karne ke liye). */
    fun toOrderSemicolon(ids: List<String>): String = ids.joinToString(";")

    /**
     * Features menu ka content — overflow access points + grid-menu ke tiles.
     * Gboard: *"Access all keyboard features here"*.
     */
    fun featuresMenu(overflow: List<AccessPoint>, gridTiles: List<GridTile>): List<AccessPoint> {
        val fromGrid = gridTiles.mapNotNull { t -> AccessPoints.byId(t.id) }
        return (overflow + fromGrid).distinctBy { it.id }
    }
}

/**
 * Symbols panel ki categories — Gboard APK se **exact** (§5.2):
 * `Numbers · Brackets · Arrows · Mathematics · List · Shapes · Emoticons · Recent`
 *
 * Voice widget ke symbols overlay mein 6 categories thin (voice-toolbar wala set);
 * yeh **symbols panel** ka poora 8-category set hai.
 */
enum class SymbolPanelCategory(val en: String, val hi: String) {
    RECENT("Recent", "हाल के"),
    NUMBERS("Numbers", "नंबर"),
    BRACKETS("Brackets", "कोष्ठक"),
    ARROWS("Arrows", "तीर"),
    MATHEMATICS("Mathematics", "गणित"),
    LIST("List", "सूची"),
    SHAPES("Shapes", "आकृतियां"),
    EMOTICONS("Emoticons", "इमोटिकॉन"),
    ;

    fun label(hindi: Boolean): String = if (hindi) hi else en
}

object SymbolPanelData {

    val SHAPES: List<String> = listOf(
        "■", "□", "▢", "▣", "▤", "▥", "▦", "▧", "▨", "▩",
        "▲", "△", "▶", "▷", "▼", "▽", "◀", "◁",
        "●", "○", "◍", "◎", "◐", "◑", "◒", "◓",
        "◆", "◇", "◈", "♦", "★", "☆", "✦", "✧", "✩", "✪",
        "❤", "❥", "❦", "❧", "✿", "❀", "❁", "☘", "❉", "❋",
    )

    val EMOTICONS: List<String> = listOf(
        ":-)", ":)", ":-D", ":D", ":-(", ":(", ";-)", ";)",
        ":-P", ":P", ":-O", ":O", ":-*", ":*", ":-|", ":|",
        ">:(", ":-/", ":/", ":')", ":'(", ":-X", ":X", "O:-)",
        ":-B", ":3", "^_^", "T_T", "-_-", "o_O", "<3", "</3",
    )

    /**
     * Emoji panel ke tabs. Gboard ke APK wale 5 (`Emoji · GIF · Stickers · Favorites ·
     * Recents`, §5.1) + **Klipy ke saare content types** user ke order par:
     * `Clips` (video) aur `Memes`. (AI Emoji generation POST-job hai — scope se bahar.)
     */
    val EXPRESSION_TABS = listOf("Emoji", "GIF", "Clips", "Stickers", "Memes", "Favorites", "Recents")

    /** Tab ka commit MIME — editor support check isi se hota hai. */
    fun tabMime(tab: String): String? = when (tab) {
        "GIF" -> "image/gif"
        "Clips" -> "video/mp4"
        "Stickers" -> "image/png"
        "Memes" -> "image/jpeg"
        else -> null
    }

    /**
     * Tab kab gated dikhe (hide-nothing: tab chhupta nahi, wajah batata hai).
     *
     * Research: TRANSLATE-GIF-FEASIBILITY.md §2.3 — **Commit Content API** mein
     * editor ko MIME accept karna hota hai (`EditorInfo.contentMimeTypes`). Jis
     * field ne opt-in nahi kiya, wahan GIF/sticker jaata hi nahi — Gboard khud
     * yahi toast dikhata hai: *"The text field does not support GIF insertion
     * from the keyboard"*.
     *
     *  - Stickers: **bundled pack** hamesha ready hai (offline tier) — sirf editor
     *    support gate hai
     *  - GIF: editor support + Klipy key (key khali ho to panel ke andar setup
     *    hint dikhta hai, yahan nahi — tab khulta hai)
     *  - Emoji/Favorites/Recents: kabhi gated nahi
     */
    fun tabGateReason(
        tab: String,
        hindi: Boolean,
        editorSupportsImage: Boolean = false,
        editorSupportsVideo: Boolean = false,
    ): String? {
        val mime = tabMime(tab) ?: return null
        val supported = if (mime.startsWith("video/")) editorSupportsVideo else editorSupportsImage
        return if (supported) null
        else com.mgboard.keyboard.media.MediaAvailability.EditorUnsupported(mime).reason(hindi)
    }
}
