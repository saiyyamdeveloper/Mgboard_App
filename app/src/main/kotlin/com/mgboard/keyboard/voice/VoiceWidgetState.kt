package com.mgboard.keyboard.voice

/**
 * Voice widget (Gboard ka "Assistant voice typing toolbar") ka **pure-Kotlin** state model.
 *
 * Is file mein koi Android import nahi hai — state machine, transitions, drag-flip
 * rules aur symbols grid sab plain JVM par unit-test hote hain.
 *
 * Naming note: Gboard ise "pill" nahi kehta — APK strings mein yeh
 * **voice toolbar / widget** hai (`widget_x_position`, `widget_change_widget_orientation`,
 * `VOICE_enable_vertical_widget`). Source: docs/research/voice-pill-project/RESEARCH.md §0.
 *
 * Window strategy (owner-approved): **(A) IME-window widget** — Gboard-exact, zero
 * permission. Gboard ke paas `SYSTEM_ALERT_WINDOW` hai hi nahi; pill wahi IME window
 * hai collapsed state mein, aur baaki screen par tap `onComputeInsets()` ke
 * touchable-region se pass-through hota hai.
 */

/** Mic / recognition ki haalat. */
enum class MicState { LISTENING, PAUSED, ERROR, OFFLINE }

/** Widget ka orientation. */
enum class WidgetOrientation { HORIZONTAL, VERTICAL }

/** Kahan dock hai — Gboard: "shrinks the keyboard into a floating pill that you can
 *  dock at the bottom or left/right of your screen". */
enum class Dock { BOTTOM, LEFT, RIGHT, FLOATING }

/** 5 states — RESEARCH.md §4 ka exact mapping. */
sealed interface VoiceWidgetState {

    /** Widget band; normal keyboard (ya kuch nahi). */
    data object Hidden : VoiceWidgetState

    /**
     * State 1 — Full Voice Panel: mic dabane par khulta hai. Keyboard **neeche bana
     * rehta hai** (Gboard-exact), upar voice strip + status + chevron.
     */
    data class FullVoicePanel(
        val mic: MicState = MicState.LISTENING,
        val status: StatusText = StatusText.SPEAK_NOW,
    ) : VoiceWidgetState

    /** State 2 — Horizontal Pill (asli "voice toolbar"). Chevron iska ekmatra trigger hai. */
    data class HorizontalPill(
        val mic: MicState = MicState.LISTENING,
        val status: StatusText = StatusText.SPEAK_NOW,
        val dockedTo: Dock = Dock.BOTTOM,
        val x: Float = 0f,
        val y: Float = 0f,
    ) : VoiceWidgetState

    /** State 3 — Menu Open. Gboard mein menu khulte hi recognition **auto-pause** ho jaata hai. */
    data class MenuOpen(val over: VoiceWidgetState) : VoiceWidgetState

    /** State 4 — Symbols Overlay (header + X + grid + category tabs). */
    data class SymbolsOverlay(val category: SymbolCategory = SymbolCategory.RECENT) : VoiceWidgetState

    /** State 5 — Vertical Pill (left/right edge par dock). */
    data class VerticalPill(
        val mic: MicState = MicState.LISTENING,
        val dockedTo: Dock = Dock.RIGHT,
        val x: Float = 0f,
        val y: Float = 0f,
    ) : VoiceWidgetState
}

/**
 * Status strings — Gboard APK se **verbatim** (RESEARCH.md §8.2/§8.3).
 * Owner decision: `"अब बोलें"` (Gboard-exact), `"बोल रहा हूं"` nahi.
 */
enum class StatusText(val en: String, val hi: String) {
    SPEAK_NOW("Speak now", "अब बोलें"),
    PAUSED("Paused", "रोकी गई"),
    LISTENING("Listening…", "सुन रहा है…"),
    JUST_SPEAK("Just speak naturally", "बस स्वाभाविक रूप से बोलें"),
    ERROR_UNSUPPORTED("Unsupported voice command", "असमर्थित वॉइस कमांड"),
    NO_TEXT_FIELD("Go to a text field to use dictation", "बोली को लिखाई में बदलने की सुविधा इस्तेमाल करने के लिए किसी टेक्स्ट फ़ील्ड पर जाएं"),
    ;

    fun label(hindi: Boolean): String = if (hindi) hi else en
}

/**
 * Symbols overlay ki categories — Gboard APK se exact:
 * `Recent · Numbers · Brackets · Arrows · Mathematics · List` (RESEARCH.md State 4).
 */
enum class SymbolCategory(val en: String, val hi: String) {
    RECENT("Recent", "हाल के"),
    NUMBERS("Numbers", "नंबर"),
    BRACKETS("Brackets", "कोष्ठक"),
    ARROWS("Arrows", "तीर"),
    MATHEMATICS("Mathematics", "गणित"),
    LIST("List", "सूची"),
    ;

    fun label(hindi: Boolean): String = if (hindi) hi else en
}

/** Menu items — owner decision: Gboard ka **poora** menu (7 items + language list). */
enum class VoiceMenuItem(val en: String, val hi: String, val actionId: String) {
    SETTINGS("Settings", "सेटिंग", "settings"),
    SHOW_VOICE_COMMANDS("Show voice commands", "वॉइस कमांड दिखाएं", "voice_commands"),
    SHOW_CLIPBOARD("Show clipboard", "क्लिपबोर्ड दिखाएं", "clipboard"),
    SHOW_TRANSLATE("Show translate", "अनुवाद दिखाएं", "translate"),
    SHOW_EMOJI("Show emoji", "इमोजी दिखाएं", "emoji"),
    SWITCH_VERTICAL("Switch to vertical toolbar", "वर्टिकल टूलबार पर स्विच करें", "widget_change_widget_orientation"),
    SWITCH_HORIZONTAL("Switch to horizontal toolbar", "क्षैतिज टूलबार पर स्विच करें", "widget_change_widget_orientation"),
    FEEDBACK("Feedback", "फ़ीडबैक", "feedback"),
    SYMBOLS("Symbols", "चिह्न", "symbols"),
    ;

    fun label(hindi: Boolean): String = if (hindi) hi else en
}

/** Symbols grid — Gboard ki category names, MgBoard ke apne glyph sets. */
object VoiceSymbols {

    /**
     * Default number symbols. IME ise `Numbers.ROWS[0]` ke **Gondi digits**
     * (U+11D50–U+11D59) se replace karta hai — supplementary code point Kotlin ke
     * `\uXXXX` escape se nahi likha ja sakta, isliye yahan literal nahi hai.
     */
    val NUMBERS: List<String> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    val BRACKETS: List<String> = listOf("(", ")", "{", "}", "[", "]", "<", ">", "«", "»", "〈", "〉")

    val ARROWS: List<String> = listOf("←", "↑", "→", "↓", "↔", "↕", "↖", "↗", "↘", "↙", "⇄", "⇅")

    val MATHEMATICS: List<String> = listOf(
        "+", "-", "×", "÷", "=", "≠", "≈", "<", ">", "≤", "≥", "±",
        "∑", "∞", "√", "∫", "%", "°", "π", "Δ",
    )

    val LIST: List<String> = listOf("•", "◦", "‣", "⁃", "✓", "☑", "☐", "1.", "a.", "i.", "—", "–")

    fun grid(category: SymbolCategory, gondiDigits: List<String>, recent: List<String>): List<String> =
        when (category) {
            SymbolCategory.RECENT -> recent.ifEmpty { NUMBERS.take(10) }
            SymbolCategory.NUMBERS -> gondiDigits.ifEmpty { NUMBERS }
            SymbolCategory.BRACKETS -> BRACKETS
            SymbolCategory.ARROWS -> ARROWS
            SymbolCategory.MATHEMATICS -> MATHEMATICS
            SymbolCategory.LIST -> LIST
        }
}

/**
 * Menu ke divider ke baad wali language list — Gboard ka `<भाषा> (<देश>)` pattern.
 * Hardcoded recognizer list nahi: yeh MgBoard ke enabled keyboard modes se banti hai
 * (Gondi/हिंदी = hi-IN, English = en-IN). Research: RESEARCH.md §8.4.
 */
val VOICE_LANGUAGES = listOf("हिन्दी (भारत)", "English (India)", "हिन्दी", "English (US)")

/** Language label → BCP-47 recognition tag. */
fun voiceLanguageTag(label: String): String = when {
    label.startsWith("हिन्दी") -> "hi-IN"
    label.contains("US") -> "en-US"
    else -> "en-IN"
}
