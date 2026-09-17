// android-only: R.string resources chahiye — JVM test runner isse skip karta hai
package com.mgboard.keyboard.gridmenu

import com.mgboard.keyboard.R

/**
 * MgBoard — Grid Menu (⊞ "More features") item definitions.
 *
 * Gboard-exact: har item ka label / action / gate / panel Gboard 18.3.1.977415014-beta ke
 * resources.arsc strings se liya gaya hai (verbatim jahan 🟢 marked hai).
 *
 * Naming note: Gboard internally is menu ko "features menu" kehta hai aur icon ki state ko
 * "More features". MgBoard mein class-naam `GridMenu*` hai (owner ka choice), lekin
 * user-facing strings Gboard wale hi rakhe gaye hain.
 */

/** Item tap par kya hota hai. */
enum class GridAction {
    /** Keyboard body ko ek panel se replace karta hai (IME window ke andar). */
    OPEN_PANEL,
    /** Keyboard ka mode/layout badalta hai (symbols, handwriting, morse, one-handed…). */
    SWITCH_KEYBOARD_MODE,
    /** IME ke bahar ki Activity launch karta hai (Gboard Settings). */
    LAUNCH_ACTIVITY,
    /** Ek preference toggle flip karta hai (number row, theme battery-saver…). */
    TOGGLE_PREFERENCE,
    /** Runtime service start karta hai (mic / camera). */
    START_SERVICE,
    /** Text par edit action chalata hai (undo/redo/cut/copy…). */
    EDIT_ACTION,
    /** Ek aur sub-menu kholta hai (more expressions). */
    OPEN_SUBMENU,
    /** Abhi implement nahi — placeholder. */
    NONE,
}

/** Gate: item kab *available* hota hai. Gboard ke 9 gate types (FUNCTIONS-AND-LOGIC §0.3). */
sealed class Gate {
    object Always : Gate()

    /** "Gboard needs access to the microphone in order to enable voice typing." */
    data class Permission(val permission: String, val deniedMessageRes: Int) : Gate()

    /** "Advanced features aren't available in your current Gboard language." */
    object LanguageSupported : Gate()

    /** "Not available in your current device language…" */
    object DeviceLanguageMatch : Gate()

    /** "Gboard settings are not available in this input field" */
    data class InputField(val allowedFieldClasses: Set<String>, val deniedMessageRes: Int) : Gate()

    /** "Only available in unfolded device state." */
    data class DeviceState(val requiredState: String) : Gate()

    /** "Advanced features are currently unavailable for apps in a work profile." */
    object PersonalProfileOnly : Gate()

    /** "Voice typing is disabled in Incognito Mode" */
    object NotInIncognito : Gate()

    /** "Enter some text to use writing tools" */
    object NeedsEditorText : Gate()

    /** "Advanced voice typing features will be available once the language download is complete" */
    data class ModelDownloaded(val modelId: String) : Gate()

    /** Item sirf tab dikhta hai jab parent toggle ON ho. */
    data class ToggleEnabled(val prefKey: String) : Gate()

    /** "Current keyboard does not support resizing" */
    object KeyboardSupportsResize : Gate()
}

/** Panel IDs — Gboard theme names se map kiye gaye (`.keyboard-header-area.panel.v2` etc.). */
enum class PanelId(val themeElement: String) {
    EXPRESSION("navbar.for-expression-footer"),
    SYMBOLS("keyboard-body-area.for-non-prime-body"),
    CLIPBOARD("keyboard-clipboard-item.panel.v2"),
    TRANSLATE("label.translate.language.panel"),
    WRITING_TOOLS("bg.writing-tools-show-more"),
    SETTINGS_EXTERNAL(""),
    THEME_PICKER(""),
    EDIT_MENU(""),
    MORE_EXPRESSIONS(""),
}

/** Keyboard modes — Gboard state machine (FUNCTIONS-AND-LOGIC §2.2). */
enum class KeyboardMode {
    LETTER,          // prime
    SYMBOLS,         // "Symbols mode" → "Back to letter keyboard"
    EXPRESSION,
    CLIPBOARD,
    TRANSLATE,
    WRITING_TOOLS,
    EDIT,
    SELECT,
    HANDWRITING_HALF,
    HANDWRITING_FULL,
    MORSE,
    ONE_HANDED_LEFT,
    ONE_HANDED_RIGHT,
    FLOATING,
    SPLIT,
}

/**
 * Grid menu ke saare items.
 *
 * @param id            Gboard-style snake_case id (preference key ke saath match karta hai)
 * @param labelEn       🟢 verbatim Gboard English label
 * @param labelHi       🟢 verbatim Gboard Hindi label (jahan available)
 * @param tier          RESEARCH.md wala tier (1 = core … 4 = utility)
 * @param action        tap par kya hoga
 * @param gate          availability gate
 * @param panel         OPEN_PANEL ke liye target
 * @param mode          SWITCH_KEYBOARD_MODE ke liye target
 * @param prefKey       TOGGLE_PREFERENCE ke liye SharedPreferences key
 * @param openLabelRes  accessibility: "Open …" / "Close …" pattern (Gboard-exact)
 * @param closeLabelRes accessibility
 * @param removable     false = toolbar se drag-out nahi ho sakta (Gboard: grid icon fixed)
 * @param mutuallyExclusiveWith  ek ON → yeh OFF (Gboard §0.2 / §2.4)
 * @param promoTextRes  promotion banner (Gboard §0.6) — null = koi promo nahi
 * @param shortcutKeyRes physical-keyboard shortcut hint (`.widget-popup-menu-entry-shortcuts-key`)
 */
enum class GridMenuItem(
    val id: String,
    val labelEn: String,
    val labelHi: String,
    val tier: Int,
    val action: GridAction,
    val gate: Gate = Gate.Always,
    val panel: PanelId? = null,
    val mode: KeyboardMode? = null,
    val prefKey: String? = null,
    val openLabelRes: Int = 0,
    val closeLabelRes: Int = 0,
    val removable: Boolean = true,
    val mutuallyExclusiveWith: List<String> = emptyList(),
    val promoTextRes: Int = 0,
    val shortcutKeyRes: Int = 0,
) {

    // ───────────────────────── TIER 1 — CORE ─────────────────────────
    CLIPBOARD(
        id = "clipboard", labelEn = "Clipboard", labelHi = "क्लिपबोर्ड", tier = 1,
        action = GridAction.OPEN_PANEL, panel = PanelId.CLIPBOARD, mode = KeyboardMode.CLIPBOARD,
        promoTextRes = R.string.grid_promo_clipboard,   // "Copy & paste multiple items? Try Clipboard"
    ),
    TRANSLATE(
        id = "translate", labelEn = "Translate", labelHi = "अनुवाद", tier = 1,
        action = GridAction.OPEN_PANEL, panel = PanelId.TRANSLATE, mode = KeyboardMode.TRANSLATE,
    ),
    EMOJI(
        id = "emoji", labelEn = "Emoji", labelHi = "इमोजी", tier = 1,
        action = GridAction.OPEN_PANEL, panel = PanelId.EXPRESSION, mode = KeyboardMode.EXPRESSION,
        // "Access emoji key with a dedicated key. This hides the language switch key."
        mutuallyExclusiveWith = listOf("language_switch_key"),
    ),
    GIF(
        id = "gif", labelEn = "GIF", labelHi = "GIF", tier = 1,
        action = GridAction.OPEN_PANEL, panel = PanelId.EXPRESSION, mode = KeyboardMode.EXPRESSION,
    ),
    STICKER(
        id = "sticker", labelEn = "Sticker", labelHi = "स्टिकर", tier = 1,
        action = GridAction.OPEN_PANEL, panel = PanelId.EXPRESSION, mode = KeyboardMode.EXPRESSION,
    ),
    SETTINGS(
        id = "settings", labelEn = "Settings", labelHi = "सेटिंग", tier = 1,
        action = GridAction.LAUNCH_ACTIVITY,
        gate = Gate.InputField(setOf("normal"), R.string.grid_gate_settings_field),
        // Gboard flag: "Whether to show the Settings icon in the feature menu."
        prefKey = "feature_menu_show_settings",
    ),
    THEME(
        id = "theme", labelEn = "Theme", labelHi = "थीम", tier = 1,
        action = GridAction.OPEN_PANEL, panel = PanelId.THEME_PICKER,
        gate = Gate.InputField(setOf("normal"), R.string.grid_gate_theme_field),
        prefKey = "feature_menu_show_theme",
    ),
    VOICE_TYPING(
        id = "voice_typing",
        labelEn = "Voice typing",
        labelHi = "बोली को लिखाई में बदलने की आसान सुविधा",
        tier = 1,
        action = GridAction.START_SERVICE,
        gate = Gate.Permission("android.permission.RECORD_AUDIO", R.string.grid_gate_mic),
        prefKey = "enable_voice_input",
    ),
    TEXT_EDITING(
        id = "text_editing", labelEn = "Text editing", labelHi = "टेक्स्ट एडिटिंग", tier = 1,
        action = GridAction.OPEN_PANEL, panel = PanelId.EDIT_MENU, mode = KeyboardMode.EDIT,
        gate = Gate.NeedsEditorText,   // "Disabled because empty editor"
    ),

    // ─────────────── TIER 2 — LAYOUT & KEYBOARD MODES ───────────────
    ONE_HANDED(
        id = "one_handed", labelEn = "One-handed", labelHi = "एक हाथ वाला", tier = 2,
        action = GridAction.SWITCH_KEYBOARD_MODE, mode = KeyboardMode.ONE_HANDED_RIGHT,
        prefKey = "keyboard_mode_foldable_one_handed_mode",
        mutuallyExclusiveWith = listOf("split"),
    ),
    FLOATING(
        id = "floating", labelEn = "Floating", labelHi = "फ़्लोटिंग", tier = 2,
        action = GridAction.SWITCH_KEYBOARD_MODE, mode = KeyboardMode.FLOATING,
        prefKey = "floating_keyboard_mode_data",
        mutuallyExclusiveWith = listOf("one_handed", "split"),
    ),
    RESIZE(
        id = "resize", labelEn = "Resize", labelHi = "आकार बदलें", tier = 2,
        action = GridAction.SWITCH_KEYBOARD_MODE, mode = KeyboardMode.FLOATING,
        gate = Gate.KeyboardSupportsResize,   // "Current keyboard does not support resizing"
        prefKey = "keyboard_height_ratio",
    ),
    SPLIT(
        id = "split", labelEn = "Split", labelHi = "स्प्लिट", tier = 2,
        action = GridAction.SWITCH_KEYBOARD_MODE, mode = KeyboardMode.SPLIT,
        prefKey = "layout_9key_split",
        mutuallyExclusiveWith = listOf("one_handed", "floating"),
    ),
    HANDWRITING(
        id = "handwriting", labelEn = "Handwriting", labelHi = "हस्तलेखन", tier = 2,
        action = GridAction.SWITCH_KEYBOARD_MODE, mode = KeyboardMode.HANDWRITING_HALF,
        gate = Gate.ModelDownloaded("stylus_handwriting"),  // "Downloading stylus handwriting model"
        prefKey = "feature_menu_show_handwriting",
    ),
    SYMBOLS(
        id = "symbols", labelEn = "Symbols", labelHi = "सिंबल", tier = 2,
        action = GridAction.SWITCH_KEYBOARD_MODE, mode = KeyboardMode.SYMBOLS,
        openLabelRes = R.string.grid_open_symbols,   // "Open the on-screen symbols keyboard"
    ),
    NUMBER_ROW(
        id = "number_row", labelEn = "Number row", labelHi = "नंबर पंक्ति", tier = 2,
        action = GridAction.TOGGLE_PREFERENCE, prefKey = "enable_number_row",
    ),
    MORSE(
        id = "morse", labelEn = "Morse code", labelHi = "मोर्स कोड", tier = 2,
        action = GridAction.SWITCH_KEYBOARD_MODE, mode = KeyboardMode.MORSE,
        prefKey = "enable_morse_keyboard",
    ),

    // ─────────────── TIER 3 — AI / WRITING TOOLS ───────────────
    WRITING_TOOLS(
        id = "writing_tools", labelEn = "Writing Tools", labelHi = "लेखन टूल", tier = 3,
        action = GridAction.OPEN_PANEL, panel = PanelId.WRITING_TOOLS, mode = KeyboardMode.WRITING_TOOLS,
        gate = Gate.NeedsEditorText,          // "Enter some text to use writing tools"
        prefKey = "pref_key_writing_tools_opt_in",
    ),
    PROOFREAD(
        id = "proofread", labelEn = "Proofread", labelHi = "प्रूफ़रीड", tier = 3,
        action = GridAction.OPEN_PANEL, panel = PanelId.WRITING_TOOLS, mode = KeyboardMode.WRITING_TOOLS,
        gate = Gate.LanguageSupported,        // "Gboard doesn't support proofreading for …"
        prefKey = "pref_key_enable_grammar_checker",
    ),
    REWRITE(
        id = "rewrite", labelEn = "Rewrite", labelHi = "रीराइट", tier = 3,
        action = GridAction.OPEN_PANEL, panel = PanelId.WRITING_TOOLS,
        gate = Gate.NeedsEditorText,
    ),
    QUICK_INSERT(
        id = "quick_insert", labelEn = "Quick Insert", labelHi = "क्विक इंसर्ट", tier = 3,
        action = GridAction.TOGGLE_PREFERENCE,
        gate = Gate.Permission("android.permission.READ_CONTACTS", R.string.grid_gate_contacts),
        prefKey = "enable_quick_insert",
    ),
    EMOJI_KITCHEN(
        id = "emoji_kitchen", labelEn = "Emoji Kitchen", labelHi = "इमोजी किचन", tier = 3,
        action = GridAction.OPEN_PANEL, panel = PanelId.EXPRESSION,
    ),
    MORE_EXPRESSIONS(
        id = "more_expressions", labelEn = "More expressions", labelHi = "और एक्सप्रेशन", tier = 3,
        action = GridAction.OPEN_SUBMENU, panel = PanelId.MORE_EXPRESSIONS,
    ),
    PERSONAL_DICT(
        id = "personal_dictionary", labelEn = "Personal dictionary",
        labelHi = "निजी शब्दकोश", tier = 3,
        action = GridAction.LAUNCH_ACTIVITY,
        prefKey = "pref_key_use_personalized_dicts",
    ),

    // ─────────────── TIER 4 — UTILITY ───────────────
    UNDO(
        id = "undo", labelEn = "Undo", labelHi = "अनडू", tier = 4,
        action = GridAction.EDIT_ACTION,
        prefKey = "enable_undo_redo_via_access_point",
    ),
    REDO(
        id = "redo", labelEn = "Redo", labelHi = "रीडू", tier = 4,
        action = GridAction.EDIT_ACTION,
        prefKey = "enable_undo_redo_via_access_point",
    ),
    SCAN_TEXT(
        id = "scan_text", labelEn = "Scan text", labelHi = "टेक्स्ट स्कैन करें", tier = 4,
        action = GridAction.START_SERVICE,
        gate = Gate.Permission("android.permission.CAMERA", R.string.grid_gate_camera),
    ),
    SHARE(
        id = "share", labelEn = "Share", labelHi = "शेयर करें", tier = 4,
        action = GridAction.LAUNCH_ACTIVITY,
    ),

    /**
     * ⊞ Grid icon khud — toolbar par FIXED, drag se hataya nahi ja sakta.
     * Gboard: "More features opened/closed", "Open/Close features menu".
     */
    MORE_FEATURES(
        id = "more_features", labelEn = "More features", labelHi = "ज़्यादा सुविधाएं", tier = 0,
        action = GridAction.OPEN_SUBMENU,
        removable = false,                    // ← Gboard-exact: fixed position
        openLabelRes = R.string.grid_open_features_menu,
        closeLabelRes = R.string.grid_close_features_menu,
    ),
    ;

    companion object {
        /** Grid icon hamesha last (ya first) — configurable. */
        val GRID_ICON: GridMenuItem = MORE_FEATURES

        /** Customization ke waqt drag kiye jaane wale items (grid icon chhodkar). */
        val DRAGGABLE: List<GridMenuItem> = values().filter { it.removable && it != MORE_FEATURES }

        fun fromId(id: String?): GridMenuItem? = values().firstOrNull { it.id == id }
    }
}
