package com.mgboard.keyboard.ime

import android.content.Intent
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.mgboard.keyboard.SettingsActivity
import com.mgboard.keyboard.engine.TypingEngine
import com.mgboard.keyboard.prefs.SgPrefs
import com.mgboard.keyboard.ui.KeyboardScreen
import com.mgboard.keyboard.ui.MgTheme
import com.mgboard.keyboard.voice.DictationBridge
import com.mgboard.keyboard.voice.ImeVoiceHost
import com.mgboard.keyboard.voice.VoiceWidgetController
import com.mgboard.keyboard.voice.VoiceWidgetLayer
import com.mgboard.keyboard.voice.VoiceWidgetRules
import com.mgboard.keyboard.voice.WidgetPositionStore
import kotlin.math.roundToInt

/**
 * MgBoard IME — `InputMethodService`.
 *
 * Architecture:
 *  - [ImeTextInput]   : editor ke text ko engine ke [com.mgboard.keyboard.engine.TextInput]
 *                       contract par map karta hai (composing region based)
 *  - [TypingEngine]   : pure logic (web ka 1:1 port) — composition, yukt, backspace, undo
 *  - [KeyboardModel]  : platform-agnostic UI model (rows, grid, popups, shift)
 *  - [VoiceWidgetController] : voice toolbar (Gboard-style pill) — **additive module**,
 *                       keyboard ke key pipeline ko chhuta nahi
 *  - Compose          : `KeyboardScreen` + `VoiceWidgetLayer`, key gestures 400/70/300ms
 *
 * Settings live apply hoti hain: [SgPrefs] ke listener se recompose, isliye height /
 * one-handed / theme badalne par keyboard turant update hota hai (restart nahi —
 * padding-project ki requirement).
 */
class MgBoardIme : InputMethodService(), KeyboardHost {

    private lateinit var prefs: SgPrefs
    private lateinit var textInput: ImeTextInput
    private lateinit var model: KeyboardModel
    private lateinit var positionStore: WidgetPositionStore
    private lateinit var voiceHost: ImeVoiceHost
    private lateinit var voice: VoiceWidgetController
    private var composeView: ComposeView? = null

    /** Compose ko refresh karne wala observable tick. */
    private var tick by mutableStateOf(0)

    private val prefsListener: () -> Unit = { tick++; applyLiveSettings() }

    /** Pill ka on-screen rect (px) — `onComputeInsets()` ka touchable region isi se banta hai. */
    private val pillRectPx = Rect()

    override fun onCreate() {
        super.onCreate()
        prefs = SgPrefs(this)
        textInput = ImeTextInput { currentInputConnection }
        val engine = TypingEngine(textInput)
        model = KeyboardModel(engine, PrefsSettingsSource(this, prefs) { openSettingsActivity() })
        model.onChange = { tick++ }

        // ── voice toolbar (additive; keyboard pipeline untouched) ──────────────
        positionStore = WidgetPositionStore(this)
        voiceHost = ImeVoiceHost(
            context = this,
            model = model,
            engine = engine,
            store = positionStore,
            openSettingsScreen = { openSettingsActivity() },
            onChanged = { tick++ },
        )
        voice = VoiceWidgetController(voiceHost)
        voiceHost.controller = voice

        prefs.addListener(prefsListener)
    }

    override fun onDestroy() {
        voice.release()
        gesturePadding.detach()
        prefs.removeListener(prefsListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = ComposeView(this).apply {
            setContent {
                MgTheme(forcedTheme = prefs.getTheme()) {
                    val t = tick
                    // Pill mode mein IME window fullscreen ho jaata hai aur keyboard
                    // chhup jaata hai — sirf pill dikhti hai (Gboard-exact).
                    val pillMode = voice.isPillMode
                    Box(Modifier.fillMaxSize()) {
                        if (!pillMode) {
                            KeyboardScreen(model = model, host = this@MgBoardIme, tick = t)
                        }
                        VoiceWidgetLayer(controller = voice, tick = t)
                    }
                    // pill ka rect nikalo (touchable region ke liye)
                    computePillRect(resources.displayMetrics.density, pillMode)
                }
            }
        }
        composeView = view
        gesturePadding.attach(view)
        gesturePadding.setKeyboardBackgroundColor(surfaceColorInt())
        applyLiveSettings()
        return view
    }

    /**
     * Pill mode ka touch pass-through — Gboard ka asli mechanism (RESEARCH.md §2.4).
     *
     * `TOUCHABLE_INSETS_REGION` + `setTouchableRegion(pill)` se pill ke **bahar** ke
     * tap seedha neeche wali app ko jaate hain, jabki pill khud interactive rehti hai.
     * `contentTopInsets` itna rakhte hain ki app content pill ke neeche na chhupa rahe.
     */
    override fun onComputeInsets(outInsets: Insets) {
        if (!voice.isPillMode) {
            setIsFullscreenMode(false)
            super.onComputeInsets(outInsets)
            return
        }
        setIsFullscreenMode(true)
        val v = composeView ?: run { super.onComputeInsets(outInsets); return }
        val h = v.height
        val w = v.width
        if (h == 0 || w == 0) { super.onComputeInsets(outInsets); return }

        outInsets.contentTopInsets = pillRectPx.bottom.coerceIn(0, h)
        outInsets.visibleTopInsets = h          // app content ko pan na karna pade
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(pillRectPx)
    }

    /** Pill state se on-screen rect (px) banao — window coordinates mein. */
    private fun computePillRect(density: Float, pillMode: Boolean) {
        if (!pillMode) { pillRectPx.setEmpty(); return }
        val s = voice.state
        val vertical = s is com.mgboard.keyboard.voice.VoiceWidgetState.VerticalPill
        val wDp = if (vertical) VoiceWidgetRules.VERTICAL_PILL_WIDTH_DP else VoiceWidgetRules.PILL_WIDTH_DP
        val hDp = if (vertical) VoiceWidgetRules.VERTICAL_PILL_HEIGHT_DP else VoiceWidgetRules.PILL_HEIGHT_DP
        val xDp = when (s) {
            is com.mgboard.keyboard.voice.VoiceWidgetState.HorizontalPill -> s.x
            is com.mgboard.keyboard.voice.VoiceWidgetState.VerticalPill -> s.x
            else -> 0f
        }
        val yDp = when (s) {
            is com.mgboard.keyboard.voice.VoiceWidgetState.HorizontalPill -> s.y
            is com.mgboard.keyboard.voice.VoiceWidgetState.VerticalPill -> s.y
            else -> 0f
        }
        val pad = 6 * density   // shadow/border ka margin — tap target thoda bada
        pillRectPx.set(
            ((xDp * density) - pad).roundToInt().coerceAtLeast(0),
            ((yDp * density) - pad).roundToInt().coerceAtLeast(0),
            (((xDp + wDp) * density) + pad).roundToInt(),
            (((yDp + hDp) * density) + pad).roundToInt(),
        )
        requestComputeInsets()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        voiceHost.editorInfo = info
        gesturePadding.onStartInputView()
        syncFromEditor()
        // Gboard: pill "remains active the next time you open a text field"
        voice.restoreIfEnabled()
        tick++
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Cursor/paste/selection badla → composition state text se re-derive karo
        // (web ka rule: kabhi last-pressed-key se infer mat karo)
        syncFromEditor()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // mic release — battery rule (RESEARCH.md §9.5): recognition band hote hi destroy
        voice.release()
        voice.saveState()
        model.closePopups()
        model.closeGrid()
        setIsFullscreenMode(false)
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        voice.release()
        super.onWindowHidden()
    }

    /** Editor ka text/cursor engine ke window mein kheencho. */
    override fun syncFromEditor() {
        if (textInput.sync()) model.onEditorSynced()
    }

    /** Enter ka contextual label (Gboard jaisa: Go/Send/Search/Next/Done). */
    override val enterLabel: String
        get() = enterLabelFor(currentInputEditorInfo)

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: android.view.inputmethod.InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        syncFromEditor()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        // rotation / RRO overlay change → padding + pill position dobara clamp
        gesturePadding.onConfigurationChanged(newConfig)
        voice.onConfigurationChanged()
        tick++
    }

    /** Back button: popups/grid/voice-widget pehle band karo, warna system behaviour. */
    override fun onBackPressed(): Boolean {
        if (voice.onBackPressed()) { tick++; return true }
        if (model.gridOpen) { model.closeGrid(); return true }
        if (model.popupChar != null || model.popupMulti.isNotEmpty()) { model.closePopups(); tick++; return true }
        return super.onBackPressed()
    }

    /** Toolbar ka 🎤 access point → voice panel (State 1). */
    override fun onMicTap() {
        voice.onMicAccessPointTap()
        tick++
    }

    private fun openSettingsActivity() {
        val i = Intent(this, SettingsActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(i)
        // pill mode mein keyboard hide nahi karna — settings activity upar aa jaati hai
        if (!voice.isPillMode) requestHideSelf(0)
    }

    /**
     * padding-project (Gboard-exact): gesture-navigation par keyboard ke neeche
     * system inset jitni gap, keyboard ke background color mein painted.
     * Height hamesha WindowInsets se — koi hardcoded px nahi. 3-button/2-button nav
     * par visible gap nahi hoti. Mode switch par live re-apply (restart nahi).
     */
    private val gesturePadding by lazy {
        com.mgboard.keyboard.ime.insets.GestureNavPaddingController(this).apply {
            isKeyboardDockedAtBottom = {
                // Gboard: gap sirf "normal mode" mein (floating/split/one-handed/pill mein nahi)
                model.settings.oneHanded.isEmpty() && !voice.isPillMode
            }
            debugLog = false
        }
    }

    /**
     * Height ratio / one-handed / theme ko view par **turant** apply karo —
     * keyboard restart nahi hota (padding-project ki live-update requirement).
     */
    private fun applyLiveSettings() {
        gesturePadding.setKeyboardBackgroundColor(surfaceColorInt())
        gesturePadding.reapply()
        composeView?.requestLayout()
        tick++
    }

    /** Theme ke hisaab se keyboard background ka ARGB (padding strip isi se paint hoti hai). */
    private fun surfaceColorInt(): Int {
        val dark = when (prefs.getTheme()) {
            "dark", "amoled" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        return if (prefs.getTheme() == "amoled") android.graphics.Color.BLACK
               else if (dark) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt()
    }

    /** Voice/bulk text insert (Devanagari → Gondi conversion model karta hai). */
    fun insertBulkText(text: String) {
        model.insertBulk(text)
        syncFromEditor()
    }

    fun editorAction(code: Int) {
        currentInputConnection?.performEditorAction(code)
    }

    fun appPrefs(): SgPrefs = prefs
    fun voiceController(): VoiceWidgetController = voice
}
