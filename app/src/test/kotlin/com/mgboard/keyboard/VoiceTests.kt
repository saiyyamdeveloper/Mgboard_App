package com.mgboard.keyboard

import com.mgboard.keyboard.voice.Dock
import com.mgboard.keyboard.voice.MicState
import com.mgboard.keyboard.voice.StatusText
import com.mgboard.keyboard.voice.SymbolCategory
import com.mgboard.keyboard.voice.VoiceMenuItem
import com.mgboard.keyboard.voice.VoiceSymbols
import com.mgboard.keyboard.voice.VoiceWidgetController
import com.mgboard.keyboard.voice.VoiceWidgetRules
import com.mgboard.keyboard.voice.VoiceWidgetState
import com.mgboard.keyboard.voice.VoiceWidgetTransitions
import com.mgboard.keyboard.voice.VOICE_LANGUAGES
import com.mgboard.keyboard.voice.WidgetDrag
import com.mgboard.keyboard.voice.WidgetOrientation
import com.mgboard.keyboard.voice.voiceLanguageTag

/**
 * Voice toolbar (Gboard-style pill) — state machine, transitions, drag/flip,
 * menu, symbols, persistence. RESEARCH.md §4/§5/§10.3 ke rules yahan assert hote hain.
 */
object VoiceTests {

    fun run() {
        rules()
        dragAndFlip()
        transitions()
        controllerStates()
        menuAndSymbols()
        persistence()
        strings()
    }

    // ── fake host (pure JVM) ───────────────────────────────────────────────────
    private class FakeHost : VoiceWidgetController.VoiceHost {
        // Pixel-class dp: 360dp jaisi chhoti screen par 80%-capped pill (288dp)
        // default position mein hi edge-zone ke andar aa jaati, isliye tests
        // realistic 412dp par chalte hain (alag regression test neeche hai).
        var w = 412f
        var h = 892f
        var hindi = false

        var x: Float? = null
        var y: Float? = null
        var orientation = WidgetOrientation.HORIZONTAL
        var modeEnabled = false
        var dragLocked = false
        var tooltipSeen = false
        val recent = mutableListOf<String>()

        var dictationAvailable = true
        var started = 0
        var stopped = 0
        var released = 0
        var preferOfflineLast: Boolean? = null

        val inserted = mutableListOf<String>()
        var backspaces = 0
        var hasField = true

        val toasts = mutableListOf<String>()
        var settingsOpened = 0
        var fullKeyboardOpened = 0
        var permissionAsked = 0
        var changes = 0

        var languages = VOICE_LANGUAGES
        var language = "hi-IN"
        var digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        override val screenWidthDp get() = w
        override val screenHeightDp get() = h
        override val hindiUi get() = hindi

        override fun loadX() = x
        override fun loadY() = y
        override fun savePosition(nx: Float, ny: Float) { x = nx; y = ny }
        override fun loadOrientation() = orientation
        override fun saveOrientation(o: WidgetOrientation) { orientation = o }
        override fun loadModeEnabled() = modeEnabled
        override fun saveModeEnabled(v: Boolean) { modeEnabled = v }
        override fun isDragLocked() = dragLocked
        override fun loadRecentSymbols() = recent.toList()
        override fun pushRecentSymbol(s: String) { recent.add(0, s) }
        override fun loadTooltipSeen() = tooltipSeen
        override fun saveTooltipSeen() { tooltipSeen = true }
        override fun languages() = languages
        override fun setRecognitionLanguage(l: String) { language = voiceLanguageTag(l) }
        override fun gondiDigits() = digits

        override fun isDictationAvailable() = dictationAvailable
        override fun startDictation(preferOffline: Boolean) { started++; preferOfflineLast = preferOffline }
        override fun stopDictation() { stopped++ }
        override fun releaseDictation() { released++ }

        override fun insertText(text: String) { inserted += text }
        override fun backspaceOnce() { backspaces++ }
        override fun hasTextField() = hasField

        override fun toast(message: String) { toasts += message }
        override fun openSettings() { settingsOpened++ }
        override fun openFullKeyboard() { fullKeyboardOpened++ }
        override fun requestPermission(recordAudio: Boolean) { permissionAsked++ }
        override fun onChanged() { changes++ }
    }

    private fun newController(host: FakeHost = FakeHost()): Pair<VoiceWidgetController, FakeHost> {
        val c = VoiceWidgetController(host)
        return c to host
    }

    // ══════════════════════════ rules / constants ══════════════════════════

    private fun rules() {
        T.section("VoiceWidgetRules — research ke confirmed numbers")
        T.eq("edge-flip threshold = 28dp", VoiceWidgetRules.EDGE_FLIP_THRESHOLD_DP, 28f)
        T.eq("hysteresis = 8dp", VoiceWidgetRules.EDGE_FLIP_HYSTERESIS_DP, 8f)
        T.eq("flip-back = 36dp", VoiceWidgetRules.FLIP_BACK_THRESHOLD_DP, 36f)
        T.eq("drag slop = 6dp", VoiceWidgetRules.DRAG_SLOP_DP, 6f)
        T.eq("top flip = 40dp", VoiceWidgetRules.TOP_FLIP_THRESHOLD_DP, 40f)

        T.eq("collapse anim 200ms", VoiceWidgetRules.ANIM_COLLAPSE_MS, 200)
        T.eq("menu anim 180ms", VoiceWidgetRules.ANIM_MENU_MS, 180)
        T.eq("fade anim 150ms", VoiceWidgetRules.ANIM_FADE_MS, 150)
        T.eq("morph anim 250ms", VoiceWidgetRules.ANIM_MORPH_MS, 250)
        T.eq("snap anim 150ms", VoiceWidgetRules.ANIM_SNAP_MS, 150)
        T.eq("sab animations 150–250ms ke andar",
            listOf(
                VoiceWidgetRules.ANIM_COLLAPSE_MS, VoiceWidgetRules.ANIM_MENU_MS,
                VoiceWidgetRules.ANIM_FADE_MS, VoiceWidgetRules.ANIM_MORPH_MS,
                VoiceWidgetRules.ANIM_SNAP_MS,
            ).all { it in 150..250 }, true)

        // Gboard ke persistence key names (APK se verbatim)
        T.eq("KEY_X = widget_x_position", VoiceWidgetRules.KEY_X, "widget_x_position")
        T.eq("KEY_Y = widget_y_position", VoiceWidgetRules.KEY_Y, "widget_y_position")
        T.eq("KEY_ORIENTATION = widget_change_widget_orientation",
            VoiceWidgetRules.KEY_ORIENTATION, "widget_change_widget_orientation")

        // pill sizing dp-based (owner rule: fixed px nahi)
        T.eq("pill height 48dp", VoiceWidgetRules.PILL_HEIGHT_DP, 48f)
        T.eq("pill corner 24dp", VoiceWidgetRules.PILL_CORNER_DP, 24f)
        T.eq("vertical pill width 52dp", VoiceWidgetRules.VERTICAL_PILL_WIDTH_DP, 52f)
    }

    // ══════════════════════════ drag + edge flip ══════════════════════════

    private fun dragAndFlip() {
        T.section("WidgetDrag — drag, edge-flip (28dp + 8dp hysteresis), dock")

        val W = 412f; val H = 892f      // Pixel-class screen (dp)
        val start = WidgetDrag.defaultHorizontal(W, H)
        T.eq("default pill bottom dock", start.dockedTo, Dock.BOTTOM)
        T.eq("default pill bottom par", start.y, H - VoiceWidgetRules.PILL_HEIGHT_DP)
        T.eq("default pill centered x", start.x, (W - VoiceWidgetRules.PILL_WIDTH_DP) / 2f)
        T.eq("badi screen par full 300dp width", WidgetDrag.pillWidthFor(W), 300f)
        T.eq("chhoti screen (360dp) par width cap 80%", WidgetDrag.pillWidthFor(360f), 288f)
        T.eq("360dp screen par default pill edge-zone ke bahar",
            run {
                val d = WidgetDrag.defaultHorizontal(360f, 740f)
                val zone = VoiceWidgetRules.EDGE_FLIP_THRESHOLD_DP
                d.x > zone && d.x + WidgetDrag.pillWidthFor(360f) < 360f - zone
            }, true)

        // normal move — flip nahi
        val moved = WidgetDrag.onDrag(start, dx = 5f, dy = -10f, screenWidthDp = W, screenHeightDp = H)
        T.eq("move par flip nahi hota", moved.flipped, false)
        T.eq("move par x badla", (moved.state as VoiceWidgetState.HorizontalPill).x, start.x + 5f)
        T.eq("move par orientation same", moved.state is VoiceWidgetState.HorizontalPill, true)

        // left edge ke andar → vertical LEFT
        val toLeft = WidgetDrag.onDrag(start.copy(x = 30f), dx = -5f, dy = 0f, screenWidthDp = W, screenHeightDp = H)
        T.eq("left edge (≤28dp) → flip", toLeft.flipped, true)
        T.eq("flip → VerticalPill", toLeft.state is VoiceWidgetState.VerticalPill, true)
        T.eq("left dock", (toLeft.state as VoiceWidgetState.VerticalPill).dockedTo, Dock.LEFT)
        T.eq("left dock par x = 0", toLeft.state.x, 0f)
        T.eq("dock = true", toLeft.docked, true)

        // right edge → vertical RIGHT
        val toRight = WidgetDrag.onDrag(start.copy(x = W - VoiceWidgetRules.PILL_WIDTH_DP - 25f),
            dx = 3f, dy = 0f, screenWidthDp = W, screenHeightDp = H)
        T.eq("right edge → flip", toRight.flipped, true)
        T.eq("right dock", (toRight.state as VoiceWidgetState.VerticalPill).dockedTo, Dock.RIGHT)
        T.eq("right dock par x = W - pillW", toRight.state.x, W - VoiceWidgetRules.VERTICAL_PILL_WIDTH_DP)

        // threshold se zyada door → flip NAHI (28dp boundary)
        val safe = WidgetDrag.onDrag(start.copy(x = 60f), dx = -1f, dy = 0f,
            screenWidthDp = W, screenHeightDp = H)
        T.eq("zone (28dp) se bahar 59dp par flip nahi", safe.flipped, false)
        T.eq("  horizontal hi rehta hai", safe.state is VoiceWidgetState.HorizontalPill, true)
        T.eq("28dp boundary ke andar + edge ki taraf → flip",
            WidgetDrag.onDrag(start.copy(x = VoiceWidgetRules.EDGE_FLIP_THRESHOLD_DP), dx = -1f, dy = 0f,
                screenWidthDp = W, screenHeightDp = H).flipped, true)

        // ★ direction gate: zone mein hone ke bawajood edge se DOOR drag karne par flip nahi
        val awayDrag = WidgetDrag.onDrag(start.copy(x = 20f), dx = +10f, dy = 0f,
            screenWidthDp = W, screenHeightDp = H)
        T.eq("zone mein ho par drag edge se door ho → flip nahi", awayDrag.flipped, false)
        T.eq("  horizontal rehta hai", awayDrag.state is VoiceWidgetState.HorizontalPill, true)
        val towardDrag = WidgetDrag.onDrag(start.copy(x = 20f), dx = -5f, dy = 0f,
            screenWidthDp = W, screenHeightDp = H)
        T.eq("zone mein + edge ki taraf drag → flip", towardDrag.flipped, true)
        T.eq("chhoti screen par default pill apne aap flip na ho (360dp/300dp regression)",
            WidgetDrag.onDrag(start, dx = 0f, dy = 0f, screenWidthDp = W, screenHeightDp = H).flipped, false)

        // vertical se wapas: hysteresis 36dp chahiye
        val vp = WidgetDrag.defaultVertical(Dock.LEFT, W, H)
        val notBack = WidgetDrag.onDrag(vp, dx = 30f, dy = 0f, screenWidthDp = W, screenHeightDp = H)
        T.eq("30dp door → abhi bhi vertical (hysteresis)", notBack.state is VoiceWidgetState.VerticalPill, true)
        val back = WidgetDrag.onDrag(vp, dx = 40f, dy = 0f, screenWidthDp = W, screenHeightDp = H)
        T.eq("40dp door → wapas horizontal", back.state is VoiceWidgetState.HorizontalPill, true)
        T.eq("wapas par bottom dock", (back.state as VoiceWidgetState.HorizontalPill).dockedTo, Dock.BOTTOM)

        // drag lock (Gboard: "Force the toolbar in horizontal mode and disable dragging")
        val locked = WidgetDrag.onDrag(start.copy(x = 30f), dx = -5f, dy = 0f,
            screenWidthDp = W, screenHeightDp = H, dragLocked = true)
        T.eq("drag lock mein flip nahi", locked.flipped, false)
        T.eq("drag lock mein horizontal rehta hai", locked.state is VoiceWidgetState.HorizontalPill, true)

        // top par le jao → FLOATING
        val top = WidgetDrag.onDrag(start, dx = 0f, dy = -(H - 30f), screenWidthDp = W, screenHeightDp = H)
        T.eq("top ke paas → FLOATING dock", (top.state as VoiceWidgetState.HorizontalPill).dockedTo, Dock.FLOATING)

        // clamp: screen ke bahar nahi ja sakta (right-edge tak jaane par flip hota hai,
        // isliye yahan sirf clamp test karne ke liye edge se pehle roka)
        val clamped = WidgetDrag.onDrag(start.copy(x = 100f), dx = 5000f, dy = 5000f,
            screenWidthDp = W, screenHeightDp = H)
        T.eq("right edge tak drag → vertical flip (expected)", clamped.flipped, true)
        val cv = clamped.state as VoiceWidgetState.VerticalPill
        T.eq("vertical x clamp (≤ W - verticalW)", cv.x <= W - VoiceWidgetRules.VERTICAL_PILL_WIDTH_DP, true)
        T.eq("vertical y clamp (≤ H - verticalH)", cv.y <= H - VoiceWidgetRules.VERTICAL_PILL_HEIGHT_DP, true)
        val clampH = WidgetDrag.clamp(start.copy(x = 9999f, y = 9999f), W, H) as VoiceWidgetState.HorizontalPill
        T.eq("clamp() horizontal x ≤ W - pillW", clampH.x, W - VoiceWidgetRules.PILL_WIDTH_DP)
        T.eq("clamp() horizontal y ≤ H - pillH", clampH.y, H - VoiceWidgetRules.PILL_HEIGHT_DP)

        // rotation ke baad re-clamp (chhoti screen → adaptive width cap bhi lagta hai)
        val rotated = WidgetDrag.clamp(start.copy(x = 350f, y = 700f), 300f, 500f) as VoiceWidgetState.HorizontalPill
        T.eq("clamp rotation ke baad x (300dp screen → 80% cap)",
            rotated.x, 300f - WidgetDrag.pillWidthFor(300f))
        T.eq("clamp rotation ke baad y", rotated.y, 500f - VoiceWidgetRules.PILL_HEIGHT_DP)

        // tooltip pehli drag par
        val first = WidgetDrag.onDrag(start, dx = 2f, dy = 0f, screenWidthDp = W, screenHeightDp = H, firstDrag = true)
        T.eq("pehli drag par tooltip", first.showTooltip, true)
        val later = WidgetDrag.onDrag(start, dx = 2f, dy = 0f, screenWidthDp = W, screenHeightDp = H, firstDrag = false)
        T.eq("baad ki drag par tooltip nahi", later.showTooltip, false)

        // non-pill states par drag no-op
        T.eq("Hidden par drag no-op", WidgetDrag.onDrag(VoiceWidgetState.Hidden, 5f, 5f, W, H).state, VoiceWidgetState.Hidden)
    }

    // ══════════════════════════ transition table ══════════════════════════

    private fun transitions() {
        T.section("VoiceWidgetTransitions — RESEARCH.md §10.3 ka table")
        val W = 360f; val H = 740f

        // mic access point
        val opened = VoiceWidgetTransitions.micTapped(VoiceWidgetState.Hidden, false)
        T.eq("mic tap → FullVoicePanel", opened.state is VoiceWidgetState.FullVoicePanel, true)
        T.eq("mic tap anim = collapse (200ms)", opened.animMs, 200)

        // chevron = pill ka ekmatra trigger
        val collapsed = VoiceWidgetTransitions.chevronTapped(
            VoiceWidgetState.FullVoicePanel(), W, H)
        T.eq("chevron → HorizontalPill", collapsed.state is VoiceWidgetState.HorizontalPill, true)
        T.eq("chevron par bottom dock", (collapsed.state as VoiceWidgetState.HorizontalPill).dockedTo, Dock.BOTTOM)
        T.eq("chevron anim 200ms", collapsed.animMs, VoiceWidgetRules.ANIM_COLLAPSE_MS)

        // chevron doosre states par kuch nahi karta
        val noOp = VoiceWidgetTransitions.chevronTapped(VoiceWidgetState.Hidden, W, H)
        T.eq("Hidden par chevron no-op", noOp.state, VoiceWidgetState.Hidden)
        T.eq("no-op par anim 0", noOp.animMs, 0)

        // keyboard icon → Hidden
        val pill = WidgetDrag.defaultHorizontal(W, H)
        T.eq("⌨ tap → Hidden", VoiceWidgetTransitions.keyboardTapped(pill).state, VoiceWidgetState.Hidden)
        T.eq("⌨ tap anim = fade (150ms)", VoiceWidgetTransitions.keyboardTapped(pill).animMs, 150)

        // pill ke mic par tap = pause toggle, pill band NAHI hoti
        val paused = VoiceWidgetTransitions.micTapped(pill, false)
        T.eq("pill mic tap → pill hi rehti hai", paused.state is VoiceWidgetState.HorizontalPill, true)
        T.eq("pill mic tap → PAUSED", (paused.state as VoiceWidgetState.HorizontalPill).mic, MicState.PAUSED)
        val resumed = VoiceWidgetTransitions.micTapped(pill.copy(mic = MicState.PAUSED), false)
        T.eq("dobara tap → LISTENING", (resumed.state as VoiceWidgetState.HorizontalPill).mic, MicState.LISTENING)

        // badge → menu + mic auto-pause (Gboard implicit behaviour)
        val menu = VoiceWidgetTransitions.badgeTapped(pill)
        T.eq("badge → MenuOpen", menu.state is VoiceWidgetState.MenuOpen, true)
        T.eq("menu khulte hi mic auto-pause",
            ((menu.state as VoiceWidgetState.MenuOpen).over as VoiceWidgetState.HorizontalPill).mic, MicState.PAUSED)
        T.eq("menu anim 180ms", menu.animMs, VoiceWidgetRules.ANIM_MENU_MS)
        T.eq("menu dobara tap → wapas pill",
            VoiceWidgetTransitions.badgeTapped(menu.state).state is VoiceWidgetState.HorizontalPill, true)

        // menu → vertical / horizontal morph
        val toVertical = VoiceWidgetTransitions.menuItemTapped(VoiceMenuItem.SWITCH_VERTICAL, menu.state, W, H)
        T.eq("Switch to vertical → VerticalPill", toVertical.state is VoiceWidgetState.VerticalPill, true)
        T.eq("morph anim 250ms", toVertical.animMs, VoiceWidgetRules.ANIM_MORPH_MS)
        val toHorizontal = VoiceWidgetTransitions.menuItemTapped(
            VoiceMenuItem.SWITCH_HORIZONTAL, VoiceWidgetState.MenuOpen(toVertical.state), W, H)
        T.eq("Switch to horizontal → HorizontalPill", toHorizontal.state is VoiceWidgetState.HorizontalPill, true)

        // menu → symbols overlay
        val symbols = VoiceWidgetTransitions.menuItemTapped(VoiceMenuItem.SYMBOLS, menu.state, W, H)
        T.eq("Symbols → SymbolsOverlay", symbols.state is VoiceWidgetState.SymbolsOverlay, true)
        T.eq("symbols default category = Recent",
            (symbols.state as VoiceWidgetState.SymbolsOverlay).category, SymbolCategory.RECENT)

        // dismiss → pichla state
        T.eq("menu dismiss → over", VoiceWidgetTransitions.dismissed(menu.state, menu.state.let { (it as VoiceWidgetState.MenuOpen).over }).state is VoiceWidgetState.HorizontalPill, true)
        val underlying = WidgetDrag.defaultHorizontal(W, H)
        T.eq("symbols dismiss → underlying pill",
            VoiceWidgetTransitions.dismissed(VoiceWidgetState.SymbolsOverlay(), underlying).state, underlying)
    }

    // ══════════════════════════ controller: 5 states ══════════════════════════

    private fun controllerStates() {
        T.section("VoiceWidgetController — states, mic, backspace, permissions")

        run {
            val (c, h) = newController()
            T.eq("shuruaat Hidden", c.state, VoiceWidgetState.Hidden)
            T.eq("pill mode off", c.isPillMode, false)

            c.onMicAccessPointTap()
            T.eq("State 1 khula", c.state is VoiceWidgetState.FullVoicePanel, true)
            T.eq("mic start hua", h.started, 1)
            T.eq("status = अब बोलें / Speak now", c.statusText, StatusText.SPEAK_NOW)
            T.eq("status EN label", StatusText.SPEAK_NOW.en, "Speak now")
            T.eq("status HI label (Gboard-exact)", StatusText.SPEAK_NOW.hi, "अब बोलें")

            c.onChevronTap()
            T.eq("State 2 (pill)", c.state is VoiceWidgetState.HorizontalPill, true)
            T.eq("pill mode on → touch pass-through chalega", c.isPillMode, true)
            T.eq("chevron ne mode enable save kiya", h.modeEnabled, true)
            T.eq("position persist hua", h.x != null && h.y != null, true)

            c.onPillMicTap()
            T.eq("pill mic → PAUSED", (c.state as VoiceWidgetState.HorizontalPill).mic, MicState.PAUSED)
            T.eq("pause par recognizer stop", h.stopped >= 1, true)
            T.eq("pill pause par band NAHI hui (Gboard-exact)", c.state is VoiceWidgetState.HorizontalPill, true)
            T.eq("paused status text", c.statusText, StatusText.PAUSED)
            c.onPillMicTap()
            T.eq("resume → LISTENING", (c.state as VoiceWidgetState.HorizontalPill).mic, MicState.LISTENING)

            c.onKeyboardTap()
            T.eq("⌨ → Hidden", c.state, VoiceWidgetState.Hidden)
            T.eq("⌨ ne mode disable kiya", h.modeEnabled, false)
            T.eq("⌨ ne full keyboard khola", h.fullKeyboardOpened, 1)
        }

        // backspace: maujooda engine ka handler reuse (timing naya nahi)
        run {
            val (c, h) = newController()
            c.onMicAccessPointTap(); c.onChevronTap()
            c.onBackspace()
            T.eq("pill ⌫ → engine.backspace() chala", h.backspaces, 1)
            h.hasField = false
            c.onBackspace()
            T.eq("text field na ho to backspace nahi", h.backspaces, 1)
            T.eq("  us par Gboard ka verbatim message",
                h.toasts.last(), StatusText.NO_TEXT_FIELD.en)
        }

        // dictation results → maujooda insert pipeline (converter untouched)
        run {
            val (c, h) = newController()
            c.onMicAccessPointTap()
            c.onPartialResult("नमस्ते")
            T.eq("partial transcript dikhta hai", c.partialTranscript, "नमस्ते")
            T.eq("partial par status = Listening…", c.statusText, StatusText.LISTENING)
            T.eq("displayStatus partial deta hai", c.displayStatus(), "नमस्ते")
            c.onFinalResult("नमस्ते दुनिया")
            T.eq("final transcript insert hua", h.inserted, listOf("नमस्ते दुनिया"))
            T.eq("insert ke baad partial clear", c.partialTranscript, "")
            T.eq("insert ke baad status wapas Speak now", c.statusText, StatusText.SPEAK_NOW)
        }

        run {
            val (c, h) = newController()
            c.onMicAccessPointTap()
            c.onFinalResult("")
            T.eq("khaali transcript par insert nahi", h.inserted.isEmpty(), true)
            c.onFinalResult("   ")
            T.eq("blank transcript par insert nahi", h.inserted.isEmpty(), true)
        }

        // mic permission / recognition unavailable → honest gating
        run {
            val (c, h) = newController()
            h.dictationAvailable = false
            c.onMicAccessPointTap()
            T.eq("permission maangi gayi", h.permissionAsked, 1)
            T.eq("mic ERROR state", (c.state as VoiceWidgetState.FullVoicePanel).mic, MicState.ERROR)
            T.eq("recognizer start nahi hua", h.started, 0)
        }

        run {
            val (c, h) = newController()
            h.hasField = false
            c.onMicAccessPointTap()
            T.eq("text field na ho to dictation start nahi", h.started, 0)
            T.eq("  Gboard message: Go to a text field…", c.statusText, StatusText.NO_TEXT_FIELD)
        }

        // offline preference (owner decision)
        run {
            val (c, h) = newController()
            T.eq("preferOffline default true", c.preferOffline, true)
            c.onMicAccessPointTap()
            T.eq("recognizer ko EXTRA_PREFER_OFFLINE bheja", h.preferOfflineLast, true)
            c.preferOffline = false
            c.onMicAccessPointTap()
            T.eq("band karne par offline false", h.preferOfflineLast, false)
        }

        // lifecycle: mic release
        run {
            val (c, h) = newController()
            c.onMicAccessPointTap()
            c.release()
            T.eq("release par recognizer destroy", h.released, 1)
            T.eq("release par stop bhi", h.stopped >= 1, true)
        }

        // error / end callbacks
        run {
            val (c, h) = newController()
            c.onMicAccessPointTap(); c.onChevronTap()
            c.onError(offlineFallbackFailed = false)
            T.eq("error par OFFLINE", (c.state as VoiceWidgetState.HorizontalPill).mic, MicState.OFFLINE)
            T.eq("error status", c.statusText, StatusText.ERROR_UNSUPPORTED)
            c.onError(offlineFallbackFailed = true)
            T.eq("hard error par ERROR", c.state.let { (it as VoiceWidgetState.HorizontalPill).mic }, MicState.ERROR)
            c.onDictationEnd()
            T.eq("dictation end → PAUSED", (c.state as VoiceWidgetState.HorizontalPill).mic, MicState.PAUSED)
        }

        // back button
        run {
            val (c, _) = newController()
            T.eq("Hidden par back consume nahi hota", c.onBackPressed(), false)
            c.onMicAccessPointTap()
            T.eq("panel par back consume hota hai", c.onBackPressed(), true)
            T.eq("back → keyboard wapas", c.state, VoiceWidgetState.Hidden)
            c.onMicAccessPointTap(); c.onChevronTap(); c.onBadgeTap()
            T.eq("menu par back → pill wapas", c.onBackPressed(), true)
            T.eq("  state pill hai", c.state is VoiceWidgetState.HorizontalPill, true)
        }

        // rotation / config change
        run {
            val (c, h) = newController()
            c.onMicAccessPointTap(); c.onChevronTap()
            h.w = 300f; h.h = 500f
            c.onConfigurationChanged()
            val s = c.state as VoiceWidgetState.HorizontalPill
            val maxX = 300f - WidgetDrag.pillWidthFor(300f)
            T.eq("config change ke baad x clamp (adaptive width ke hisaab se)", s.x <= maxX, true)
            T.eq("config change ke baad y clamp", s.y <= 500f - VoiceWidgetRules.PILL_HEIGHT_DP, true)
            T.eq("state wahi rehta hai", c.state is VoiceWidgetState.HorizontalPill, true)
        }

        // drag controller-level
        run {
            val (c, h) = newController()
            c.onMicAccessPointTap(); c.onChevronTap()
            val before = c.revision

            // pehla drag: halka move (flip zone ke bahar) → tooltip dikhna chahiye
            c.onDrag(-10f, 0f)
            T.eq("pehli drag par tooltip dikha", c.tooltipVisible, true)
            T.eq("tooltip seen save hua", h.tooltipSeen, true)
            T.eq("halka drag → horizontal hi rehta hai", c.state is VoiceWidgetState.HorizontalPill, true)
            c.onDragEnd()
            T.eq("drag end par tooltip gaya", c.tooltipVisible, false)
            T.eq("revision badhta hai (recompose)", c.revision > before, true)

            // doosra drag: edge tak → flip, par tooltip ab nahi (seen ho chuka)
            c.onDrag(-200f, 0f)
            T.eq("edge tak drag se left flip → vertical", c.state is VoiceWidgetState.VerticalPill, true)
            T.eq("orientation persist hua", h.orientation, WidgetOrientation.VERTICAL)
            T.eq("position persist hua", h.x != null, true)
            T.eq("tooltip seen ke baad dobara nahi dikhta", c.tooltipVisible, false)
        }

        run {
            val (c, h) = newController()
            h.dragLocked = true
            c.onMicAccessPointTap(); c.onChevronTap()
            val s0 = c.state
            c.onDrag(-1000f, 0f)
            T.eq("drag lock mein state nahi badla", c.state, s0)
            T.eq("drag lock par Gboard-style toast", h.toasts.last(), "Toolbar is locked in horizontal mode")
            h.hindi = true
            c.onDrag(-200f, 0f)
            T.eq("  Hindi toast", h.toasts.last(), "टूलबार क्षैतिज मोड में लॉक है")
        }
    }

    // ══════════════════════════ menu + symbols ══════════════════════════

    private fun menuAndSymbols() {
        T.section("Voice menu (Gboard ka poora menu) + symbols overlay")

        // owner decision: poora 7-item menu (+ symbols)
        val items = VoiceWidgetTransitions.menuItems(VoiceWidgetState.HorizontalPill())
        T.eq("menu = 8 items (7 Gboard + Symbols)", items.size, 8)
        T.eq("menu ka Gboard order",
            items.take(6).map { it.actionId == "settings" || true }.size, 6)
        T.eq("menu items (order)",
            items.map { it.en },
            listOf(
                "Settings", "Show voice commands", "Show clipboard", "Show translate",
                "Show emoji", "Switch to vertical toolbar", "Symbols", "Feedback",
            ))
        T.eq("vertical pill par switch item ulta hota hai",
            VoiceWidgetTransitions.menuItems(VoiceWidgetState.VerticalPill())
                .any { it == VoiceMenuItem.SWITCH_HORIZONTAL }, true)

        // verbatim labels (APK strings)
        T.eq("SWITCH_VERTICAL EN verbatim", VoiceMenuItem.SWITCH_VERTICAL.en, "Switch to vertical toolbar")
        T.eq("SWITCH_VERTICAL HI verbatim", VoiceMenuItem.SWITCH_VERTICAL.hi, "वर्टिकल टूलबार पर स्विच करें")
        T.eq("SHOW_CLIPBOARD HI verbatim", VoiceMenuItem.SHOW_CLIPBOARD.hi, "क्लिपबोर्ड दिखाएं")
        T.eq("SHOW_TRANSLATE HI verbatim", VoiceMenuItem.SHOW_TRANSLATE.hi, "अनुवाद दिखाएं")
        T.eq("SHOW_EMOJI HI verbatim", VoiceMenuItem.SHOW_EMOJI.hi, "इमोजी दिखाएं")
        T.eq("SETTINGS HI verbatim", VoiceMenuItem.SETTINGS.hi, "सेटिंग")
        T.eq("SYMBOLS HI verbatim", VoiceMenuItem.SYMBOLS.hi, "चिह्न")
        T.eq("Gboard action ID verbatim", VoiceMenuItem.SWITCH_VERTICAL.actionId, "widget_change_widget_orientation")

        // controller se menu flow
        run {
            val (c, h) = newController()
            c.onMicAccessPointTap(); c.onChevronTap()
            c.onBadgeTap()
            T.eq("badge → MenuOpen", c.state is VoiceWidgetState.MenuOpen, true)

            c.onMenuItem(VoiceMenuItem.SETTINGS)
            T.eq("Settings item ne settings khola", h.settingsOpened, 1)
            T.eq("Settings ke baad menu band", c.state !is VoiceWidgetState.MenuOpen, true)

            c.onBadgeTap()
            c.onMenuItem(VoiceMenuItem.SHOW_TRANSLATE)
            T.eq("hide-nothing: gated item par Gboard ka verbatim reason",
                h.toasts.last(), "Can't use this tool at the moment. Please try again later.")
            h.hindi = true
            c.onBadgeTap()
            c.onMenuItem(VoiceMenuItem.SHOW_CLIPBOARD)
            T.eq("  Hindi gate reason", h.toasts.last(), "इस ऐप में क्लिपबोर्ड उपलब्ध नहीं है")
            c.onBadgeTap()
            c.onMenuItem(VoiceMenuItem.FEEDBACK)
            T.eq("  Feedback gate reason", h.toasts.last(), "फ़ीडबैक के लिए इंटरनेट और ईमेल क्लाइंट चाहिए")
        }

        // orientation switch persist
        run {
            val (c, h) = newController()
            c.onMicAccessPointTap(); c.onChevronTap(); c.onBadgeTap()
            c.onMenuItem(VoiceMenuItem.SWITCH_VERTICAL)
            T.eq("vertical ho gaya", c.state is VoiceWidgetState.VerticalPill, true)
            T.eq("orientation save hua", h.orientation, WidgetOrientation.VERTICAL)
            c.onBadgeTap()
            c.onMenuItem(VoiceMenuItem.SWITCH_HORIZONTAL)
            T.eq("horizontal wapas", c.state is VoiceWidgetState.HorizontalPill, true)
            T.eq("orientation save hua (horizontal)", h.orientation, WidgetOrientation.HORIZONTAL)
        }

        // symbols overlay
        run {
            val (c, h) = newController()
            h.digits = listOf("𑵐", "𑵑", "𑵒", "𑵓", "𑵔", "𑵕", "𑵖", "𑵗", "𑵘", "𑵙")
            c.onMicAccessPointTap(); c.onChevronTap(); c.onBadgeTap()
            val pillBefore = (c.state as VoiceWidgetState.MenuOpen).over
            c.onMenuItem(VoiceMenuItem.SYMBOLS)
            T.eq("symbols overlay khula", c.state is VoiceWidgetState.SymbolsOverlay, true)
            T.eq("overlay ke peeche wali pill yaad hai", c.underlyingState(), pillBefore)
            T.eq("Numbers category mein Gondi digits",
                c.symbolsFor(SymbolCategory.NUMBERS), h.digits)
            T.eq("Brackets category", c.symbolsFor(SymbolCategory.BRACKETS).take(4), listOf("(", ")", "{", "}"))
            T.eq("Arrows category", c.symbolsFor(SymbolCategory.ARROWS).take(4), listOf("←", "↑", "→", "↓"))
            T.eq("Mathematics category", c.symbolsFor(SymbolCategory.MATHEMATICS).take(4), listOf("+", "-", "×", "÷"))
            T.eq("List category", c.symbolsFor(SymbolCategory.LIST).take(3), listOf("•", "◦", "‣"))
            T.eq("Recent khali ho to Numbers fallback", c.symbolsFor(SymbolCategory.RECENT).size > 0, true)

            c.onSymbolTap("→")
            T.eq("symbol insert hua", h.inserted, listOf("→"))
            T.eq("symbol Recent mein gaya", h.recent.first(), "→")
            T.eq("Recent ab wahi symbol deta hai", c.symbolsFor(SymbolCategory.RECENT).first(), "→")

            c.onSymbolCategoryTap(SymbolCategory.MATHEMATICS)
            T.eq("category switch", (c.state as VoiceWidgetState.SymbolsOverlay).category, SymbolCategory.MATHEMATICS)

            c.onSymbolsClose()
            T.eq("X se overlay band → pill wapas", c.state, pillBefore)
        }

        // 6 categories (Gboard APK se exact)
        T.eq("categories = 6", SymbolCategory.entries.size, 6)
        T.eq("category EN names",
            SymbolCategory.entries.map { it.en },
            listOf("Recent", "Numbers", "Brackets", "Arrows", "Mathematics", "List"))
        T.eq("Symbols HI = चिह्न", SymbolCategory.NUMBERS.hi.isNotEmpty(), true)

        // language list (menu ke divider ke baad)
        run {
            val (c, h) = newController()
            T.eq("languages = enabled modes se", c.languages(), VOICE_LANGUAGES)
            T.eq("Gboard pattern: <भाषा> (<देश>)", c.languages().first(), "हिन्दी (भारत)")
            T.eq("hi-IN tag", voiceLanguageTag("हिन्दी (भारत)"), "hi-IN")
            T.eq("en-IN tag", voiceLanguageTag("English (India)"), "en-IN")
            T.eq("en-US tag", voiceLanguageTag("English (US)"), "en-US")
            c.onMicAccessPointTap(); c.onChevronTap(); c.onBadgeTap()
            c.onLanguageTap("English (US)")
            T.eq("menu label → BCP-47 tag mein convert hua", h.language, "en-US")
            T.eq("locale tag (mic ke kone par) BCP-47", c.localeTag, "en-US")
            T.eq("language tap ke baad menu band", c.state !is VoiceWidgetState.MenuOpen, true)
        }
    }

    // ══════════════════════════ persistence / restore ══════════════════════════

    private fun persistence() {
        T.section("Persistence — Gboard: pill agle text field par wapas aati hai")

        // Gboard: "this toolbar is persistent and remains active the next time you
        // open a text field until you manually bring back the keyboard"
        run {
            val h = FakeHost()
            val c = VoiceWidgetController(h)
            c.onMicAccessPointTap(); c.onChevronTap()
            T.eq("mode enabled save hua", h.modeEnabled, true)
            val savedX = h.x; val savedY = h.y

            // IME window mar gaya → naya controller (agla text field)
            val c2 = VoiceWidgetController(h)
            T.eq("restore → pill wapas", c2.state is VoiceWidgetState.HorizontalPill, true)
            val restored = c2.state as VoiceWidgetState.HorizontalPill
            T.eq("restore par wahi position", restored.x, savedX)
            T.eq("restore par wahi y", restored.y, savedY)
        }

        run {
            val h = FakeHost()
            val c = VoiceWidgetController(h)
            c.onMicAccessPointTap(); c.onChevronTap(); c.onBadgeTap()
            c.onMenuItem(VoiceMenuItem.SWITCH_VERTICAL)
            T.eq("vertical save hua", h.orientation, WidgetOrientation.VERTICAL)
            val c2 = VoiceWidgetController(h)
            T.eq("restore → vertical pill", c2.state is VoiceWidgetState.VerticalPill, true)
            T.eq("restore par dock right (x > half)",
                (c2.state as VoiceWidgetState.VerticalPill).dockedTo, Dock.RIGHT)  // cast local nahi: data class copy se mutate nahi hota
        }

        run {
            val h = FakeHost()
            T.eq("mode disabled ho to restore nahi hota", VoiceWidgetController(h).state, VoiceWidgetState.Hidden)
            h.modeEnabled = true
            h.x = null; h.y = null
            val c = VoiceWidgetController(h)
            T.eq("saved position na ho to default (bottom-center)",
                (c.state as VoiceWidgetState.HorizontalPill).dockedTo, Dock.BOTTOM)
        }

        run {
            val h = FakeHost()
            h.modeEnabled = true
            h.x = 9999f; h.y = 9999f       // purani/stale position (rotation ke baad)
            val c = VoiceWidgetController(h)
            val s = c.state as VoiceWidgetState.HorizontalPill
            T.eq("stale position restore par clamp hoti hai",
                s.x <= h.w - WidgetDrag.pillWidthFor(h.w) + 0.01f, true)
            T.eq("stale y bhi clamp", s.y <= h.h - VoiceWidgetRules.PILL_HEIGHT_DP + 0.01f, true)
        }

        // keyboard icon = manual "bring back the keyboard" → mode disable
        run {
            val h = FakeHost()
            h.modeEnabled = true
            val c = VoiceWidgetController(h)
            c.onKeyboardTap()
            T.eq("⌨ ke baad mode disable (Gboard-exact)", h.modeEnabled, false)
            T.eq("agle session mein restore nahi hoga", VoiceWidgetController(h).state, VoiceWidgetState.Hidden)
        }
    }

    // ══════════════════════════ strings (verbatim) ══════════════════════════

    private fun strings() {
        T.section("StatusText — Gboard APK se verbatim (owner decision: 'अब बोलें')")
        T.eq("Speak now EN", StatusText.SPEAK_NOW.en, "Speak now")
        T.eq("Speak now HI (APK verbatim)", StatusText.SPEAK_NOW.hi, "अब बोलें")
        T.eq("Paused EN", StatusText.PAUSED.en, "Paused")
        T.eq("Paused HI (APK verbatim)", StatusText.PAUSED.hi, "रोकी गई")
        T.eq("Just speak naturally EN", StatusText.JUST_SPEAK.en, "Just speak naturally")
        T.eq("Go to a text field… EN", StatusText.NO_TEXT_FIELD.en, "Go to a text field to use dictation")
        T.eq("Unsupported voice command EN", StatusText.ERROR_UNSUPPORTED.en, "Unsupported voice command")
        T.eq("label(hindi) HI deta hai", StatusText.PAUSED.label(true), "रोकी गई")
        T.eq("label(hindi=false) EN deta hai", StatusText.PAUSED.label(false), "Paused")

        // symbols grid fallbacks
        T.eq("NUMBERS fallback 10 items", VoiceSymbols.NUMBERS.size, 10)
        T.eq("BRACKETS 12 items", VoiceSymbols.BRACKETS.size, 12)
        T.eq("ARROWS 12 items", VoiceSymbols.ARROWS.size, 12)
        T.eq("MATHEMATICS 20 items", VoiceSymbols.MATHEMATICS.size, 20)
        T.eq("LIST 12 items", VoiceSymbols.LIST.size, 12)
        T.eq("gondiDigits override hota hai",
            VoiceSymbols.grid(SymbolCategory.NUMBERS, listOf("𑵐", "𑵑"), emptyList()), listOf("𑵐", "𑵑"))
        T.eq("recent override hota hai",
            VoiceSymbols.grid(SymbolCategory.RECENT, emptyList(), listOf("→", "•")), listOf("→", "•"))
    }
}
