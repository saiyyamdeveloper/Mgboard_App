package com.mgboard.keyboard.gridmenu

import android.content.SharedPreferences

/**
 * MgBoard — Grid Menu Customizer (drag & drop).
 *
 * Gboard-exact flow (FUNCTIONS-AND-LOGIC §0.4):
 *
 *   1. ⊞ tap                      → "Open features menu"
 *   2. item LONG-PRESS            → "Hold and drag to customize"
 *                                   → customization mode ON
 *                                   → 🎨 .access-point-customized-state-indicator
 *   3. drag:
 *        grid → toolbar           = PROMOTE
 *        toolbar → grid           = DEMOTE
 *        grid ke andar            = REORDER
 *      → "Drag to reorganize, or place in your shortcut top bar"
 *   4. "Finish customizing feature menus" → persist (semicolon-separated)
 *
 * Gboard rules jo yahan enforce hoti hain:
 *   - capacity 5 portrait / 6 landscape, range 3–8 inclusive
 *   - grid icon (MORE_FEATURES) `removable = false` — drag se nahi hataya ja sakta
 *   - education/promotion tooltip CLOSED na ho tab tak "Remove" clickable nahi
 *     ("Remove emoji fast-access row. Not clickable until the introduction tooltip is closed")
 */
class GridMenuCustomizer(
    private val prefs: SharedPreferences,
    private val controller: GridMenuController,
) {

    companion object {
        const val KEY_CUSTOMIZING = "access_point_customizing"
        const val KEY_EDU_SHOWN = "access_point_education_shown"
        const val KEY_EDU_CLOSED = "access_point_education_closed"
        const val KEY_LAST_CHECKED_FEATURE = "customized_order_personalize_last_checked_feature"
        const val KEY_LAST_CHECK_TIME = "customized_order_personalize_last_check_time"

        const val PROMO_UNDO = "undo_access_point_promotion"
        const val PROMO_CLIPBOARD = "clipboard_access_point_promotion"
        const val INTRO_EMOJI_FAST_ROW = "emoji_fast_access_row_introduction"

        // Gboard-exact strings
        const val TXT_HOLD_DRAG_CUSTOMIZE = "Hold and drag to customize"
        const val TXT_DRAG_REORGANIZE =
            "Drag to reorganize, or place in your shortcut top bar"
        const val TXT_FINISH = "Finish customizing feature menus"
        const val TXT_NOT_CLICKABLE_UNTIL_TOOLTIP_CLOSED =
            "Remove emoji fast-access row. Not clickable until the introduction tooltip is closed"
    }

    enum class PromoState { NOT_SHOWN, SHOWING, CLOSED }   // §0.6 state machine

    // ───────────────────────── customization mode ─────────────────────────

    val isCustomizing: Boolean
        get() = prefs.getBoolean(KEY_CUSTOMIZING, false)

    /** Long-press → customization mode ON. */
    fun enterCustomization(): Boolean {
        prefs.edit().putBoolean(KEY_CUSTOMIZING, true).apply()
        controller.panelHost_requestToolbarRebuild()
        return true
    }

    /** "Finish customizing feature menus" → persist + exit. */
    fun finishCustomization(order: List<GridMenuItem>) {
        prefs.edit()
            .putBoolean(KEY_CUSTOMIZING, false)
            .putString(KEY_LAST_CHECKED_FEATURE, order.firstOrNull()?.id ?: "")
            .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
            .apply()
        commitOrder(order)
    }

    fun cancelCustomization() {
        prefs.edit().putBoolean(KEY_CUSTOMIZING, false).apply()
        controller.panelHost_requestToolbarRebuild()
    }

    // ───────────────────────── PROMOTE / DEMOTE / REORDER ─────────────────────────

    /**
     * Grid se toolbar par drop.
     * @return false agar capacity full hai (Gboard: max 5/6) ya item removable nahi.
     */
    fun promote(item: GridMenuItem): Boolean {
        if (!item.removable) return false                      // grid icon fixed
        val order = controller.loadOrder().toMutableList()
        val cap = controller.capacity()

        order.remove(item)                                     // pehle grid se hatao
        if (order.size >= cap) {
            // Gboard behaviour: capacity full ho to last item wapas grid mein chala jaata hai
            val evicted = order.removeAt(order.size - 1)
            order.add(0, item)
            commitOrder(order)
            onEvicted?.invoke(evicted)
            return true
        }
        order.add(0, item)
        commitOrder(order)
        return true
    }

    /** Toolbar se grid mein drop. */
    fun demote(item: GridMenuItem): Boolean {
        if (!item.removable) return false                      // ⊞ hataya nahi ja sakta
        val order = controller.loadOrder().toMutableList()
        val cap = controller.capacity()
        if (!order.take(cap).contains(item)) return false       // toolbar par tha hi nahi
        if (order.take(cap).size <= GridMenuController.MIN_COUNT) return false  // 3 se neeche nahi

        order.remove(item)
        order.add(item)                                        // ab overflow (grid) mein
        commitOrder(order)
        return true
    }

    /** Grid ya toolbar ke andar reorder. */
    fun move(item: GridMenuItem, toIndex: Int): Boolean {
        if (!item.removable) return false
        val order = controller.loadOrder().toMutableList()
        val from = order.indexOf(item)
        if (from < 0) return false
        order.removeAt(from)
        order.add(toIndex.coerceIn(0, order.size), item)
        commitOrder(order)
        return true
    }

    /** Toolbar ke andar swap (drag over existing icon). */
    fun swap(a: GridMenuItem, b: GridMenuItem): Boolean {
        if (!a.removable || !b.removable) return false
        val order = controller.loadOrder().toMutableList()
        val ia = order.indexOf(a); val ib = order.indexOf(b)
        if (ia < 0 || ib < 0) return false
        order[ia] = b; order[ib] = a
        commitOrder(order)
        return true
    }

    private fun commitOrder(order: List<GridMenuItem>) {
        controller.saveOrder(order)
        controller.panelHost_requestToolbarRebuild()
        onOrderChanged?.invoke(order)
    }

    // ───────────────────────── EDUCATION / PROMO (§0.6) ─────────────────────────

    fun promoState(key: String): PromoState = when {
        prefs.getBoolean("$key$KEY_EDU_CLOSED", false) -> PromoState.CLOSED
        prefs.getBoolean("$key$KEY_EDU_SHOWN", false) -> PromoState.SHOWING
        else -> PromoState.NOT_SHOWN
    }

    fun markPromoShown(key: String) {
        prefs.edit().putBoolean("$key$KEY_EDU_SHOWN", true).apply()
    }

    /** "Close undo access point promotion" / "Close emoji fast-access row introduction" */
    fun closePromo(key: String) {
        prefs.edit()
            .putBoolean("$key$KEY_EDU_SHOWN", true)
            .putBoolean("$key$KEY_EDU_CLOSED", true)
            .apply()
    }

    /**
     * Gboard rule: introduction tooltip CLOSED na ho tab tak remove-action clickable NAHI.
     */
    fun isRemoveActionClickable(introKey: String): Boolean =
        promoState(introKey) == PromoState.CLOSED

    /** Promotion banner ka text (agar dikhana ho). */
    fun promoTextFor(item: GridMenuItem): String? = when (item) {
        GridMenuItem.CLIPBOARD ->
            if (promoState(PROMO_CLIPBOARD) != PromoState.CLOSED)
                "Copy & paste multiple items? Try Clipboard" else null
        GridMenuItem.UNDO ->
            if (promoState(PROMO_UNDO) != PromoState.CLOSED) null else null  // close-only
        else -> null
    }

    // ───────────────────────── callbacks ─────────────────────────

    var onOrderChanged: ((List<GridMenuItem>) -> Unit)? = null
    var onEvicted: ((GridMenuItem) -> Unit)? = null
}

/**
 * Controller ka public rebuild hook — customizer isi se toolbar refresh karta hai.
 * (GridMenuController.onOrderChangedRebuild() → panelHost.requestToolbarRebuild())
 */
internal fun GridMenuController.panelHost_requestToolbarRebuild() = onOrderChangedRebuild()
