package com.mgboard.keyboard.grid

/**
 * Web ke inline SVG icons ka Android equivalent (text glyph).
 *
 * Generated [GridMenu.TILES] ka **har** id yahan hona chahiye — `requireIcons()`
 * unit test mein assert hota hai, taaki naya tile aane par icon add karna na bhoole.
 */
object GridIcons {
    private val MAP: Map<String, String> = mapOf(
        "undo" to "↶",
        "redo" to "↷",
        "clipboard" to "⎘",
        "textEdit" to "✎",
        "emoji" to "☺",
        "nextLang" to "⊕",
        "theme" to "◐",
        "settings" to "⚙",
        "shareApp" to "↗",
        "symbols" to "?123",
        "oneHanded" to "◧",
        "personalDict" to "⊞",
        "translate" to "अ",
        "writingTools" to "✒",
        "proofread" to "✓",
        "gif" to "GIF",
        "sticker" to "⌗",
        "emojiKitchen" to "⚭",
        "quickInsert" to "+",
        "scanText" to "▣",
        "mic" to "◉",
    )

    fun of(tileId: String): String = MAP[tileId] ?: "•"

    /** Test hook: koi tile bina icon ke na reh jaye. */
    fun requireIcons(): List<String> = GridMenu.TILES.map { it.id }.filter { !MAP.containsKey(it) }
}
