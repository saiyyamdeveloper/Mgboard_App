# voice-pill-project → Mgboard implementation

**Status: ✅ implemented** (`app/src/main/kotlin/com/mgboard/keyboard/voice/`, 219 JVM assertions)

Research docs (`RESEARCH.md`, `APK-EVIDENCE.md`, `SPEC-vs-GBOARD.md`) ke decisions ab
code mein hain. Yeh file batati hai ki **kya bana, kahan bana, aur kahan research se
jaan-boojh kar hatke kiya gaya**.

---

## 1. Owner ke 4 decisions (implement se pehle confirm hue)

| # | Decision | Chuna gaya | Asar |
|---|---|---|---|
| 1 | Window strategy | **(A) IME-window widget** | Gboard-exact, **zero permission**. `SYSTEM_ALERT_WINDOW` nahi, foreground service nahi, Play-policy risk nahi. Text insert seedha `InputConnection` se. Mic "while-in-use" chalta hai |
| 2 | State 1 status text | **"अब बोलें" / "Speak now"** (Gboard-exact) | APK verbatim; "बोल रहा हूं" APK mein 0 matches tha |
| 3 | Menu items | **Gboard ka poora menu** (7 + Symbols = 8) + language list | hide-nothing: backend-less items bhi dikhte hain, Gboard ka verbatim reason dete hain |
| 4 | Recognition | **offline prefer, fallback online** | `EXTRA_PREFER_OFFLINE=true`; `ERROR_NO_MATCH` par ek baar online retry |

Baaki research-recommended defaults khud apply hue: Kotlin + Compose, edge-flip **28dp**
+ hysteresis **8dp**, animations **150–250ms**, symbols overlay ke peeche pill visible,
pehli drag par tooltip, gesture-inset dock safety.

---

## 2. Files

| File | Kaam | Pure Kotlin? |
|---|---|---|
| `voice/VoiceWidgetState.kt` | 5 states (sealed), `MicState`, `Dock`, `WidgetOrientation`, `StatusText` + `VoiceMenuItem` + `SymbolCategory` (Gboard verbatim EN/HI), `VoiceSymbols` grid, `VOICE_LANGUAGES` | ✅ |
| `voice/VoiceWidgetRules.kt` | saare constants (thresholds, animations, dp sizes, Gboard ke pref key names), `WidgetDrag` (drag/flip/dock/clamp), `VoiceWidgetTransitions` (§10.3 ka transition table) | ✅ |
| `voice/VoiceWidgetController.kt` | **brain** — state machine, mic start/pause/stop, menu, symbols, drag, persistence, error gating; `VoiceHost` interface ke through platform se baat | ✅ |
| `voice/VoiceAndroid.kt` | `WidgetPositionStore` (SharedPreferences, Gboard key names) + `DictationBridge` (`SpeechRecognizer`, offline-preferred, fallback, release) | ❌ Android |
| `voice/ImeVoiceHost.kt` | `VoiceHost` ka real implementation — IME/model/engine/prefs se wire | ❌ Android |
| `voice/VoiceWidgetUi.kt` | Compose: panel, horizontal/vertical pill, menu, symbols overlay, mic circle, tooltip, drag gesture | ❌ Compose |
| `ime/MgBoardIme.kt` | `onComputeInsets()` touch pass-through + `setIsFullscreenMode` + lifecycle wiring | ❌ Android |
| `ui/Preview.kt` | `PreviewVoiceHost` — launcher preview mein poora voice UI bina device ke | ❌ Compose |

Tests: `app/src/test/kotlin/com/mgboard/keyboard/VoiceTests.kt` — **219 assertions**
(rules, drag/flip, transition table, controller states, menu/symbols, persistence,
verbatim strings).

---

## 3. Gboard-exact jo implement hua

- **5 states** — panel / horizontal pill / menu / symbols overlay / vertical pill
- **Pill order** (teardown se): `badge | ⌨ | status | ⌫ | 🎤` (mic ke kone par chhota
  language tag = display-only)
- **Vertical pill order**: `🎤 | ⌫ | ⌨ | badge` (ऊपर→नीचे)
- **˅˅ chevron = pill dikhne ka ekmatra trigger**
- **⌨ tap = pill poori tarah dismiss** + mode pref off
- **pill ke mic par tap = pause/resume**, pill band **nahi** hoti
- **menu khulte hi mic auto-pause** (Gboard mein implicit, yahan explicit)
- **Persistence**: `widget_x_position` / `widget_y_position` /
  `widget_change_widget_orientation` — agla text field khulte hi pill wapas
  (*"persistent … until you manually bring back the keyboard"*)
- **Drag lock**: "Force the toolbar in horizontal mode and disable dragging"
- **Tooltip**: "Hold and drag to move toolbar" — pehli drag par, phir kabhi nahi
- **Touch pass-through**: `TOUCHABLE_INSETS_REGION` + `touchableRegion = pill rect`;
  `contentTopInsets = pill.bottom`, `visibleTopInsets = window height` (app content pan
  nahi hota)
- **Mic release**: `onFinishInputView()` / `onWindowHidden()` / `onDestroy()` par
  `SpeechRecognizer.destroy()` (battery rule §9.5)
- **Symbols categories**: `Recent · Numbers · Brackets · Arrows · Mathematics · List`
  (APK se exact), Numbers category mein **Gondi digits** (U+11D50–U+11D59)
- **Language list**: `<भाषा> (<देश>)` pattern, enabled modes se (hardcoded recognizer
  list nahi), active par ✓ checkmark

---

## 4. Standing rules jo follow hue (owner ke)

| Rule | Kaise |
|---|---|
| **Additive — keyboard ko chhuna nahi** | `voice/` alag package; `KeyboardScreen`/`KeyGestures`/`TypingEngine`/converter mein koi behaviour change nahi. Toolbar ke 🎤 par sirf `host.onMicTap()` juda |
| **Backspace 400/70ms reuse** | pill ka ⌫ wahi `KeyGestures.pointerInputFor(repeat = true)` use karta hai jo keyboard ke ⌫ par hai → `KeyTiming.REPEAT_INITIAL_DELAY_MS` (400) / `REPEAT_INTERVAL_MS` (70). Naya Handler/Runnable **nahi** banaya |
| **Text insert maujooda pipeline se** | `host.insertText()` → `KeyboardModel.insertBulk()` → Gondi mode mein `DevToGondi.convert()`. Converter untouched |
| **dp-based sizing** | `VoiceWidgetRules` ke saare sizes dp mein; Compose `animateDpAsState`; pill rect px mein sirf `onComputeInsets()` ke liye convert hota hai |
| **Gesture-inset safety** | `GestureNavPaddingController.isKeyboardDockedAtBottom` ab `!voice.isPillMode` bhi check karta hai → pill mode mein keyboard-bottom-gap nahi lagti (Gboard: gap sirf "normal mode") |
| **hide-nothing** | gated menu items (clipboard/translate/emoji/feedback) dikhte hain + verbatim reason; mic permission na ho to honest message + system settings intent |
| **Fonts/layout unchanged** | pill ke text par bhi wahi bundled `MgondiFont` (Gondi transcript sahi dikhe) |

---

## 5. Research se jaan-boojh kar hatke (2 jagah) — dono regression-tested

### (a) Edge-flip ab **direction-gated** hai
Research ka rule tha "pill edge ke ≤28dp andar ho → vertical flip". Implement karte waqt
regression mila: **360dp screen par 300dp chaudi pill default position mein hi right-edge
zone ke andar hoti hai**, isliye bina drag kiye flip ho jaati.

Do fix:
1. flip sirf tab jab user **edge ki taraf** drag kar raha ho (`dx < 0` left ke liye,
   `dx > 0` right ke liye)
2. pill ki chaudai adaptive cap: `min(300dp, screenWidth × 0.80)`

Gboard ka behaviour isse match karta hai ("Hold and drag to move toolbar" — drag zaroori
hai). Test: `VoiceTests.dragAndFlip()` mein
*"chhoti screen par default pill apne aap flip na ho (360dp/300dp regression)"*.

### (b) `voice commands` abhi gated hain
Gboard ke "Assistant"-specific smartness (voice commands, writing tools) Pixel-only hai
aur on-device model maangta hai. MgBoard mein menu item **dikhta** hai aur Gboard ka
verbatim reason deta hai — chhupaya nahi gaya.

---

## 6. Test matrix (RESEARCH.md §13) ka status

**States** — ✅ sab automated (JVM): mic→panel, chevron→pill (200ms), ⌨→hidden,
pill-mic→pause (pill rehti hai), badge→menu+auto-pause, menu→vertical/horizontal morph
(250ms), menu→symbols, dismiss→pichla state.

**Drag/flip** — ✅ left/right edge flip, 28dp boundary, hysteresis 36dp, direction gate,
drag lock, top→FLOATING, clamp, rotation re-clamp, tooltip first-drag-only.

**Persistence** — ✅ mode enable/disable, position save/restore, orientation
save/restore, stale position clamp, "manual keyboard = disable".

**Dictation** — ✅ partial→status Listening…, final→insert (existing pipeline), blank
final ignored, permission missing→ERROR + toast, recognition unavailable→toast,
offline preference flag, release par destroy.

**Strings** — ✅ EN + HI verbatim assertions (अब बोलें / रोकی गई / वर्टिकल टूलबार पर
स्विच करें / क्लिपबोर्ड दिखाएं / अनुवाद दिखाएं / इमोजी दिखाएं / सेटिंग / चिह्न).

**Device par manually verify karna hai** (JVM par possible nahi):
- [ ] `onComputeInsets()` ka touch pass-through real apps mein (Chrome/WhatsApp)
- [ ] `setIsFullscreenMode(true)` ke saath pill ki actual position/clip
- [ ] `SpeechRecognizer` ka behaviour offline/online dono par (device-dependent)
- [ ] Foldable/landscape par `VOICE_enable_vertical_widget_*` jaisa gating chahiye ya nahi
- [ ] TalkBack labels + RTL
- [ ] Rotation/fold par window re-measure + pill re-clamp live

---

## 7. IME wiring (kya juda, kya nahi chheda)

```kotlin
// MgBoardIme.onCreate()  ← naya
voiceHost = ImeVoiceHost(this, model, engine, positionStore, ::openSettingsActivity) { tick++ }
voice = VoiceWidgetController(voiceHost)

// onCreateInputView()  ← Compose root ab Box hai
Box(Modifier.fillMaxSize()) {
    if (!voice.isPillMode) KeyboardScreen(model, host, tick)   // keyboard pill mode mein chhupta hai
    VoiceWidgetLayer(voice, tick)
}

// onComputeInsets()  ← naya override (pill mode mein pass-through)
// onStartInputView() ← voice.restoreIfEnabled()  (Gboard persistence)
// onFinishInputView()/onWindowHidden()/onDestroy() ← voice.release() + saveState()
// onBackPressed()    ← voice.onBackPressed() sabse pehle
// onConfigurationChanged() ← voice.onConfigurationChanged() (re-clamp)
```

**Kuch nahi chheda:** `TypingEngine`, `DevToGondi`, `KeyboardData`, `KeyboardLayout`,
`QwertyShift`, `GridMenu`, `SgPrefs` ke existing keys, keyboard ke key gestures.
