// android-only: KeyboardModel (ime/) Compose state use karta hai — JVM test runner isse skip karta hai
package com.mgboard.keyboard.ui

import com.mgboard.keyboard.CpText
import com.mgboard.keyboard.engine.TextInput

/**
 * In-memory [TextInput] — app ke andar live keyboard **preview** ke liye.
 *
 * Isse user bina IME enable kiye typing engine (nukta composition, युक्त,
 * backspace, undo) ko test kar sakta hai. Contract wahi hai jo real IME ka
 * [com.mgboard.keyboard.ime.ImeTextInput] follow karta hai, isliye preview aur
 * real keyboard ka behaviour same rehta hai.
 */
class PreviewTextInput(initial: String = "") : TextInput {

    var buffer: String = initial
        private set
    var cursor: Int = CpText.len(initial)
        private set
    var anchor: Int? = null
        private set

    override val text: String get() = buffer
    override val cursorPos: Int? get() = cursor
    override val selAnchor: Int? get() = anchor

    override fun replaceRange(start: Int, end: Int, replacement: String) {
        val cps = CpText.cpsAsStrings(buffer)
        val s = start.coerceIn(0, cps.size)
        val e = end.coerceIn(s, cps.size)
        buffer = cps.take(s).joinToString("") + replacement + cps.drop(e).joinToString("")
        cursor = s + CpText.len(replacement)
        anchor = null
    }

    override fun restoreCursor(cursor: Int?, anchor: Int?) {
        val len = CpText.len(buffer)
        this.cursor = (cursor ?: len).coerceIn(0, len)
        this.anchor = anchor?.coerceIn(0, len)
    }

    fun clear() { buffer = ""; cursor = 0; anchor = null }
}

/**
 * Preview ke liye [com.mgboard.keyboard.ime.KeyboardModel.SettingsSource].
 *
 * Real prefs ki jagah in-memory values, taaki launcher preview mein height /
 * one-handed / theme / toolbar sliders turant asar dikhayein. Defaults wahi hain
 * jo `SgPrefs` ke hain (Gboard-parity).
 */
class PreviewSettingsSource(
    private val onToast: (String) -> Unit = {},
) : com.mgboard.keyboard.ime.KeyboardModel.SettingsSource {

    override var landscape: Boolean = false
    override var storedCapacity: Int? = null
    override var heightRatio: Double = 1.0
    override var oneHanded: String = ""
    override var toolbarVisible: Boolean = true
    override var theme: String = "system"
    override var hapticEnabled: Boolean = true
    override var pinnedIds: List<String> = DEFAULT_PINNED
    override var uiHindi: Boolean = false

    // ── toolbar-project ────────────────────────────────────────────────────────
    override val toolbarFlags = com.mgboard.keyboard.toolbar.ToolbarFlags()
    override val toolbarOrderRaw: String? = null
    override val hasImeAction: Boolean = true
    override val isEditingExistingText: Boolean = false

    private val clips = mutableListOf<String>()
    private val recents = mutableListOf<String>()
    private val recentEmojis = mutableListOf<String>()
    private val favorites = mutableListOf<String>()

    override fun clipboardHistory(): List<String> = clips.toList()
    override fun addClipboardEntry(text: String) {
        clips.remove(text); clips.add(0, text)
        while (clips.size > 50) clips.removeAt(clips.size - 1)
    }
    override fun removeClipboardEntry(text: String) { clips.remove(text) }

    override fun recentSymbols(): List<String> = recents.toList()
    override fun rememberRecentSymbol(sym: String) { recents.remove(sym); recents.add(0, sym) }
    override fun recentEmoji(): List<String> = recentEmojis.toList()
    override fun rememberRecentEmoji(e: String) { recentEmojis.remove(e); recentEmojis.add(0, e) }
    override fun favoriteEmoji(): List<String> = favorites.toList()

    override fun performImeAction() = onToast("IME action (preview)")
    override fun showImePicker() = onToast("System keyboard picker (device par chalta hai)")
    override fun editorSelectAll() = onToast("Select all (preview)")
    override fun editorCopy() = onToast("Copy (preview)")
    override fun editorCut() = onToast("Cut (preview)")
    override fun editorPaste() = onToast("Paste (preview)")
    override fun setToolbarVisible(v: Boolean) { toolbarVisible = v }

    // ── GIF/Stickers (preview: editor support simulate + fake commit) ──────
    var previewEditorSupportsMedia = true
    var lastCommittedMedia: String? = null
    override fun mediaEditorSupports(mime: String): Boolean = previewEditorSupportsMedia
    override fun commitBundledSticker(sticker: com.mgboard.keyboard.media.BundledSticker): Boolean {
        lastCommittedMedia = sticker.id
        onToast("Sticker sent: " + sticker.title)
        return previewEditorSupportsMedia
    }
    override fun commitRemoteMedia(item: com.mgboard.keyboard.media.MediaItem): Boolean {
        lastCommittedMedia = item.slug
        onToast("GIF sent: " + item.title)
        return previewEditorSupportsMedia
    }
    override fun klipyAppKey(): String = ""       // preview mein bundled pack hi live
    override fun klipyFetch(url: String, onResult: (com.mgboard.keyboard.media.MediaPage?) -> Unit) {
        onResult(null)
    }

    // ── translate (preview ke liye fake engine — device/ML Kit ke bina panel chale) ──
    override fun translateEngine(): com.mgboard.keyboard.translate.TranslateEngine = PreviewTranslateEngine
    override val translateWifiOnly: Boolean get() = true
    override fun translateSrcCode(): String? = null
    override fun translateTgtCode(): String? = null
    override fun saveTranslateLanguages(src: String, tgt: String) {}

    /** Preview mein debounce ke liye turant chalane wala timer (UI test aasaan). */
    private val timers = HashMap<Long, () -> Unit>()
    private var timerToken = 0L
    override fun scheduleTimer(delayMs: Long, runnable: () -> Unit): Long {
        val t = ++timerToken
        timers[t] = runnable
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            timers.remove(t)?.invoke()
        }, delayMs)
        return t
    }
    override fun cancelTimer(token: Long) { timers.remove(token) }

    override fun setPinnedIds(ids: List<String>) { pinnedIds = ids }

    override fun cycleOneHanded(): String {
        oneHanded = when (oneHanded) { "" -> "right"; "right" -> "left"; else -> "" }
        return oneHanded
    }

    override fun setOneHanded(mode: String) { oneHanded = mode }

    override fun toast(message: String) = onToast(message)

    override fun onSettingsChanged() = onToast("Settings (preview mein band)")

    companion object {
        /** Web ke default pinned ids (Gboard-parity; grid-menu-project §0.1). */
        val DEFAULT_PINNED = listOf("clipboard", "textEdit", "emoji", "theme", "symbols")
    }
}

/**
 * Launcher-preview ke liye voice host — IME wale `ImeVoiceHost` jaisa hi, par
 * dictation ki jagah honest gating: bina mic permission/recognition ke status
 * text dikhta hai aur transcript simulate nahi hota (fake data se parity ka
 * jhootha bharosa nahi).
 *
 * Isse preview mein poora voice-toolbar UI (5 states, drag, menu, symbols) test
 * ho jaata hai — bina IME enable kiye.
 */
class PreviewVoiceHost(
    private val model: com.mgboard.keyboard.ime.KeyboardModel,
    private val input: PreviewTextInput,
) : com.mgboard.keyboard.voice.VoiceWidgetController.VoiceHost {

    override var screenWidthDp: Float = 360f
    override var screenHeightDp: Float = 740f
    override val hindiUi: Boolean get() = model.settings.uiHindi

    private var x: Float? = null
    private var y: Float? = null
    private var orientation = com.mgboard.keyboard.voice.WidgetOrientation.HORIZONTAL
    private var modeEnabled = false
    private var tooltipSeen = false
    /** "Force the toolbar in horizontal mode and disable dragging" (Gboard debug flag). */
    var dragLocked = false
    private val recent = mutableListOf<String>()
    var recognitionLanguage = "hi-IN"

    override fun loadX() = x
    override fun loadY() = y
    override fun savePosition(nx: Float, ny: Float) { x = nx; y = ny }
    override fun loadOrientation() = orientation
    override fun saveOrientation(o: com.mgboard.keyboard.voice.WidgetOrientation) { orientation = o }
    override fun loadModeEnabled() = modeEnabled
    override fun saveModeEnabled(v: Boolean) { modeEnabled = v }
    override fun isDragLocked() = dragLocked

    /** Position/orientation/mode ko defaults par wapas le jao. */
    fun resetPosition() {
        x = null; y = null
        orientation = com.mgboard.keyboard.voice.WidgetOrientation.HORIZONTAL
        recent.clear()
    }
    override fun loadRecentSymbols(): List<String> = recent.toList()
    override fun pushRecentSymbol(s: String) {
        recent.add(0, s)
        while (recent.size > 24) recent.removeAt(recent.size - 1)
    }
    override fun loadTooltipSeen() = tooltipSeen
    override fun saveTooltipSeen() { tooltipSeen = true }

    override fun languages() = com.mgboard.keyboard.voice.VOICE_LANGUAGES
    override fun setRecognitionLanguage(language: String) {
        recognitionLanguage = com.mgboard.keyboard.voice.voiceLanguageTag(language)
    }
    override fun gondiDigits(): List<String> = com.mgboard.keyboard.data.Numbers.ROWS[0].map { it.g }

    override fun isDictationAvailable() = false
    override fun startDictation(preferOffline: Boolean) { onToast("Preview mein dictation band hai — device par mic chalega") }
    override fun stopDictation() {}
    override fun releaseDictation() {}

    override fun insertText(text: String) = model.insertBulk(text)
    override fun backspaceOnce() = model.engine.backspace()
    override fun hasTextField() = true

    override fun toast(message: String) = onToast(message)
    override fun openSettings() = onToast("Settings (preview mein band)")
    override fun openFullKeyboard() {}
    override fun requestPermission(recordAudio: Boolean) =
        onToast("Microphone permission is needed for voice typing")
    override fun onChanged() {}

    var onToast: (String) -> Unit = {}
}

/**
 * Preview/launcher ke liye fake translate engine — ML Kit device par hi chalta hai,
 * par panel ka UI (pickers, ⇄ swap, progress, ✓ insert, gated reasons) bina device
 * ke verify karna zaroori hai. Chhota built-in dictionary + simulated download.
 */
private object PreviewTranslateEngine : com.mgboard.keyboard.translate.TranslateEngine {
    private val ready = mutableSetOf<Pair<String, String>>()
    private val dict = mapOf(
        "hello" to "\u0928\u092E\u0938\u094D\u0924\u0947",
        "how are you" to "\u0906\u092A \u0915\u0948\u0938\u0947 \u0939\u0948\u0902",
        "good morning" to "\u0938\u0941\u092A\u094D\u0930\u092D\u093E\u0924",
        "thank you" to "\u0927\u0928\u094D\u092F\u0935\u093E\u0926",
        "\u0928\u092E\u0938\u094D\u0924\u0947" to "hello",
        "\u0906\u092A \u0915\u0948\u0938\u0947 \u0939\u0948\u0902" to "how are you",
        "\u0927\u0928\u094D\u092F\u0935\u093E\u0926" to "thank you",
    )

    override fun isModelReady(src: com.mgboard.keyboard.translate.TranslateLang,
                             tgt: com.mgboard.keyboard.translate.TranslateLang) =
        (src.code to tgt.code) in ready

    override fun downloadModel(src: com.mgboard.keyboard.translate.TranslateLang,
                               tgt: com.mgboard.keyboard.translate.TranslateLang,
                               onProgress: (Float) -> Unit,
                               onDownloaded: (Boolean) -> Unit) {
        // simulated progress — UI ke LinearProgressIndicator ko test karne ke liye
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        var p = 0f
        fun step() {
            p += 0.25f
            onProgress(p.coerceAtMost(1f))
            if (p < 1f) h.postDelayed({ step() }, 220)
            else {
                ready += src.code to tgt.code
                ready += tgt.code to src.code
                onDownloaded(true)
            }
        }
        h.postDelayed({ step() }, 220)
    }

    override fun translate(src: com.mgboard.keyboard.translate.TranslateLang,
                           tgt: com.mgboard.keyboard.translate.TranslateLang,
                           text: String, onResult: (String?) -> Unit) {
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val key = text.trim().lowercase()
        val out = dict[key]
            ?: if (tgt == com.mgboard.keyboard.translate.TranslateLang.HINDI)
                "[" + src.code + "\u2192" + tgt.code + "] " + text
               else "[preview translation] " + text
        h.postDelayed({ onResult(out) }, 260)
    }

    override fun close() {}
}
