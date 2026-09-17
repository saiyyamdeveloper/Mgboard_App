package com.mgboard.keyboard.voice

/**
 * Voice widget ka **brain** — state machine, transitions, drag, menu, prefs.
 *
 * Yeh class pure Kotlin hai (koi Android import nahi): saara platform kaam
 * [VoiceHost] ke through hota hai, isliye poora behaviour plain JVM par test ho jaata hai.
 *
 * Standing rule (owner): voice pill ek **naya, additive** module hai —
 * `KeyboardView`/key-event pipeline, converter, nukta rules aur backspace-repeat handler
 * ko chhuta hi nahi. Backspace ke liye wahi maujooda timing **reuse** hoti hai
 * ([com.mgboard.keyboard.layout.KeyTiming]: 400ms initial, 70ms repeat).
 */
class VoiceWidgetController(private val host: VoiceHost) {

    /** Platform seam — IME aur launcher-preview dono apna implementation dete hain. */
    interface VoiceHost {
        // screen geometry (dp) — drag/clamp ke liye
        val screenWidthDp: Float
        val screenHeightDp: Float
        val hindiUi: Boolean

        // persistence (Gboard: widget_x_position / widget_y_position …)
        fun loadX(): Float?
        fun loadY(): Float?
        fun savePosition(x: Float, y: Float)
        fun loadOrientation(): WidgetOrientation
        fun saveOrientation(o: WidgetOrientation)
        fun loadModeEnabled(): Boolean
        fun saveModeEnabled(v: Boolean)
        fun isDragLocked(): Boolean
        fun loadRecentSymbols(): List<String>
        fun pushRecentSymbol(s: String)
        fun loadTooltipSeen(): Boolean
        fun saveTooltipSeen()

        /** Menu ki language list (enabled-languages prefs se — hardcoded nahi). */
        fun languages(): List<String>
        fun setRecognitionLanguage(language: String)
        /** Gondi digits (U+11D50–U+11D59) — symbols overlay ki Numbers category. */
        fun gondiDigits(): List<String>

        // dictation (SpeechRecognizer) — DictationBridge implement karta hai
        fun isDictationAvailable(): Boolean
        fun startDictation(preferOffline: Boolean)
        fun stopDictation()
        fun releaseDictation()

        // text — maujooda pipeline hi use hota hai (dubara implement nahi karna)
        fun insertText(text: String)
        /** Backspace: host ke paas wahi repeat handler hai jo keyboard ke ⌫ par hai. */
        fun backspaceOnce()
        fun hasTextField(): Boolean

        // UI feedback
        fun toast(message: String)
        fun openSettings()
        fun openFullKeyboard()
        fun requestPermission(recordAudio: Boolean)
        fun onChanged()
    }

    var state: VoiceWidgetState = VoiceWidgetState.Hidden
        private set

    /** Status text — mic state + partial transcript se banta hai. */
    var statusText: StatusText = StatusText.SPEAK_NOW
        private set

    /** Partial transcript (recognition chalte waqt live text). */
    var partialTranscript: String = ""
        private set

    /** Active recognition locale ka chhota tag (mic ke kone par display-only). */
    var localeTag: String = "hi-IN"
        private set

    /** Pehli baar drag par "Hold and drag to move toolbar" tooltip (Gboard flag-gated). */
    var tooltipVisible: Boolean = false
        private set

    /** Owner decision: offline recognition prefer karo, na ho to online. */
    var preferOffline: Boolean = true

    private var underlyingBeforeSymbols: VoiceWidgetState = VoiceWidgetState.Hidden

    init {
        restoreIfEnabled()
    }

    // ══════════════════════════ entry points ══════════════════════════

    /** Keyboard ke toolbar par 🎤 access-point tap. */
    fun onMicAccessPointTap() {
        val r = VoiceWidgetTransitions.micTapped(state, host.hindiUi)
        apply(r)
        if (state is VoiceWidgetState.FullVoicePanel) startListening()
    }

    /** State 1 ka ˅˅ chevron — pill dikhne ka ekmatra trigger. */
    fun onChevronTap() {
        val r = VoiceWidgetTransitions.chevronTapped(state, host.screenWidthDp, host.screenHeightDp)
        apply(r)
        host.saveModeEnabled(true)
        persist()
    }

    /** ⌨ keyboard icon — pill dismiss, normal typing mode. */
    fun onKeyboardTap() {
        stopListening()
        apply(VoiceWidgetTransitions.keyboardTapped(state))
        host.saveModeEnabled(false)
        host.openFullKeyboard()
    }

    /** भाषा-badge / hamburger → menu (mic auto-pause). */
    fun onBadgeTap() {
        apply(VoiceWidgetTransitions.badgeTapped(state))
        if (state is VoiceWidgetState.MenuOpen) pauseListening()
    }

    /** Pill ke 🎤 par tap = pause/resume toggle (pill band nahi hoti). */
    fun onPillMicTap() {
        val s = state
        when (s) {
            is VoiceWidgetState.HorizontalPill -> {
                val next = if (s.mic == MicState.LISTENING) MicState.PAUSED else MicState.LISTENING
                apply(VoiceWidgetTransitions.Result(s.copy(mic = next), VoiceWidgetRules.ANIM_FADE_MS))
                if (next == MicState.LISTENING) startListening() else pauseListening()
            }
            is VoiceWidgetState.VerticalPill -> {
                val next = if (s.mic == MicState.LISTENING) MicState.PAUSED else MicState.LISTENING
                apply(VoiceWidgetTransitions.Result(s.copy(mic = next), VoiceWidgetRules.ANIM_FADE_MS))
                if (next == MicState.LISTENING) startListening() else pauseListening()
            }
            else -> onMicAccessPointTap()
        }
    }

    /** Pill ka ⌫ — ek char delete. Hold-repeat host ke maujooda handler se aata hai. */
    fun onBackspace() {
        if (!host.hasTextField()) {
            host.toast(StatusText.NO_TEXT_FIELD.label(host.hindiUi))
            return
        }
        host.backspaceOnce()
    }

    fun onMenuItem(item: VoiceMenuItem) {
        val r = VoiceWidgetTransitions.menuItemTapped(item, state, host.screenWidthDp, host.screenHeightDp)
        if (item == VoiceMenuItem.SYMBOLS) underlyingBeforeSymbols = (state as? VoiceWidgetState.MenuOpen)?.over ?: state
        apply(r)
        when (item) {
            VoiceMenuItem.SETTINGS -> host.openSettings()
            VoiceMenuItem.SHOW_VOICE_COMMANDS ->
                host.toast(if (host.hindiUi) "सभी वॉइस कमांड" else "All voice commands")
            VoiceMenuItem.SHOW_CLIPBOARD,
            VoiceMenuItem.SHOW_TRANSLATE,
            VoiceMenuItem.SHOW_EMOJI,
            VoiceMenuItem.FEEDBACK -> host.toast(gateReason(item))
            VoiceMenuItem.SWITCH_VERTICAL -> host.saveOrientation(WidgetOrientation.VERTICAL)
            VoiceMenuItem.SWITCH_HORIZONTAL -> host.saveOrientation(WidgetOrientation.HORIZONTAL)
            VoiceMenuItem.SYMBOLS -> { host.stopDictation() }
        }
        persist()
    }

    /**
     * hide-nothing: jo feature backend ke bina hai woh chhupaya nahi jaata —
     * Gboard ka verbatim reason dikhta hai (grid-menu wale rules jaisa).
     */
    fun gateReason(item: VoiceMenuItem): String = if (host.hindiUi) when (item) {
        VoiceMenuItem.SHOW_CLIPBOARD -> "इस ऐप में क्लिपबोर्ड उपलब्ध नहीं है"
        VoiceMenuItem.SHOW_TRANSLATE -> "Can't use this tool at the moment. Please try again later."
        VoiceMenuItem.SHOW_EMOJI -> "इस ऐप में इमोजी उपलब्ध नहीं है"
        VoiceMenuItem.FEEDBACK -> "फ़ीडबैक के लिए इंटरनेट और ईमेल क्लाइंट चाहिए"
        else -> "Command not available in this app"
    } else when (item) {
        VoiceMenuItem.SHOW_CLIPBOARD -> "Clipboard is not available in this app"
        VoiceMenuItem.SHOW_TRANSLATE -> "Can't use this tool at the moment. Please try again later."
        VoiceMenuItem.SHOW_EMOJI -> "Emoji is not available in this app"
        VoiceMenuItem.FEEDBACK -> "Feedback needs the internet and an email client"
        else -> "Command not available in this app"
    }

    /** Symbols overlay ka X / back. */
    fun onSymbolsClose() {
        apply(VoiceWidgetTransitions.dismissed(state, underlyingBeforeSymbols))
    }

    fun onSymbolTap(symbol: String) {
        host.insertText(symbol)
        host.pushRecentSymbol(symbol)
        notifyChanged()
    }

    fun onSymbolCategoryTap(category: SymbolCategory) {
        if (state is VoiceWidgetState.SymbolsOverlay) {
            setState(VoiceWidgetState.SymbolsOverlay(category))
        }
    }

    /** Back button / bahar tap. */
    fun onBackPressed(): Boolean {
        val s = state
        return when (s) {
            is VoiceWidgetState.MenuOpen -> { apply(VoiceWidgetTransitions.dismissed(s, s.over)); true }
            is VoiceWidgetState.SymbolsOverlay -> { onSymbolsClose(); true }
            VoiceWidgetState.Hidden -> false
            else -> {
                // pill/panel par back = keyboard wapas
                onKeyboardTap()
                true
            }
        }
    }

    // ══════════════════════════ drag ══════════════════════════

    fun onDrag(dx: Float, dy: Float) {
        if (host.isDragLocked()) {
            // "Force the toolbar in horizontal mode and disable dragging"
            host.toast(if (host.hindiUi) "टूलबार क्षैतिज मोड में लॉक है" else "Toolbar is locked in horizontal mode")
            return
        }
        val first = !host.loadTooltipSeen()
        val outcome = WidgetDrag.onDrag(
            state = state,
            dx = dx, dy = dy,
            screenWidthDp = host.screenWidthDp,
            screenHeightDp = host.screenHeightDp,
            dragLocked = false,
            firstDrag = first,
        )
        setState(outcome.state)
        if (outcome.showTooltip) { tooltipVisible = true; host.saveTooltipSeen() }
        if (outcome.flipped) {
            host.saveOrientation(
                if (outcome.state is VoiceWidgetState.VerticalPill) WidgetOrientation.VERTICAL
                else WidgetOrientation.HORIZONTAL
            )
        }
        if (outcome.docked) notifyChanged()
        persist()
    }

    fun onDragEnd() {
        tooltipVisible = false
        notifyChanged()
    }

    // ══════════════════════════ dictation callbacks ══════════════════════════

    fun onPartialResult(text: String) {
        partialTranscript = text
        statusText = StatusText.LISTENING
        setMic(MicState.LISTENING)
        notifyChanged()
    }

    /**
     * Final transcript — **maujooda pipeline** hi use hota hai:
     * `host.insertText()` IME mein `KeyboardModel.insertBulk()` ko call karta hai, jo
     * Gondi mode mein `DevToGondi.convert()` chalata hai. Converter ko chheda nahi gaya.
     */
    fun onFinalResult(text: String) {
        partialTranscript = ""
        if (text.isBlank()) return
        if (!host.hasTextField()) {
            statusText = StatusText.NO_TEXT_FIELD
            host.toast(statusText.label(host.hindiUi))
            notifyChanged()
            return
        }
        host.insertText(text)
        statusText = StatusText.SPEAK_NOW
        setMic(MicState.LISTENING)
        notifyChanged()
    }

    fun onError(offlineFallbackFailed: Boolean) {
        partialTranscript = ""
        setMic(if (offlineFallbackFailed) MicState.ERROR else MicState.OFFLINE)
        statusText = StatusText.ERROR_UNSUPPORTED
        host.toast(statusText.label(host.hindiUi))
        notifyChanged()
    }

    fun onDictationEnd() {
        setMic(MicState.PAUSED)
        statusText = StatusText.PAUSED
        notifyChanged()
    }

    // ══════════════════════════ lifecycle ══════════════════════════

    /**
     * Gboard: *"this toolbar is persistent and remains active the next time you open a
     * text field until you manually bring back the keyboard."*
     * IME window marte hi widget bhi jaata hai — "persistent" ka matlab yahi hai ki
     * mode prefs mein yaad rehta hai aur agla text field khulte hi restore ho jaata hai.
     */
    fun restoreIfEnabled() {
        if (!host.loadModeEnabled()) return
        val o = host.loadOrientation()
        setState(
            when (o) {
                WidgetOrientation.VERTICAL -> restoreVertical()
                WidgetOrientation.HORIZONTAL -> restoreHorizontal()
            }
        )
        statusText = StatusText.SPEAK_NOW
    }

    private fun restoreHorizontal(): VoiceWidgetState {
        val def = WidgetDrag.defaultHorizontal(host.screenWidthDp, host.screenHeightDp)
        val x = host.loadX() ?: def.x
        val y = host.loadY() ?: def.y
        return WidgetDrag.clamp(def.copy(x = x, y = y), host.screenWidthDp, host.screenHeightDp)
    }

    private fun restoreVertical(): VoiceWidgetState {
        val dock = if ((host.loadX() ?: host.screenWidthDp) < host.screenWidthDp / 2f) Dock.LEFT else Dock.RIGHT
        val def = WidgetDrag.defaultVertical(dock, host.screenWidthDp, host.screenHeightDp)
        val y = host.loadY() ?: def.y
        return WidgetDrag.clamp(def.copy(y = y), host.screenWidthDp, host.screenHeightDp)
    }

    /** Rotation / fold / config change → position re-clamp (state wahi rehta hai). */
    fun onConfigurationChanged() {
        setState(WidgetDrag.clamp(state, host.screenWidthDp, host.screenHeightDp))
        persist()
    }

    fun saveState() = persist()

    /** Mic release — `onWindowHidden()`/`onDestroy()` mein (battery rule, RESEARCH.md §9.5). */
    fun release() {
        stopListening()
        host.releaseDictation()
    }

    // ══════════════════════════ internals ══════════════════════════

    private fun startListening() {
        if (!host.isDictationAvailable()) {
            host.requestPermission(recordAudio = true)
            setMic(MicState.ERROR)
            statusText = StatusText.ERROR_UNSUPPORTED
            notifyChanged()
            return
        }
        if (!host.hasTextField()) {
            statusText = StatusText.NO_TEXT_FIELD
            host.toast(statusText.label(host.hindiUi))
            notifyChanged()
            return
        }
        host.startDictation(preferOffline)
        setMic(MicState.LISTENING)
        statusText = StatusText.SPEAK_NOW
        notifyChanged()
    }

    private fun pauseListening() {
        host.stopDictation()
        setMic(MicState.PAUSED)
        statusText = StatusText.PAUSED
        notifyChanged()
    }

    private fun stopListening() {
        host.stopDictation()
        partialTranscript = ""
    }

    private fun setMic(mic: MicState) {
        setState(
            when (val s = state) {
                is VoiceWidgetState.HorizontalPill -> s.copy(mic = mic)
                is VoiceWidgetState.VerticalPill -> s.copy(mic = mic)
                is VoiceWidgetState.FullVoicePanel -> s.copy(mic = mic)
                else -> s
            }
        )
    }

    private fun apply(r: VoiceWidgetTransitions.Result) {
        setState(r.state)
        notifyChanged()
    }

    /**
     * State set karo. Notification caller karta hai (`notifyChanged()`), taaki ek user action
     * par ek hi recompose ho.
     */
    private fun setState(s: VoiceWidgetState) {
        state = s
        // status text state ke saath sync
        when (s) {
            is VoiceWidgetState.HorizontalPill -> if (s.mic == MicState.PAUSED) statusText = StatusText.PAUSED
            is VoiceWidgetState.VerticalPill -> if (s.mic == MicState.PAUSED) statusText = StatusText.PAUSED
            else -> Unit
        }
    }

    private fun persist() {
        when (val s = state) {
            is VoiceWidgetState.HorizontalPill -> host.savePosition(s.x, s.y)
            is VoiceWidgetState.VerticalPill -> host.savePosition(s.x, s.y)
            else -> Unit
        }
    }

    private fun notifyChanged() {
        revision++
        host.onChanged()
    }

    /** Pill abhi screen par hai (touch pass-through region isi se banta hai). */
    val isPillMode: Boolean
        get() = state is VoiceWidgetState.HorizontalPill || state is VoiceWidgetState.VerticalPill

    /** Compose ko recompose karne wala counter — har notifyChanged() par badhta hai. */
    var revision: Int = 0
        private set

    fun isHindi(): Boolean = host.hindiUi

    fun micState(): MicState = when (val s = state) {
        is VoiceWidgetState.HorizontalPill -> s.mic
        is VoiceWidgetState.VerticalPill -> s.mic
        is VoiceWidgetState.FullVoicePanel -> s.mic
        is VoiceWidgetState.MenuOpen -> when (val o = s.over) {
            is VoiceWidgetState.HorizontalPill -> o.mic
            is VoiceWidgetState.VerticalPill -> o.mic
            is VoiceWidgetState.FullVoicePanel -> o.mic
            else -> MicState.PAUSED
        }
        else -> MicState.PAUSED
    }

    /** Symbols overlay ke peeche wali pill (owner decision: peeche visible rehti hai). */
    fun underlyingState(): VoiceWidgetState = underlyingBeforeSymbols

    /**
     * Category ka symbol grid. Numbers category mein **Gondi digits** (U+11D50–U+11D59)
     * aate hain — wahi jo keyboard ke numbers panel mein hain.
     */
    fun symbolsFor(category: SymbolCategory): List<String> =
        VoiceSymbols.grid(category, host.gondiDigits(), host.loadRecentSymbols())

    /**
     * Menu ke divider ke baad wali language list.
     * Gboard pattern: har language ke liye `<भाषा> (<देश>)`, kuch ke "संक्षिप्त/compact"
     * variants. Yeh list **enabled-languages prefs** se banti hai, hardcoded nahi —
     * MgBoard ke 3 keyboard modes hi abhi languages hain.
     */
    fun languages(): List<String> = host.languages()

    fun onLanguageTap(language: String) {
        host.setRecognitionLanguage(language)
        // mic ke kone par chhota tag BCP-47 mein dikhta hai (display-only)
        localeTag = voiceLanguageTag(language)
        // menu band, pill wapas (mic paused rehta hai — user ko dobara tap karna hota hai)
        apply(VoiceWidgetTransitions.dismissed(state, (state as? VoiceWidgetState.MenuOpen)?.over ?: state))
        persist()
    }

    /** Current display status — partial transcript ho to wahi dikhta hai. */
    fun displayStatus(): String =
        partialTranscript.ifBlank { statusText.label(host.hindiUi) }
}
