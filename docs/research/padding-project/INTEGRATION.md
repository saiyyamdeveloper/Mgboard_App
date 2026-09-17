# Mgboard में wiring — step by step

> `GestureNavPaddingController.kt` (Kotlin) या `.java` (Java) — जो भी तुम्हारी codebase हो।
> दोनों का logic 100% same है।

## 1. Dependency (अगर नहीं है)

```gradle
// build.gradle (module)
implementation "androidx.core:core-ktx:1.13.1"     // Java-only project: androidx.core:core:1.13.1
```
`WindowInsetsCompat.Type.systemGestures()`, `getInsetsIgnoringVisibility()` और
`WindowCompat.setDecorFitsSystemWindows()` इसी से आते हैं (androidx.core ≥ 1.5.0 ज़रूरी)।

---

## 2. Kotlin — `MgboardImeService.kt`

```kotlin
class MgboardImeService : InputMethodService() {

    private lateinit var gesturePadding: GestureNavPaddingController
    private var keyboardRoot: View? = null

    override fun onCreate() {
        super.onCreate()
        gesturePadding = GestureNavPaddingController(
            ime = this,
            keyboardBackgroundColor = currentThemeBackgroundColor(),   // theme से असली bg color
        ).apply {
            debugLog = BuildConfig.DEBUG

            // floating / split / one-handed में keyboard bottom पर docked नहीं होता
            isKeyboardDockedAtBottom = { !keyboardLayoutController.isFloatingOrSplit() }

            onPaddingChanged = { bottom, _, _ ->
                // अगर keyboard की height FIXED है तो keys squeeze हो जाएँगी —
                // इसलिए container की height में padding जोड़ दो (नीचे §4 देखो)
                keyboardRoot?.let { updateKeyboardHeight(it, bottom) }
            }
        }
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.mgboard_keyboard_root, null)
        keyboardRoot = root
        root.setBackgroundColor(currentThemeBackgroundColor())   // strip इसी color की बनेगी
        gesturePadding.attach(root)                              // ★ यहीं feature जुड़ता है
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        gesturePadding.onStartInputView()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        gesturePadding.onWindowShown()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        gesturePadding.onConfigurationChanged()   // RRO overlay बदला → nav mode बदला
    }

    // theme switch / day-night पर
    private fun onKeyboardThemeChanged() {
        val bg = currentThemeBackgroundColor()
        keyboardRoot?.setBackgroundColor(bg)
        gesturePadding.setKeyboardBackgroundColor(bg)
    }

    override fun onDestroy() {
        gesturePadding.detach()
        super.onDestroy()
    }
}
```

## 2b. Java — `MgboardImeService.java`

```java
public class MgboardImeService extends InputMethodService {

    private GestureNavPaddingController gesturePadding;
    private View keyboardRoot;

    @Override public void onCreate() {
        super.onCreate();
        gesturePadding = new GestureNavPaddingController(this, currentThemeBackgroundColor());
        gesturePadding.setDebugLog(BuildConfig.DEBUG);
        gesturePadding.setDockedCheck(() -> !keyboardLayoutController.isFloatingOrSplit());
        gesturePadding.setOnPaddingChangedListener((bottom, l, r) -> {
            if (keyboardRoot != null) updateKeyboardHeight(keyboardRoot, bottom);
        });
    }

    @Override public View onCreateInputView() {
        keyboardRoot = getLayoutInflater().inflate(R.layout.mgboard_keyboard_root, null);
        keyboardRoot.setBackgroundColor(currentThemeBackgroundColor());
        gesturePadding.attach(keyboardRoot);
        return keyboardRoot;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        gesturePadding.onStartInputView();
    }

    @Override public void onWindowShown() {
        super.onWindowShown();
        gesturePadding.onWindowShown();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        gesturePadding.onConfigurationChanged();
    }

    @Override public void onDestroy() {
        gesturePadding.detach();
        super.onDestroy();
    }
}
```

---

## 3. Layout — strip को "invisible" रखने का सही तरीका

```xml
<!-- res/layout/mgboard_keyboard_root.xml -->
<LinearLayout
    android:id="@+id/keyboard_root"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@color/mgboard_keyboard_bg">   <!-- ★ यही color strip में दिखेगा -->

    <include layout="@layout/mgboard_candidates_bar"/>
    <include layout="@layout/mgboard_key_rows"/>       <!-- आख़िरी row = spacebar row -->

    <!-- अलग spacer View मत बनाओ — controller root पर ही paddingBottom लगाता है,
         इसलिए strip अपने-आप root के background color की बनती है (कोई border नहीं) -->
</LinearLayout>
```

**दो विकल्प, एक चुनो:**

| तरीका | कब use करें | असर |
|---|---|---|
| **A. root पर `paddingBottom`** (default, controller यही करता है) | keyboard की height `wrap_content` है | पूरी keyboard ऊपर उठ जाती है — keys का size वही रहता है (Gboard जैसा) |
| **B. container की height बढ़ाओ** | keyboard की height **fixed** है (आम बात) | `height = keyboardHeight + gap`, वरना keys squeeze हो जाएँगी |

> ⭐ **Gboard APK से verified:** तरीक़ा B ही Gboard का मॉडल है। Gboard की अपनी setting का description कहता है —
> *"An integer value used to set the maximum height of the keyboard, in pixels, including the keyboard body,
> keyboard header **and system navigation bar**."*
> यानी Gboard के लिए नीचे वाली gap कुल height का **हिस्सा** है, अतिरिक्त चीज़ नहीं।
>
> साथ ही यह भी मिला: Gboard height **mm में** नापता है, dp में नहीं —
> `keyboard_height_33_mm`, `35_mm`, `37_mm`, `39_mm`, `47_mm`, `48_mm`, `49_mm`, `52_mm`,
> `keyboard_height_ratio` (*"A float value to be multiplied when computing keyboard height …
> should between 0.5 and 2.0"*), `"set default keyboard height to 33 mm"`.
> यह padding feature के लिए ज़रूरी नहीं, पर Mgboard की height-setting बनानी हो तो यही सही मॉडल है
> (physical mm = हर screen पर उँगली के हिसाब से एक जैसी ergonomics)।

```kotlin
// तरीका B का helper (Gboard semantics: total = body + header + system navigation bar)
private fun updateKeyboardHeight(root: View, gapPx: Int) {
    val lp = root.layoutParams ?: return
    val base = prefs.keyboardBodyHeightPx      // तुम्हारी existing height setting (बिना gap के)
    lp.height = base + gapPx                   // gap इसी में गिनी जाती है
    root.layoutParams = lp
}
```

---

## 4. Debug / verification snippets

```kotlin
// किसी भी view पर लगाकर असली insets देखो (adb logcat -s MgboardGestureNav)
Log.d("INSETS", """
    navMode      = ${GestureNavPaddingController.getNavInteractionMode(this)}  (2 = gesture)
    stable navBar= ${insets.getInsetsIgnoringVisibility(Type.navigationBars())}  ← ★ Gboard इसे ही use करता है
    auto systemBars = ${insets.getInsets(Type.systemBars())}   ← 0 हो और stable > 0 हो तो DecorView fit कर चुका है
    sysGestures  = ${insets.getInsetsIgnoringVisibility(Type.systemGestures())}  (सिर्फ़ fallback)
    mandatory    = ${insets.getInsetsIgnoringVisibility(Type.mandatorySystemGestures())}
    tappable     = ${insets.getInsets(Type.tappableElement())}
    ime (हमेशा 0)= ${insets.getInsets(Type.ime())}
    cutout       = ${insets.getInsets(Type.displayCutout())}
    caption      = ${insets.getInsets(Type.captionBar())}
""".trimIndent())
```

```bash
# IME window की frame verify करो — bottom display height के बराबर होना चाहिए
adb shell dumpsys window windows | grep -A6 "InputMethod"
adb shell wm size

# live nav mode देखो
adb shell settings get secure navigation_mode          # 0/1/2
adb shell cmd overlay list --user 0 | grep -i nav      # gesture RRO enabled है या नहीं
```

---

## 5. Test checklist

- [ ] Gesture nav, portrait → spacebar row के नीचे strip, color = keyboard bg, कोई border नहीं
- [ ] Gesture nav, portrait → strip की height `stable navBar inset` के **बराबर** है (logcat से match करो), न ज़्यादा न कम
- [ ] 3-button nav, portrait → कोई extra strip नहीं; keys nav bar के नीचे नहीं दबीं
- [ ] **Double-padding check:** 3-button mode में keyboard की height सामान्य से ज़्यादा तो नहीं?
      (logcat में `decorAlreadyFits=true` और `pad(b=0)` दिखना चाहिए, API ≤ 34 पर)
- [ ] Keyboard **खुली रखकर** Settings से mode switch → दोनों तरफ़ तुरंत adjust, IME restart नहीं
- [ ] Keyboard **बंद** रखकर mode switch → दोबारा खोलने पर सही state
- [ ] Landscape: gesture (नीचे pill) + 3-button (nav bar side में) दोनों
- [ ] **Portrait और landscape में gap अलग-अलग सही** (Gboard के पास दोनों की अलग keys हैं)
- [ ] Day/night theme switch → strip का color + pill contrast सही
- [ ] Floating / split / one-handed mode → padding 0 (Gboard: `normal_mode_` only)
- [ ] Android 10 (API 29), Android 13, **Android 15 (API 35)** तीनों पर — खासकर API 35 पर 3-button में
      `systemGestures.bottom` non-zero आएगा, तब भी gap सही होनी चाहिए
- [ ] Samsung `Hide gesture hint` toggle → padding height बदलनी चाहिए (dynamic proof)
- [ ] Xiaomi `Hide full screen indicator` → वही check
- [ ] Tablet / foldable (taskbar/captionBar) पर कोई उल्टी padding नहीं
- [ ] Cutout नीचे की तरफ़ वाले device पर keys cutout में नहीं फँसीं

---

## 6. Known pitfalls (research + Gboard APK verification से निकले हुए)

1. **`Type.ime()` IME window में हमेशा 0** — AOSP `InsetsPolicy` उसे `removeSource(ID_IME)` कर देता है।
2. **`setDecorFitsSystemWindows(false)` भूल गए** → insets 0 आएँगे, feature silently fail।
3. **`padding += inset`** → हर dispatch पर padding बढ़ती जाएगी। हमेशा `base + inset` absolute set करो।
4. **`systemGestures.left > 0` से mode detect** → Android 15+ में टूटा हुआ है। `config_navBarInteractionMode` use करो।
5. ★ **Double padding** — API ≤ 34 पर अगर DecorView पहले से insets fit कर रहा हो और हम ऊपर से
   `navBar.bottom` जोड़ दें तो keyboard ज़रूरत से ऊपर उठ जाएगी।
   Controller अब खुद detect करता है: `systemBars().bottom == 0 && navBarIgnoringVisibility.bottom > 0`
   → `decorAlreadyFits = true` → 3-button branch में gap = **0**।
6. ★ **`systemGestures.bottom` को primary मान लेना** → Android 15+ में 3-button mode में भी non-zero आता है।
   Primary हमेशा **`navigationBarsIgnoringVisibility`** (Gboard का *stable inset*); gestures सिर्फ़ fallback जब वह 0 हो।
7. ★ **hardcoded floor/cap** → Gboard में कोई floor नहीं है (device inset जस-का-तस)।
   `minGapPx` का default **0** रखो; `maxGapPx` default **0 = बंद** (सिर्फ़ garbage guard चाहिए तभी on करो)।
8. **3-button + API 35 (edge-to-edge enforced)** → वहाँ DecorView fit नहीं करता, इसलिए gap खुद लगानी
   ज़रूरी है वरना keys opaque nav bar के नीचे दब जाएँगी (यह padding user को दिखती नहीं)।
9. **Fixed-height keyboard** → gap से keys squeeze होंगी। Gboard की semantics:
   *"maximum height of the keyboard … including the keyboard body, keyboard header **and system navigation bar**"*
   → यानी `totalHeight = bodyHeight + gap` (§3 तरीक़ा B)।
10. **Floating/split/one-handed में padding** → हवा में अजीब gap; `isKeyboardDockedAtBottom` से बचाओ
    (Gboard की keys ही `normal_mode_`-scoped हैं)।
11. **`navigationBarColor` reset नहीं किया** → कुछ devices पर keyboard छिपने के बाद भी color रह सकता है;
    `detach()` में restore हो रहा है।
