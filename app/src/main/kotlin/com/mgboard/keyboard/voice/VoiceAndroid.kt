package com.mgboard.keyboard.voice

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.mgboard.keyboard.prefs.SgPrefs

/**
 * Widget ki position/orientation/mode ka store.
 *
 * Keys Gboard ke APK se verbatim hain (`widget_x_position`, `widget_y_position`,
 * `widget_change_widget_orientation`) — research: RESEARCH.md §5.1 + APK-EVIDENCE.md.
 * Wahi SharedPreferences file use hoti hai jo baaki settings ki hai ([SgPrefs.FILE]),
 * isliye ek hi jagah se sab kuch live update hota hai.
 */
class WidgetPositionStore(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(SgPrefs.FILE, Context.MODE_PRIVATE)

    fun loadX(): Float? = if (sp.contains(VoiceWidgetRules.KEY_X)) sp.getFloat(VoiceWidgetRules.KEY_X, 0f) else null
    fun loadY(): Float? = if (sp.contains(VoiceWidgetRules.KEY_Y)) sp.getFloat(VoiceWidgetRules.KEY_Y, 0f) else null

    fun savePosition(x: Float, y: Float) {
        sp.edit()
            .putFloat(VoiceWidgetRules.KEY_X, x)
            .putFloat(VoiceWidgetRules.KEY_Y, y)
            .apply()
    }

    fun loadOrientation(): WidgetOrientation =
        if (sp.getString(VoiceWidgetRules.KEY_ORIENTATION, WidgetOrientation.HORIZONTAL.name) ==
            WidgetOrientation.VERTICAL.name
        ) WidgetOrientation.VERTICAL else WidgetOrientation.HORIZONTAL

    fun saveOrientation(o: WidgetOrientation) {
        sp.edit().putString(VoiceWidgetRules.KEY_ORIENTATION, o.name).apply()
    }

    fun loadModeEnabled(): Boolean = sp.getBoolean(VoiceWidgetRules.KEY_MODE_ENABLED, false)
    fun saveModeEnabled(v: Boolean) {
        sp.edit().putBoolean(VoiceWidgetRules.KEY_MODE_ENABLED, v).apply()
    }

    /** "Force the toolbar in horizontal mode and disable dragging" (Gboard debug flag). */
    fun isDragLocked(): Boolean = sp.getBoolean(VoiceWidgetRules.KEY_DRAG_LOCKED, false)

    fun loadTooltipSeen(): Boolean = sp.getBoolean(VoiceWidgetRules.KEY_TOOLTIP_SEEN, false)
    fun saveTooltipSeen() {
        sp.edit().putBoolean(VoiceWidgetRules.KEY_TOOLTIP_SEEN, true).apply()
    }

    /** Recently used symbols — Gboard ki "Recent" category. */
    fun loadRecentSymbols(): List<String> =
        sp.getString(VoiceWidgetRules.KEY_RECENT_SYMBOLS, "")
            ?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()

    fun pushRecentSymbol(s: String) {
        val next = (listOf(s) + loadRecentSymbols()).distinct().take(24)
        sp.edit().putString(VoiceWidgetRules.KEY_RECENT_SYMBOLS, next.joinToString("|")).apply()
    }

    fun clear() {
        sp.edit()
            .remove(VoiceWidgetRules.KEY_X).remove(VoiceWidgetRules.KEY_Y)
            .remove(VoiceWidgetRules.KEY_ORIENTATION).remove(VoiceWidgetRules.KEY_MODE_ENABLED)
            .apply()
    }
}

/**
 * `SpeechRecognizer` ↔ MgBoard pipeline ka bridge.
 *
 * Owner decision: **offline prefer karo, na ho to online** —
 * `EXTRA_PREFER_OFFLINE = true`. Device par on-device model na ho to recognizer
 * normal (network) path par gir jaata hai; dono hi case mein transcript wahi
 * maujooda `DevToGondi` pipeline se guzarta hai (converter ko chheda nahi gaya).
 *
 * Battery rule (RESEARCH.md §9.5): IME visible = "while-in-use", isliye koi
 * foreground service nahi chahiye. Lekin recognition band hote hi `destroy()` karo —
 * `onWindowHidden()`/`onFinishInputView()`/`onDestroy()` mein [release] call hota hai.
 */
class DictationBridge(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (offlineFallbackFailed: Boolean) -> Unit,
    private val onEnd: () -> Unit,
) {

    private var recognizer: SpeechRecognizer? = null
    private var preferOfflineRequested = false

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(preferOffline: Boolean, localeTag: String = "hi-IN") {
        if (!isAvailable) {
            onError(true)
            return
        }
        preferOfflineRequested = preferOffline
        destroyInternal()

        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (preferOffline) {
                // API 23+; device-dependent — na ho to recognizer khud online chala jaata hai
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        runCatching { r.startListening(intent) }
            .onFailure { onError(true) }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    fun cancel() {
        runCatching { recognizer?.cancel() }
    }

    fun release() {
        cancel()
        destroyInternal()
    }

    private fun destroyInternal() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // offline request fail hua aur recognizer abhi tak koi result nahi de paaya
            // → ek baar online path try karo (owner decision: "offline prefer, na ho to online")
            if (preferOfflineRequested && error == SpeechRecognizer.ERROR_NO_MATCH) {
                preferOfflineRequested = false
                start(preferOffline = false)
                return
            }
            onError(error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        }

        override fun onResults(results: Bundle?) {
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = list?.firstOrNull().orEmpty()
            if (text.isNotBlank()) onFinal(text) else onEnd()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            list?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onPartial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
