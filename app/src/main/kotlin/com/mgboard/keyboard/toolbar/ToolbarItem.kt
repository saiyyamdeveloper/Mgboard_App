package com.mgboard.keyboard.toolbar

/**
 * Toolbar/menu ka ek row item (label + optional summary + action).
 *
 * "More keyboard options" popup aur "Edit menu" (§5.3) isi se bante hain.
 * Labels Gboard ke verbatim strings se aate hain — jahan string nahi mili wahan
 * uska pattern follow kiya gaya hai ("Open X" / "Close X").
 */
data class ToolbarItem(
    val id: String,
    val glyph: String,
    val en: String,
    val hi: String,
    val summaryEn: String = "",
    val summaryHi: String = "",
    val onClick: () -> Unit,
) {
    fun label(hindi: Boolean): String = if (hindi) hi else en
    fun summary(hindi: Boolean): String = if (hindi) summaryHi else summaryEn
}
