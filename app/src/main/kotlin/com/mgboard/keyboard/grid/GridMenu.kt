package com.mgboard.keyboard.grid

/* ============================================================================
 *  GridMenu.kt  —  GENERATED FILE, DO NOT EDIT BY HAND
 *
 *  Source of truth : Mgboard_Web/index.html  (EXT_ITEMS + capacity constants)
 *  Generator       : Mgboard/scripts/gen_grid_menu.py
 *
 *  Gboard parity: tile ids/labels, enabled vs gated, aur gateReason strings
 *  (Gboard ke verbatim) web se 1:1 aate hain. Capacity rules bhi web ke same:
 *  portrait 5 / landscape 6, user override 3–8.
 * ========================================================================== */

/**
 * Grid menu ka ek access point (tile).
 *
 * @param enabled false matlab tile dikhta hai par gated hai — tap par
 *                [gateReason] toast hota hai (hide-nothing principle).
 */
data class GridTile(
    val id: String,
    val label: String,
    val labelHi: String? = null,
    val enabled: Boolean = true,
    val gateReason: String? = null,
)

object GridMenu {

    /** Web ke `EXT_ITEMS` — same order. `mic` fixed slot hai (draggable nahi). */
    val TILES: List<GridTile> = listOf(
        GridTile("undo", "Undo", enabled = true),
        GridTile("redo", "Redo", labelHi = "फिर से करें", enabled = true),
        GridTile("clipboard", "Clipboard", enabled = true),
        GridTile("textEdit", "Text editing", enabled = true),
        GridTile("emoji", "Emoji", enabled = true),
        GridTile("nextLang", "Next language", enabled = true),
        GridTile("theme", "Theme", enabled = true),
        GridTile("settings", "Settings", enabled = true),
        GridTile("shareApp", "Share Mgboard", enabled = true),
        GridTile("symbols", "Symbols", labelHi = "सिंबल", enabled = true),
        GridTile("oneHanded", "One-handed", labelHi = "एक हाथ वाला", enabled = true),
        GridTile("personalDict", "Personal dictionary", labelHi = "निजी शब्दकोश", enabled = true),
        GridTile("translate", "Translate", labelHi = "अनुवाद", enabled = false, gateReason = "Can't use this tool at the moment. Please try again later."),
        GridTile("writingTools", "Writing Tools", labelHi = "लेखन टूल", enabled = false, gateReason = "Can't use this tool at the moment. Please try again later."),
        GridTile("proofread", "Proofread", labelHi = "प्रूफ़रीड", enabled = false, gateReason = "Can't proofread text in this field"),
        GridTile("gif", "GIF", labelHi = "GIF", enabled = false, gateReason = "Command not available in this app"),
        GridTile("sticker", "Sticker", labelHi = "स्टिकर", enabled = false, gateReason = "Command not available in this app"),
        GridTile("emojiKitchen", "Emoji Kitchen", labelHi = "इमोजी किचन", enabled = false, gateReason = "Command not available in this app"),
        GridTile("quickInsert", "Quick Insert", labelHi = "क्विक इंसर्ट", enabled = false, gateReason = "Disabled because opt-in is disabled"),
        GridTile("scanText", "Scan text", labelHi = "टेक्स्ट स्कैन करें", enabled = false, gateReason = "Gboard needs access to the camera in order to enable Scan text."),
        GridTile("mic", "Voice typing", enabled = true),
    )

    /** Fixed slot — grid mein draggable nahi (Gboard: mic hamesha right par). */
    const val MIC_ID = "mic"

    /** Draggable tiles (mic ke bina). */
    val GRID_TILES: List<GridTile> get() = TILES.filter { it.id != MIC_ID }

    val ENABLED_TILES: List<GridTile> get() = GRID_TILES.filter { it.enabled }
    val GATED_TILES: List<GridTile> get() = GRID_TILES.filter { !it.enabled }

    fun byId(id: String): GridTile? = TILES.firstOrNull { it.id == id }

    // ── capacity (Gboard-exact, web: maxPinnedCapacity/setPinnedCapacity) ────

    /** Gboard: "between 3 and 8, inclusive". */
    const val MIN_PINNED = 3
    const val MAX_PINNED = 8

    /** Web ka `MAX_PINNED` (2023 redesign ke baad Gboard mein 6 slots). */
    const val LANDSCAPE_CAPACITY = 6

    /** Gboard portrait default = 5 (web: else branch). */
    const val PORTRAIT_CAPACITY = 5

    /** Grid popup mein ek page par kitne tiles (web: EXT_PER_PAGE). */
    const val PER_PAGE = 6

    /** Storage keys — web localStorage ke same naam. */
    const val KEY_CAPACITY = "mg_access_points_count_on_bar"
    const val KEY_PINNED = "mg_pinned_ext_ids"

    /**
     * Web `maxPinnedCapacity()`: orientation default, phir stored override,
     * phir clamp. `stored` null/empty/invalid ho to orientation default chalta
     * hai (web ka parseInt-NaN branch).
     */
    fun capacity(landscape: Boolean, stored: Int?): Int {
        val base = when {
            stored != null -> stored
            landscape -> LANDSCAPE_CAPACITY
            else -> PORTRAIT_CAPACITY
        }
        return clamp(base)
    }

    /**
     * Web `setPinnedCapacity(n)`: `parseInt(n,10) || 5` wala falsy-zero bug
     * yahan nahi hai — 0 → MIN_PINNED clamp hona chahiye (web ka [FIX]).
     */
    fun normalizeCapacity(n: Int?): Int = clamp(if (n == null) 5 else n)

    fun clamp(n: Int): Int = n.coerceIn(MIN_PINNED, MAX_PINNED)

    /**
     * Gboard ka order format — "Define the order of displayed access point
     * icons, separated by semicolon" (web: extApplyOrderSemicolon).
     *
     * Rules: mic drop (fixed slot), unknown ids drop, gated ids drop,
     * duplicates drop, capacity tak trim.
     */
    fun parseOrderSemicolon(str: String?, capacity: Int): List<String> {
        if (str == null) return emptyList()
        val enabledIds = ENABLED_TILES.map { it.id }.toSet()
        return str.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { it != MIC_ID && enabledIds.contains(it) }
            .distinct()
            .take(capacity)
    }

    /** Web `extOrderSemicolon()` — pinned ids ko Gboard format mein dedo. */
    fun toOrderSemicolon(ids: List<String>): String = ids.joinToString(";")
}
