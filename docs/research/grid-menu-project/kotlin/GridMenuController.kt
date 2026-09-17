package com.mgboard.keyboard.gridmenu

import android.content.SharedPreferences
import android.content.res.Configuration

/**
 * MgBoard — Grid Menu Controller.
 *
 * Gboard-exact rules (grid-menu-project/FUNCTIONS-AND-LOGIC.md):
 *  - §0.1  capacity: 5 portrait / 6 landscape, valid range 3–8 inclusive
 *  - §0.2  grid icon FIXED (removable = false)
 *  - §0.5  overflow: jo toolbar par fit na ho → grid menu
 *  - §2.1  item click sequence: GATE CHECK → PANEL SWITCH → HEADER UPDATE → STATE SAVE
 *  - §2.3  har failure par Gboard ka exact disabled/error string
 */
class GridMenuController(
    private val prefs: SharedPreferences,
    private val context: GridMenuContext,
    private val panelHost: GridMenuPanelHost,
) {

    /** Runtime context jo gates evaluate karne ke liye chahiye. */
    interface GridMenuContext {
        val orientation: Int                       // Configuration.ORIENTATION_*
        val isUnfolded: Boolean                    // foldable gate
        val isTablet: Boolean
        val isIncognito: Boolean                   // "Voice typing is disabled in Incognito Mode"
        val isWorkProfile: Boolean                 // work-profile gate
        val hasEditorText: Boolean                 // "Enter some text to use writing tools"
        val editorFieldClass: String               // "normal" | "password" | "url" | …
        val keyboardSupportsResize: Boolean        // "Current keyboard does not support resizing"
        val gboardLanguages: List<String>          // enabled keyboard languages
        val deviceLanguage: String
        fun hasPermission(permission: String): Boolean
        fun isModelReady(modelId: String): Boolean
        fun supportsFeatureInLanguage(featureId: String): Boolean
        fun getString(res: Int): String
    }

    // ─────────────────────────── CONFIG (Gboard-exact) ───────────────────────────

    companion object {
        const val KEY_ORDER = "access_points_showing_order"      // 🟢 semicolon-separated
        const val KEY_COUNT = "access_points_count_on_bar"       // 🟢 3..8
        const val KEY_REMAINED = "remained_access_points_on_bar"
        const val KEY_MORE = "more_access_points"

        const val DEFAULT_COUNT_PORTRAIT = 5                     // 🟢
        const val DEFAULT_COUNT_LANDSCAPE = 6                    // 🟢
        const val MIN_COUNT = 3                                  // 🟢
        const val MAX_COUNT = 8                                  // 🟢
        private const val SEPARATOR = ";"                        // 🟢 "separated by semicolon"

        // Gboard-exact disabled / error strings (§2.3)
        const val ERR_NOT_AVAILABLE_IN_APP = "Command not available in this app"
        const val ERR_TOOL_UNAVAILABLE = "Can't use this tool at the moment. Please try again later."
        const val ERR_MIC_IN_USE = "Can't start. Microphone in use."
        const val ERR_EMPTY_EDITOR = "Disabled because empty editor"
        const val ERR_NO_TEXT_SELECTED = "Disabled because no text selected"
        const val ERR_OPT_IN_DISABLED = "Disabled because opt-in is disabled"
        const val ERR_NO_ROOM = "Disabled because there is no enough room for the dialog."
        const val ERR_NO_RESIZE = "Current keyboard does not support resizing"
        const val ERR_OFFLINE = "Can't connect. Retry offline."
        const val ERR_GENERIC = "Something went wrong. Please try again."
    }

    enum class ItemState { ENABLED, DISABLED, HIDDEN }

    data class ResolvedItem(
        val item: GridMenuItem,
        val state: ItemState,
        val disabledReason: String? = null,
    )

    // ─────────────────────────── ORDER PERSISTENCE ───────────────────────────

    /** "Define the order of displayed access point icons, separated by semicolon" */
    fun loadOrder(): List<GridMenuItem> =
        prefs.getString(KEY_ORDER, null)
            ?.split(SEPARATOR)
            ?.mapNotNull { GridMenuItem.fromId(it.trim()) }
            ?.filter { it.removable }
            ?: defaultOrder()

    fun saveOrder(order: List<GridMenuItem>) {
        prefs.edit()
            .putString(KEY_ORDER, order.joinToString(SEPARATOR) { it.id })
            .putString(KEY_MORE, overflowIds(order).joinToString(SEPARATOR))
            .apply()
    }

    /** Gboard default: Tier 1 pehle, phir tier 2/3/4. Grid icon last (fixed). */
    private fun defaultOrder(): List<GridMenuItem> =
        GridMenuItem.DRAGGABLE.sortedWith(compareBy({ it.tier }, { it.ordinal }))

    /** "Icons count on suggestions strip" — clamp to Gboard's 3..8. */
    fun capacity(): Int {
        val default =
            if (context.orientation == Configuration.ORIENTATION_LANDSCAPE) DEFAULT_COUNT_LANDSCAPE
            else DEFAULT_COUNT_PORTRAIT
        return prefs.getInt(KEY_COUNT, default).coerceIn(MIN_COUNT, MAX_COUNT)
    }

    fun setCapacity(count: Int) {
        prefs.edit().putInt(KEY_COUNT, count.coerceIn(MIN_COUNT, MAX_COUNT)).apply()
    }

    // ─────────────────────────── VISIBILITY SPLIT (§0.5) ───────────────────────────

    /** Toolbar par dikhne wale access points. */
    fun visibleToolbarItems(): List<GridMenuItem> {
        val order = loadOrder()
        val cap = capacity()
        return (order.take(cap) + GridMenuItem.GRID_ICON).distinct()
    }

    /** Grid menu ke andar ke items = overflow + sabhi enabled-but-not-on-toolbar. */
    fun overflowItems(): List<GridMenuItem> = overflowIds(loadOrder()).mapNotNull(GridMenuItem::fromId)

    private fun overflowIds(order: List<GridMenuItem>): List<String> {
        val cap = capacity()
        val onBar = order.take(cap).map { it.id }.toSet()
        return GridMenuItem.DRAGGABLE
            .filter { it.id !in onBar }
            .sortedWith(compareBy({ it.tier }, { it.ordinal }))
            .map { it.id }
    }

    /** Poora grid menu (resolved states ke saath). */
    fun buildMenu(): List<ResolvedItem> =
        GridMenuItem.DRAGGABLE
            .sortedWith(compareBy({ it.tier }, { it.ordinal }))
            .map { resolve(it) }
            .filter { it.state != ItemState.HIDDEN }

    // ─────────────────────────── GATE EVALUATION (§2.1 step 1) ───────────────────────────

    fun resolve(item: GridMenuItem): ResolvedItem {
        // 1) feature-menu visibility flag ("Whether to show the X icon in the feature menu.")
        item.prefKey?.let { key ->
            if (key.startsWith("feature_menu_show_") && !prefs.getBoolean(key, true)) {
                return ResolvedItem(item, ItemState.HIDDEN)
            }
        }

        // 2) gate
        val reason = gateFailureReason(item) ?: return ResolvedItem(item, ItemState.ENABLED)
        return ResolvedItem(item, ItemState.DISABLED, reason)
    }

    private fun gateFailureReason(item: GridMenuItem): String? = when (val g = item.gate) {
        Gate.Always -> null

        is Gate.Permission ->
            if (context.hasPermission(g.permission)) null
            else context.getString(g.deniedMessageRes)

        Gate.LanguageSupported ->
            if (context.gboardLanguages.any { context.supportsFeatureInLanguage(item.id) }) null
            else "Advanced features aren't available in your current Gboard language."

        Gate.DeviceLanguageMatch ->
            if (context.deviceLanguage in context.gboardLanguages) null
            else "Not available in your current device language. " +
                 "Change your device language to match your Gboard language to use this."

        is Gate.InputField ->
            if (context.editorFieldClass in g.allowedFieldClasses) null
            else context.getString(g.deniedMessageRes)

        is Gate.DeviceState ->
            if (g.requiredState != "unfolded" || context.isUnfolded) null
            else "Only available in unfolded device state."

        Gate.PersonalProfileOnly ->
            if (!context.isWorkProfile) null
            else "Advanced features are currently unavailable for apps in a work profile. " +
                 "Switch to your personal apps to use this."

        Gate.NotInIncognito ->
            if (!context.isIncognito) null else "Voice typing is disabled in Incognito Mode"

        Gate.NeedsEditorText ->
            if (context.hasEditorText) null else ERR_EMPTY_EDITOR

        is Gate.ModelDownloaded ->
            if (context.isModelReady(g.modelId)) null
            else "Advanced features will be available once the language download is complete"

        is Gate.ToggleEnabled ->
            if (prefs.getBoolean(g.prefKey, false)) null else ERR_OPT_IN_DISABLED

        Gate.KeyboardSupportsResize ->
            if (context.keyboardSupportsResize) null else ERR_NO_RESIZE
    }

    // ─────────────────────────── CLICK HANDLING (§2.1) ───────────────────────────

    /**
     * Gboard sequence:
     * 1. GATE CHECK  2. PANEL SWITCH / MODE SWITCH  3. HEADER UPDATE  4. STATE SAVE
     * @return user ko dikhane wala error message, ya null agar success.
     */
    fun onItemClick(item: GridMenuItem): String? {
        // 1) gate
        gateFailureReason(item)?.let { return it }

        // 2) action
        return try {
            when (item.action) {
                GridAction.OPEN_PANEL -> {
                    panelHost.savePreviousState()                     // "Back to previous state"
                    panelHost.openPanel(requireNotNull(item.panel), item)
                    null
                }
                GridAction.SWITCH_KEYBOARD_MODE -> {
                    panelHost.savePreviousState()
                    panelHost.switchMode(requireNotNull(item.mode))
                    null
                }
                GridAction.OPEN_SUBMENU -> {
                    panelHost.savePreviousState()
                    panelHost.openSubmenu(item)
                    null
                }
                GridAction.TOGGLE_PREFERENCE -> {
                    item.prefKey?.let { k ->
                        prefs.edit().putBoolean(k, !prefs.getBoolean(k, false)).apply()
                    }
                    null
                }
                GridAction.LAUNCH_ACTIVITY,
                GridAction.START_SERVICE,
                GridAction.EDIT_ACTION -> {
                    // Host app (MgBoard IME) inko handle karta hai — yahan sirf dispatch.
                    panelHost.dispatchExternalAction(item)
                    null
                }
                GridAction.NONE -> ERR_NOT_AVAILABLE_IN_APP
            }
        } catch (t: Throwable) {
            ERR_GENERIC                                              // "Something went wrong…"
        }
    }

    /** Grid icon (⊞) ka open/close — "More features opened/closed". */
    fun isMenuOpen(): Boolean = panelHost.isMenuOpen()

    fun toggleMenu() {
        if (panelHost.isMenuOpen()) panelHost.closeMenu() else panelHost.openMenu()
    }

    /** Toolbar/grid ko dobara build karo (order ya capacity change hone par). */
    fun onOrderChangedRebuild() = panelHost.requestToolbarRebuild()

    /** Orientation change par capacity badalti hai → toolbar/grid dobara split karo. */
    fun onOrientationChanged() {
        saveOrder(loadOrder())          // overflow list recompute
        panelHost.requestToolbarRebuild()
    }

    /**
     * Mutual exclusion (§2.4): ek item ON hone par uske rivals OFF + unke side-effects.
     * Gboard: "Access emoji key with a dedicated key. This hides the language switch key."
     */
    fun applyMutualExclusion(item: GridMenuItem, newValue: Boolean) {
        if (!newValue) return
        item.mutuallyExclusiveWith.forEach { rivalId ->
            prefs.edit().putBoolean(rivalId, false).apply()
        }
    }
}
