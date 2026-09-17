package com.mgboard.keyboard.gridmenu

import com.mgboard.keyboard.R

/**
 * MgBoard — Grid Menu Panel Host.
 *
 * Gboard state machine (FUNCTIONS-AND-LOGIC §2.2) + panel-switch sequence (§2.1):
 *
 *   LETTER ─┬─ SYMBOLS ─────────── "Back to letter keyboard"
 *           ├─ EXPRESSION ──────── "Close the emoji panel"
 *           ├─ CLIPBOARD ───────── .keyboard-clipboard-item.panel.v2
 *           ├─ TRANSLATE ───────── .label.translate.language.panel
 *           ├─ WRITING_TOOLS ───── "Close Writing Tools"
 *           ├─ EDIT / SELECT ───── "Exit select mode"
 *           ├─ HANDWRITING_HALF/FULL ── "Switch to full screen handwriting"
 *           ├─ MORSE ───────────── .keytop…for_morse_keyboard
 *           ├─ ONE_HANDED_LEFT/RIGHT ── "Exit one-handed mode"
 *           ├─ FLOATING ────────── "Exit floating keyboard" / "Drag here to exit floating mode"
 *           └─ SPLIT ───────────── "some keys will be duplicated on both sides"
 */
interface GridMenuPanelHost {

    /** Current mode. */
    fun currentMode(): KeyboardMode

    /** "Back to previous state" ke liye stack. */
    fun savePreviousState()

    fun restorePreviousState()

    /** Panel kholo: header `.keyboard-header-area.panel.v2` + body replace. */
    fun openPanel(panel: PanelId, source: GridMenuItem)

    /** Keyboard mode switch (symbols / handwriting / morse / one-handed / floating / split). */
    fun switchMode(mode: KeyboardMode)

    /** "Open more expressions list" jaisa sub-menu. */
    fun openSubmenu(source: GridMenuItem)

    /** ⊞ grid popup — "More features opened". */
    fun openMenu()

    /** "More features closed". */
    fun closeMenu()

    fun isMenuOpen(): Boolean

    /** Panel ka header title + close button ka label (Gboard "Close the X panel" pattern). */
    fun panelHeaderFor(panel: PanelId): PanelHeader

    /** Settings / voice / camera jaise IME-ke-bahar wale actions. */
    fun dispatchExternalAction(item: GridMenuItem)

    /** Capacity ya order change hone par toolbar dobara banao. */
    fun requestToolbarRebuild()

    /** Keyboard ko LETTER mode par wapas lao ("Back to letter keyboard"). */
    fun backToLetterKeyboard()
}

/** Panel header ka content — Gboard ke theme elements se map. */
data class PanelHeader(
    val titleRes: Int,
    /** Gboard pattern: "Close the emoji panel" / "Close the symbols panel" */
    val closeLabelRes: Int,
    val showBackArrow: Boolean = true,
    /** `.navbar.for-expression-footer` — sirf expression panel par. */
    val showExpressionNav: Boolean = false,
)

/**
 * Default implementation ka skeleton — MgBoard ke existing keyboard-view ke saath wire karna hai.
 * (INTEGRATION.md §3 dekhein.)
 */
class GridMenuPanelHostImpl(
    private val keyboardView: MgKeyboardViewContract,
) : GridMenuPanelHost {

    /** MgBoard ke existing keyboard view ka minimal contract. */
    interface MgKeyboardViewContract {
        fun setHeaderElement(themeElement: String)   // ".keyboard-header-area" vs ".panel.v2"
        fun setBodyElement(themeElement: String)     // ".keyboard-body-area" vs ".for-non-prime-body"
        fun rebuildToolbar()
        fun setMode(modeName: String)
        fun launchSettings()
        fun startVoiceTyping()
        fun startScanText()
        fun openShareSheet()
        fun openPersonalDictionary()
        fun performUndo()
        fun performRedo()
        fun showExpressionNav(show: Boolean)
    }

    private val modeStack = ArrayDeque<KeyboardMode>()
    private var mode: KeyboardMode = KeyboardMode.LETTER
    private var menuOpen = false

    override fun currentMode(): KeyboardMode = mode

    override fun savePreviousState() { modeStack.addLast(mode) }

    override fun restorePreviousState() {
        mode = modeStack.removeLastOrNull() ?: KeyboardMode.LETTER
        applyMode()
    }

    override fun openPanel(panel: PanelId, source: GridMenuItem) {
        // Gboard: header panel-mode mein chala jaata hai
        keyboardView.setHeaderElement("keyboard-header-area.panel.v2")
        keyboardView.showExpressionNav(panel == PanelId.EXPRESSION)  // ".navbar.for-expression-footer"
        mode = when (panel) {
            PanelId.EXPRESSION -> KeyboardMode.EXPRESSION
            PanelId.CLIPBOARD -> KeyboardMode.CLIPBOARD
            PanelId.TRANSLATE -> KeyboardMode.TRANSLATE
            PanelId.WRITING_TOOLS -> KeyboardMode.WRITING_TOOLS
            PanelId.EDIT_MENU -> KeyboardMode.EDIT
            else -> mode
        }
        applyMode()
    }

    override fun switchMode(mode: KeyboardMode) {
        this.mode = mode
        applyMode()
    }

    override fun openSubmenu(source: GridMenuItem) {
        if (source == GridMenuItem.MORE_FEATURES) {
            menuOpen = true                        // "More features opened"
        }
    }

    override fun openMenu() { menuOpen = true }

    override fun closeMenu() { menuOpen = false }  // "More features closed"

    override fun isMenuOpen(): Boolean = menuOpen

    override fun panelHeaderFor(panel: PanelId): PanelHeader = when (panel) {
        PanelId.EXPRESSION -> PanelHeader(R.string.panel_emoji_title, R.string.panel_emoji_close,
            showExpressionNav = true)
        PanelId.CLIPBOARD -> PanelHeader(R.string.panel_clipboard_title, R.string.panel_clipboard_close)
        PanelId.TRANSLATE -> PanelHeader(R.string.panel_translate_title, R.string.panel_translate_close)
        PanelId.WRITING_TOOLS -> PanelHeader(R.string.panel_writing_tools_title, R.string.panel_writing_tools_close)
        PanelId.EDIT_MENU -> PanelHeader(R.string.panel_edit_title, R.string.panel_edit_close)
        PanelId.MORE_EXPRESSIONS -> PanelHeader(R.string.panel_more_expr_title, R.string.panel_more_expr_close)
        PanelId.SYMBOLS, PanelId.SETTINGS_EXTERNAL, PanelId.THEME_PICKER ->
            PanelHeader(R.string.panel_symbols_title, R.string.panel_symbols_close)
    }

    override fun dispatchExternalAction(item: GridMenuItem) {
        when (item) {
            GridMenuItem.SETTINGS, GridMenuItem.PERSONAL_DICT -> keyboardView.launchSettings()
            GridMenuItem.VOICE_TYPING -> keyboardView.startVoiceTyping()
            GridMenuItem.SCAN_TEXT -> keyboardView.startScanText()
            GridMenuItem.SHARE -> keyboardView.openShareSheet()
            GridMenuItem.UNDO -> keyboardView.performUndo()
            GridMenuItem.REDO -> keyboardView.performRedo()
            else -> Unit
        }
    }

    override fun requestToolbarRebuild() = keyboardView.rebuildToolbar()

    override fun backToLetterKeyboard() {
        modeStack.clear()
        mode = KeyboardMode.LETTER
        applyMode()
    }

    private fun applyMode() {
        keyboardView.setHeaderElement(
            if (mode == KeyboardMode.LETTER) "keyboard-header-area" else "keyboard-header-area.panel.v2"
        )
        keyboardView.setBodyElement(
            if (mode == KeyboardMode.SYMBOLS || mode == KeyboardMode.MORSE)
                "keyboard-body-area.for-non-prime-body"      // 🎨 Gboard theme name
            else "keyboard-body-area"
        )
        keyboardView.setMode(mode.name)
    }
}
