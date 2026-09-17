package com.mgboard.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mgboard.keyboard.CpText
import com.mgboard.keyboard.data.Numbers
import com.mgboard.keyboard.engine.TypingEngine
import com.mgboard.keyboard.ime.KeyboardModel

/**
 * [VoiceWidgetController.VoiceHost] ka real implementation.
 *
 * Standing rule: yeh module **additive** hai — keyboard ka key-event pipeline,
 * converter, nukta rules aur layout kuch nahi badla. Text insert karne ke liye wahi
 * `KeyboardModel.insertBulk()` use hota hai (jo Gondi mode mein `DevToGondi.convert()`
 * chalata hai), aur backspace ke liye wahi `TypingEngine.backspace()` — timing
 * (400ms/70ms) UI layer ke `KeyGestures` se aati hai, dobara implement nahi ki gayi.
 */
class ImeVoiceHost(
    private val context: Context,
    private val model: KeyboardModel,
    private val engine: TypingEngine,
    private val store: WidgetPositionStore,
    private val openSettingsScreen: () -> Unit,
    private val onChanged: () -> Unit,
) : VoiceWidgetController.VoiceHost {

    var screenWidthDpOverride: Float? = null
    var screenHeightDpOverride: Float? = null
    var hasTextFieldOverride: Boolean? = null

    val bridge: DictationBridge by lazy {
        DictationBridge(
            context = context,
            onPartial = { controller?.onPartialResult(it) },
            onFinal = { controller?.onFinalResult(it) },
            onError = { failed -> controller?.onError(failed) },
            onEnd = { controller?.onDictationEnd() },
        )
    }

    /** Controller banne ke baad wire hota hai (lazy circular dependency todne ke liye). */
    var controller: VoiceWidgetController? = null

    override val screenWidthDp: Float
        get() = screenWidthDpOverride
            ?: (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density)
    override val screenHeightDp: Float
        get() = screenHeightDpOverride
            ?: (context.resources.displayMetrics.heightPixels / context.resources.displayMetrics.density)
    override val hindiUi: Boolean get() = model.settings.uiHindi

    // ── persistence ────────────────────────────────────────────────────────────
    override fun loadX() = store.loadX()
    override fun loadY() = store.loadY()
    override fun savePosition(x: Float, y: Float) = store.savePosition(x, y)
    override fun loadOrientation() = store.loadOrientation()
    override fun saveOrientation(o: WidgetOrientation) = store.saveOrientation(o)
    override fun loadModeEnabled() = store.loadModeEnabled()
    override fun saveModeEnabled(v: Boolean) = store.saveModeEnabled(v)
    override fun isDragLocked() = store.isDragLocked()
    override fun loadRecentSymbols() = store.loadRecentSymbols()
    override fun pushRecentSymbol(s: String) = store.pushRecentSymbol(s)
    override fun loadTooltipSeen() = store.loadTooltipSeen()
    override fun saveTooltipSeen() = store.saveTooltipSeen()

    // ── dictation ──────────────────────────────────────────────────────────────
    override fun isDictationAvailable(): Boolean =
        bridge.isAvailable && hasMicPermission()

    override fun startDictation(preferOffline: Boolean) = bridge.start(preferOffline, recognitionLanguage)
    override fun stopDictation() = bridge.stop()
    override fun releaseDictation() = bridge.release()

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Gboard pattern: `<भाषा> (<देश>)` — recognition locale ka chhota tag. */
    fun localeTag(): String = recognitionLanguage

    /** Recognition language — menu ki language list se set hoti hai (prefs mein persist). */
    var recognitionLanguage: String = "hi-IN"

    override fun languages(): List<String> = LANGUAGES

    override fun setRecognitionLanguage(language: String) {
        // menu label ("English (US)") → BCP-47 tag ("en-US")
        recognitionLanguage = languageTagOf(language)
    }

    override fun gondiDigits(): List<String> = Numbers.ROWS[0].map { it.g }

    // ── text (maujooda pipeline reuse) ─────────────────────────────────────────
    override fun insertText(text: String) = model.insertBulk(text)

    override fun backspaceOnce() = engine.backspace()

    override fun hasTextField(): Boolean =
        hasTextFieldOverride ?: (editorInputType() != EditorInfo.TYPE_NULL)

    private fun editorInputType(): Int = editorInfo?.inputType ?: EditorInfo.TYPE_NULL

    /** IME service apna EditorInfo inject karta hai. */
    var editorInfo: EditorInfo? = null

    // ── UI feedback ────────────────────────────────────────────────────────────
    override fun toast(message: String) = model.settings.toast(message)

    override fun openSettings() = openSettingsScreen()

    override fun openFullKeyboard() { onChanged() }

    override fun requestPermission(recordAudio: Boolean) {
        val granted = hasMicPermission()
        val msg = when {
            !granted -> if (hindiUi)
                "बोली को लिखाई में बदलने के लिए माइक्रोफ़ोन की अनुमति चाहिए"
            else "Microphone permission is needed for voice typing"
            !bridge.isAvailable -> if (hindiUi)
                "इस डिवाइस पर speech recognition उपलब्ध नहीं है"
            else "Speech recognition is not available on this device"
            else -> null
        }
        msg?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        if (!granted) {
            // IME ke paas activity nahi hoti — system app-settings par bhejo
            runCatching {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
            }
        }
    }

    override fun onChanged() = onChanged.invoke()

    // ── helpers ────────────────────────────────────────────────────────────────
    companion object {
        /**
         * Language list — Gboard ka `<भाषा> (<देश>)` pattern. Yeh MgBoard ke enabled
         * keyboard modes se aati hai (Gondi/हिंदी = hi-IN, English = en-IN), hardcoded
         * recognizer list nahi.
         */
        val LANGUAGES = VOICE_LANGUAGES

        fun languageTagOf(label: String): String = voiceLanguageTag(label)
    }

    /** Caret se pehle ka text — status/undo decisions ke liye (code-point safe). */
    fun textBeforeCursor(): String {
        val t = engine.input.text
        val idx = engine.cursorIndex()
        return CpText.cpsAsStrings(t).take(idx).joinToString("")
    }
}
