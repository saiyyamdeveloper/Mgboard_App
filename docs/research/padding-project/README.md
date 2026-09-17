# 📦 padding-project  (status: SAVED — implement नहीं हुआ) · **Gboard-verified ✅**

**क्या है:** Mgboard keyboard app के लिए *Android gesture-navigation bottom safe-area padding* feature
(Gboard जैसा behavior) — पूरा research + ready-to-use implementation।

**Status:** सिर्फ़ save है। जब owner कहेगा **"padding project implement karo"**, तब इसे Mgboard
codebase में wire करना है।

**🔎 Update (2026-09-17):** असली **Gboard APK 18.3.1-beta** खोलकर इस project को verify किया गया और
4 जगह सुधार करके **Gboard-exact** बना दिया गया। पूरा रिपोर्ट → `GBOARD-VERIFICATION.md`

---

## Files

| File | काम |
|---|---|
| `README.md` | यही file — quick map |
| `GBOARD-VERIFICATION.md` | ⭐ **असली Gboard APK से verification रिपोर्ट** — `normal_mode_keyboard_bottom_gap_*` और `normal_mode_decor_view_stable_inset_bottom_*` keys का सबूत, 12-point तुलना, और क्या-क्या बदला गया |
| `RESEARCH.md` | पूरा deep research: Gboard में यह कैसे काम करता है, AOSP source evidence (file + line सहित), inset types का comparison, mode-detection के सही/गलत तरीके, edge cases, test matrix, sources |
| `kotlin/GestureNavPaddingController.kt` | drop-in Kotlin controller (**Gboard-exact logic**, हिंदी comments) |
| `java/GestureNavPaddingController.java` | बिल्कुल वही Java version |
| `INTEGRATION.md` | `MgboardImeService` में wiring (Kotlin + Java), layout XML, fixed-height keyboard का समाधान, adb debug commands, known pitfalls, test checklist |

---

## Feature का 1-line सार

IME window हमेशा screen के bottom तक जाता है (`setFitInsetsSides(Side.all() & ~Side.BOTTOM)`).
3-button mode में opaque nav bar keys को ढक लेता है → padding दिखती नहीं।
Gesture mode में nav bar transparent (सिर्फ़ pill) → keyboard को **खुद** keys ऊपर उठानी पड़ती हैं,
और उस खाली strip को keyboard के background color से paint करना पड़ता है।

Height हमेशा insets से — **Gboard-exact** (APK में इसकी key ही `normal_mode_decor_view_stable_inset_bottom_*` है):
```
stableBottom  = navigationBarsIgnoringVisibility.bottom      ← primary ("stable inset")
gestureBottom = systemGesturesIgnoringVisibility.bottom      ← fallback, सिर्फ़ जब stableBottom == 0
gap           = max(stableBottom ?: gestureBottom, displayCutout.bottom)     // floor default 0
```
Mode हमेशा `config_navBarInteractionMode == 2` से (insets से **नहीं** — Android 15+ में वह heuristic टूटा हुआ है)।

3-button mode में: अगर DecorView पहले से insets fit कर रहा है (`systemBars.bottom == 0` जबकि stable frame > 0)
तो gap = **0** — बिल्कुल Gboard जैसा "कोई extra padding नहीं"। वरना (API 35+ enforced edge-to-edge)
`navigationBars.bottom` लगाना ज़रूरी है, नहीं तो keys opaque nav bar के नीचे दब जाएँगी (यह padding दिखती नहीं)।

---

## 🔄 Gboard APK verification के बाद क्या बदला (2026-09-17)

| # | पहले | अब (Gboard-exact) |
|---|---|---|
| ① | gap = `max(navBar, systemGestures, mandatory, cutout)` | gap = **`navigationBarsIgnoringVisibility.bottom`** (= Gboard का *stable inset*), `systemGestures` सिर्फ़ fallback जब वह 0 हो |
| ② | `minGesturePaddingPx = 12dp` (hardcoded floor) | **`minGapPx = 0`** — Gboard device inset में अपना कुछ नहीं जोड़ता |
| ③ | 3-button branch में हमेशा `navBar.bottom` लगता था | अब **adaptive**: DecorView पहले से fit कर रहा हो तो **0** (वरना कुछ devices पर padding दोगुनी हो जाती) |
| ④ | `maxPaddingPx = 64dp` (fixed cap) | **`maxGapPx = 0` = cap बंद** (चाहो तो garbage-guard के लिए on करो) |
| ⑤ | orientation-agnostic | अब portrait/landscape के अलग floor/cap के hooks (Gboard के पास दोनों की अलग keys हैं) |
| ⑥ | setter नाम `setMinGesturePadding…` | अब `minGapPx` / `maxGapPx` / `minGapPxLandscape` / `maxGapPxLandscape` |

**जो नहीं बदला** (पहले से Gboard-exact था): live insets listener + 3 backup triggers,
`setDecorFitsSystemWindows(false)`, mode detection, strip का color = keyboard bg,
`navigationBarColor = TRANSPARENT` + pill contrast, docked-only check, `base + inset` absolute padding, SDK<29 → 0.

पूरा 12-point तुलना-तालिका → **`GBOARD-VERIFICATION.md` §4–5**


---

## Implement करते समय यह 4 चीज़ें owner से confirm करनी हैं

1. **Language:** Kotlin या Java? (दोनों controllers ready हैं)
2. **Keyboard layout:** custom View / XML / Jetpack Compose?
3. **Height:** keyboard की height *fixed* है या *wrap_content*?
   → fixed है तो `onPaddingChanged` में `height = baseHeight + bottomPadding` ज़रूरी है,
   वरना keys squeeze हो जाएँगी (INTEGRATION.md §3, तरीका B)
4. **Modes:** floating / split / one-handed keyboard है? → `isKeyboardDockedAtBottom` में wire करना है

---

## Implementation के 6 steps (short)

1. `androidx.core:core(-ktx):1.13.1+` dependency confirm करो
2. controller file को अपने package में copy करो (package line बदलो)
3. `onCreate()` में controller बनाओ → `onCreateInputView()` में `attach(rootView)`
4. `onWindowShown()`, `onStartInputView()`, `onConfigurationChanged()`, `onDestroy()` में hook करो
5. theme बदलने पर `setKeyboardBackgroundColor(bg)` call करो
6. `INTEGRATION.md` की test checklist चलाओ (खासकर: keyboard खुली रखकर live mode switch)

---

## ⚠️ 4 सबसे बड़े pitfalls (जो feature को silently तोड़ देते हैं)

1. `WindowCompat.setDecorFitsSystemWindows(window, false)` भूल गए → insets हमेशा 0, feature dead
2. `padding += inset` किया → हर dispatch पर padding बढ़ती जाएगी (हमेशा `base + inset` absolute)
3. **double padding** — API ≤ 34 पर अगर DecorView पहले से insets fit कर रहा हो और हम ऊपर से
   `navBar.bottom` जोड़ दें, तो keyboard ज़रूरत से ऊपर उठ जाएगी।
   → अब controller इसे खुद detect करता है (`systemBars.bottom == 0 && navBarIgnoringVisibility.bottom > 0`)
4. `systemGestures.bottom` को primary source मान लिया → Android 15+ में 3-button mode में भी non-zero आता है।
   Primary हमेशा **`navigationBarsIgnoringVisibility`** (= Gboard का *stable inset*), gestures सिर्फ़ fallback

बाकी pitfalls + पूरा test checklist → `INTEGRATION.md` §5–6

---

## 📌 Gboard से जुड़ी 2 और काम की बातें (APK से)

- **Keyboard height में nav bar भी गिना जाता है:** *"…maximum height of the keyboard, in pixels,
  including the keyboard body, keyboard header and **system navigation bar**."*
  → यानी Gboard के लिए gap keyboard की height का **हिस्सा** है, अतिरिक्त चीज़ नहीं।
  इसलिए fixed-height keyboard में `total = body + gap` सही model है (INTEGRATION.md §3 तरीक़ा B) ✅
- **Gboard height mm में नापता है, dp में नहीं:** `keyboard_height_33_mm … 52_mm` + `keyboard_height_ratio` (0.5–2.0)
  → padding feature के लिए ज़रूरी नहीं, पर Mgboard की height-setting के लिए बहुत काम की जानकारी।
- **Cutout भी इसी padding system से handle होता है:** *"Ignore display cutout area … extend the IME window to
  fullscreen … the keyboard UI might be cut off by the coutout area **if keyboard paddings are not set properly**."*
