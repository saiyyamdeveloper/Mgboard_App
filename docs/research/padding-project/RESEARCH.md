# Mgboard — Gesture-Navigation Bottom Safe-Area Padding
## Deep Research: Gboard में यह feature आख़िर काम कैसे करता है?

> सब कुछ AOSP source code (android14-release) से verify किया गया है — अनुमान नहीं।
> नीचे हर claim के साथ source file + line का हवाला है।
>
> ⭐ **Update (2026-09-17):** असली **Gboard APK 18.3.1-beta** खोलकर इस research को verify किया गया।
> Gboard की internal keys मिलीं: `normal_mode_keyboard_bottom_gap_{portrait,landscape}` और
> `normal_mode_decor_view_stable_inset_bottom_{portrait,landscape}` — यानी हमारा मॉडल सही निकला,
> पर 4 details बदलनी पड़ीं (floor 12dp → 0, primary source = stable nav-bar inset, 3-button branch
> adaptive, cap off)। **पूरा रिपोर्ट → `GBOARD-VERIFICATION.md`** और नीचे §6 में updated formula।

---

## 0. TL;DR (एक नज़र में पूरा logic)

```
        ┌────────────────────────────────────────────────────────────┐
        │  Android का IME window ALWAYS screen के bottom तक जाता है  │
        │  (nav bar के नीचे भी) — चाहे कोई भी navigation mode हो     │
        └────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┴───────────────────────────┐
        │                                                       │
   GESTURE NAV (mode 2)                                 3-BUTTON NAV (mode 0)
   nav bar window = पतली transparent pill               nav bar window = 48dp OPAQUE bar
   → pill keyboard के ऊपर draw होती है                   → bar keyboard को ढक देता है
   → keyboard को खुद keys ऊपर उठानी पड़ती हैं            → keys पहले से safe हैं
        │                                                       │
   systemGestures.bottom / navigationBars.bottom         navigationBars.bottom
   (≈16–28dp, device के हिसाब से) ≠ 0 मिलता है           (≈48dp) मिलता है, पर दिखता नहीं
        │                                                       │
   keys पर bottom padding लगाओ + उस strip को                कोई visible padding नहीं
   keyboard के background color से paint करो              (system bar ही strip है)
```

**Feature का पूरा सार = 3 चीज़ें:**
1. **Window को edge-to-edge बनाना** (`setDecorFitsSystemWindows(false)`) — वरना insets 0 आते हैं
2. **Mode detect करना** (`config_navBarInteractionMode == 2`) — insets से नहीं!
3. **Height insets से लेना** (`systemGestures`/`navigationBars`, hardcode कभी नहीं) + strip का color = keyboard background

---

## 1. Gboard में यह behavior दिखता कैसे है (observed)

| Navigation mode | Keyboard के नीचे क्या दिखता है | कौन paint करता है |
|---|---|---|
| **Gesture** (swipe) | spacebar row के नीचे ~16–28dp की खाली पट्टी, **बिल्कुल keyboard के background color की** — कोई border/line नहीं, continuous look | **Gboard खुद** (अपनी window में padding) |
| **3-button** | back/home/recent वाला normal nav bar | **System** (nav bar window), Gboard नहीं |
| **2-button** (Android 9 style) | 3-button जैसा | System |
| **Landscape + gesture** | नीचे पतली pill | Gboard |
| **Landscape + 3-button** | nav bar **side** में चला जाता है (left/right) | System |

User-facing confusion जो इसी feature से जुड़ी है (research से मिला):
- Pixel users: *"gap at the bottom of Gboard"* — हटाने का कोई official तरीका नहीं, क्योंकि वही system gesture area है।
- Samsung: `Settings → Display → Navigation bar → Hide gesture hint` करने पर Gboard की padding भी छोटी हो जाती है → **proof कि padding system insets से आती है, hardcoded नहीं**।
- Xiaomi/MIUI: *"hide full screen indicator"* on करने पर gap बदलता है → वही proof।
- Gboard के `Resize` / `Keyboard height` setting से कुल height बदलती है, पर gesture strip का **ratio वही रहता है**।

---

## 2. सबसे ज़रूरी सवाल: क्या IME window सच में screen के bottom तक जाता है?

**हाँ। हमेशा।** यह Android framework में hardcoded है।

### Evidence A — `InputMethodService.onCreate()` window setup
`frameworks/base/core/java/android/inputmethodservice/InputMethodService.java` (android14-release), line ~1626:

```java
final WindowManager.LayoutParams lp = window.getAttributes();
lp.setTitle("InputMethod");
lp.type = WindowManager.LayoutParams.TYPE_INPUT_METHOD;
lp.width  = WindowManager.LayoutParams.MATCH_PARENT;
lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
lp.gravity = Gravity.BOTTOM;
lp.setFitInsetsTypes(statusBars() | navigationBars());
lp.setFitInsetsSides(Side.all() & ~Side.BOTTOM);      // ←★ BOTTOM side excluded!
lp.receiveInsetsIgnoringZOrder = true;
window.setAttributes(lp);

final int windowFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
window.setFlags(windowFlags, windowFlagsMask);

// Automotive devices may request the navigation bar to be hidden when the IME shows up
if (mHideNavBarForKeyboard) {
    window.setDecorFitsSystemWindows(false);
}
```

`setFitInsetsSides(Side.all() & ~Side.BOTTOM)` का मतलब: **IME window की frame nav-bar inset से shrink नहीं होती — वह screen के आख़िरी pixel तक जाती है।**

### Evidence B — `WindowLayout.computeFrames()`
`frameworks/base/core/java/android/view/WindowLayout.java`, line ~140:

```java
if (type == TYPE_INPUT_METHOD
        && displayCutoutSafeExceptMaybeBars.bottom != MAX_Y
        && state.calculateInsets(displayFrame, navigationBars(), true).bottom > 0) {
    // The IME can always extend under the bottom cutout if the navbar is there.
    displayCutoutSafeExceptMaybeBars.bottom = MAX_Y;
}
```

### Evidence C — real-world measurement (Pixel 7, API 35)
एक actual IME PR में measured frame: `[0,1591][1080,2400]` — screen height 2400px है, यानी IME window **bottom edge तक** गया (gesture bar के नीचे भी)।

---

## 3. तो फिर 3-button mode में keyboard nav bar के नीचे क्यों नहीं चला जाता?

क्योंकि **nav bar एक अलग system window है जो IME window के ऊपर (higher z-order) draw होता है** और 3-button mode में वह **opaque** होता है। IME की keys उस opaque bar के पीछे छिप जाती हैं — इसलिए "padding" की ज़रूरत ही नहीं पड़ती, वो already invisible है।

Gesture mode में nav bar window **transparent** है (सिर्फ़ pill draw करती है), इसलिए IME का content उसमें दिखाई देता है → यहीं keyboard को **खुद** keys ऊपर उठानी पड़ती हैं।

### Evidence — `InsetsPolicy`
`frameworks/base/services/core/java/com/android/server/wm/InsetsPolicy.java`

```java
// line 127
mHideNavBarForKeyboard = r.getBoolean(R.bool.config_hideNavBarForKeyboard);

// line ~388 : adjustVisibilityForIme()
if (w.mIsImWindow) {
    InsetsState state = originalState;
    // If navigation bar is not hidden by IME, IME should always receive visible
    // navigation bar insets.
    final boolean navVisible = !mHideNavBarForKeyboard;
    ... // nav bar source को force-visible करके IME को भेजा जाता है
}

// line ~550 : getNavControlTarget()
final WindowState imeWin = mDisplayContent.mInputMethodWindow;
if (imeWin != null && imeWin.isVisible() && !mHideNavBarForKeyboard) {
    // Force showing navigation bar while IME is visible and if navigation bar is not
    // configured to be hidden by the IME.
    return null;
}
```

`config_hideNavBarForKeyboard` का AOSP default = **false**
(`frameworks/base/core/res/res/values/config.xml` line 5306) — यानी phones पर nav bar keyboard के दौरान भी visible रहता है, और **IME को nav-bar insets हमेशा मिलते हैं**।

### Evidence — IME को अपने आप के insets नहीं मिलते
```java
// InsetsPolicy.enforceInsetsPolicyForTarget(), line ~309
if (attrs.type == TYPE_INPUT_METHOD) {
    state = new InsetsState(state);
    state.removeSource(ID_IME);   // ← keyboard को Type.ime() का inset कभी नहीं मिलेगा
}
```
इसलिए IME के अंदर `WindowInsetsCompat.Type.ime()` हमेशा 0 होता है — यह एक common गलती है।

---

## 4. ★ सबसे बड़ा proof: framework खुद IME window पर system-gesture insets पढ़ता है

`InputMethodService.java`, line ~1218:

```java
/** Set region of the keyboard to be avoided from back gesture */
private void setImeExclusionRect(int visibleTopInsets) {
    View rootView = mInputFrame.getRootView();
    android.graphics.Insets systemGesture =
            rootView.getRootWindowInsets().getInsets(Type.systemGestures());   // ←★
    ArrayList<Rect> exclusionRects = new ArrayList<>();
    exclusionRects.add(new Rect(0, visibleTopInsets, systemGesture.left, rootView.getHeight()));
    exclusionRects.add(new Rect(rootView.getWidth() - systemGesture.right,
            visibleTopInsets, rootView.getWidth(), rootView.getHeight()));
    rootView.setSystemGestureExclusionRects(exclusionRects);
}
```

**निष्कर्ष:** IME window को `Type.systemGestures()` के insets **सच में dispatch होते हैं** — यह Google का अपना code है। यही API हमें padding height के लिए चाहिए।

Bonus: `WindowInsets#getSystemGestureInsets()` की official doc में लिखा है —
> *"the system will put a limit of 200dp on the vertical extent of the exclusions it takes into account. The limit does not apply while the navigation bar is stickily hidden, **nor to the `android.inputmethodservice.InputMethodService` input method** and `CATEGORY_HOME` home activity."*

यानी keyboard के लिए gesture-exclusion की 200dp सीमा भी हटी हुई है — IME एक privileged window type है।

---

## 5. Navigation mode कैसे detect करें (3 तरीके, reliability के हिसाब से)

### ✅ तरीका 1 (best): `config_navBarInteractionMode` resource
```java
int id = res.getIdentifier("config_navBarInteractionMode", "integer", "android");
int mode = id > 0 ? res.getInteger(id) : 0;
// 0 = 3-button, 1 = 2-button, 2 = gesture
```
AOSP config.xml line 3943-3947:
```xml
<!-- Controls the navigation bar interaction mode:
     0: 3 button mode (back, home, overview buttons)
     1: 2 button mode (back, home buttons + swipe up for overview)
     2: gestures only for back, home and overview -->
<integer name="config_navBarInteractionMode">0</integer>
```
यह value **RRO overlay** से set होती है — जब user Settings में mode बदलता है, तो यह resource value **turant** बदल जाती है और एक configuration change भी dispatch होता है (इसलिए `onConfigurationChanged` में इसे दोबारा पढ़ना ज़रूरी है)।

### ⚠️ तरीका 2: `Settings.Secure "navigation_mode"`
```java
Settings.Secure.getInt(cr, "navigation_mode", 0)   // 0/1/2
```
कई OEMs पर काम करता है, पर **public/undocumented key** है → सिर्फ़ fallback के तौर पर use करें, primary नहीं।

### ❌ तरीका 3 (mat use karo): `systemGestures.left/right > 0`
पुराना StackOverflow trick। **Android 15 (API 35) से टूट चुका है** — अब 3-button mode में भी `systemGestures` non-zero आता है। यही कारण है कि हम mode detection के लिए insets पर भरोसा नहीं करते।

---

## 6. Padding height किन insets से लें?

| Inset type | Gesture mode (portrait) | 3-button (portrait) | Use |
|---|---|---|---|
| **`navigationBarsIgnoringVisibility.bottom`** | ≈ pill area (16–28dp) | ≈ 48dp | ✅ **PRIMARY** — यही Gboard का `decor_view_stable_inset_bottom` है |
| `systemGesturesIgnoringVisibility.bottom` | ≈ pill + home-gesture zone | ≈ nav bar (Android 15+ पर यहाँ फँसाव है) | ⚠️ **सिर्फ़ fallback**, जब primary 0 हो |
| `mandatorySystemGestures().bottom` | home gesture zone (अक्सर बड़ा) | nav bar height | ❌ मत लो — ज़रूरत से बड़ी gap बनती है |
| `tappableElement().bottom` | **0** (क्योंकि nav bar transparent है) | 48dp | ❌ हमारे काम का नहीं |
| `ime().bottom` | **हमेशा 0** (ऊपर §3 देखें) | 0 | ❌ कभी नहीं |
| `displayCutout().bottom` | cutout वाले devices पर | वही | ✅ max() में मिलाएँ (Gboard: "Ignore display cutout area") |
| `captionBar().bottom` | tablets/desktop पर | वही | ✅ सिर्फ़ 3-button branch में |
| `systemBars().bottom` (visible) | = nav bar | = nav bar, **या 0 अगर DecorView fit कर चुका** | ✅ **double-padding detect करने के लिए** |

### ⭐ Final formula (Gboard APK से verified)

```
stableBottom  = navigationBarsIgnoringVisibility.bottom      // Gboard: decor_view_stable_inset_bottom
gestureBottom = systemGesturesIgnoringVisibility.bottom      // fallback
autoBottom    = systemBars().bottom                          // DecorView ने fit किया या नहीं
decorAlreadyFits = (stableBottom > 0 && autoBottom == 0)

if (SDK < 29 || !docked)          gap = 0
else if (gestureNav)              gap = max(stableBottom > 0 ? stableBottom : gestureBottom,
                                            displayCutout.bottom,
                                            minGapPx /* default 0 */)
else if (decorAlreadyFits)        gap = 0                    // ← Gboard जैसा: 3-button में कोई extra padding नहीं
else                              gap = max(stableBottom, displayCutout.bottom, captionBar.bottom, minGapPx)

gap = minGapPx > 0 ? max(gap, minGapPx) : gap
if (maxGapPx > 0) gap = min(gap, maxGapPx)                   // सिर्फ़ garbage-value guard, default बंद
```

**चार बातें जो Gboard verification से बदलीं:**
1. **primary = stable nav-bar inset**, `systemGestures` सिर्फ़ fallback (Gboard की key का नाम ही
   `decor_view_stable_inset_bottom` है)
2. **floor = 0** (Gboard device inset में अपना कुछ नहीं जोड़ता — यह "hardcode मत करो" वाली requirement
   से भी ज़्यादा सख़्ती से मेल खाता है)
3. **3-button branch adaptive है** — वरना API ≤ 34 के कुछ devices पर padding **दोगुनी** हो जाती
4. **cap default बंद** है (यह layout choice नहीं, सिर्फ़ garbage guard है)

> Chris Banes (Google) की official gesture-navigation series से:
> *"In gesture navigation with color adaptation the navigation bar is transparent, meaning tappable views could theoretically be placed within it, which is why it [tappableElementInsets] contains a bottom value of 0."*
> और: *"**Never hardcode the values** … since the navigation bar can change size — use insets."*

> **Gboard की height semantics (APK से):** *"An integer value used to set the maximum height of the keyboard,
> in pixels, including the keyboard body, keyboard header **and system navigation bar**."*
> → यानी gap keyboard की कुल height का **हिस्सा** है, अतिरिक्त नहीं। fixed-height keyboard में
> `total = body + gap` ही सही model है (देखो `INTEGRATION.md` §3 तरीक़ा B)।

---

## 7. ⚠️ सबसे बड़ा gotcha: `setDecorFitsSystemWindows(false)` ज़रूरी है

Default IME window में `decorFitsSystemWindows == true` रहता है (सिर्फ़ `config_hideNavBarForKeyboard=true` यानी Automotive पर false होता है)। उस हालत में **DecorView insets को consume कर लेता है** और आपके listener को bottom ≈ 0 मिलता है।

इसलिए feature को reliably काम कराने के लिए:
```java
Window w = getWindow().getWindow();          // IME की SoftInputWindow
w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
w.setNavigationBarColor(Color.TRANSPARENT);   // या keyboard bg color
WindowCompat.setDecorFitsSystemWindows(w, false);   // API 30+
// API 29: decorView.systemUiVisibility |= SYSTEM_UI_FLAG_LAYOUT_STABLE
//                                        | SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
```

**पर ध्यान रहे:** ऐसा करने पर 3-button mode में keyboard nav bar के *नीचे* चला जाएगा (क्योंकि अब DecorView padding नहीं लगाएगा)। इसलिए 3-button branch में हमें `navigationBars` की पूरी height का padding **खुद** लगाना पड़ता है — तभी keys ऊपर रहेंगी। visually कोई फ़र्क़ नहीं दिखेगा क्योंकि nav bar opaque है और ऊपर draw होता है; बस nav-bar color keyboard bg से match होना चाहिए (वरना 3-button mode में एक अलग रंग की पट्टी दिखेगी — यही HeliBoard/AnySoftKeyboard की "colorize nav bar" setting है)।

### Real-world reference implementation (AnySoftKeyboard)
`ime/app/src/main/java/com/anysoftkeyboard/ime/AnySoftKeyboardColorizeNavBar.java`:
```java
w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
w.setNavigationBarColor(Color.TRANSPARENT);
WindowCompat.setDecorFitsSystemWindows(w, false);          // API 30+
ViewCompat.setOnApplyWindowInsetsListener(w.getDecorView(), (v, windowInsets) -> {
    Insets navBarInsets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
    int bottomPadding = Math.max(navBarInsets.bottom, getMinimumBottomPadding());
    inputContainer.setBottomPadding(bottomPadding);
    return windowInsets;
});
ViewCompat.requestApplyInsets(w.getDecorView());
```
यह वही pattern है जो हम Mgboard में use करेंगे — फ़र्क़ सिर्फ़ इतना कि हम **mode के हिसाब से branch** करेंगे (3-button में visible extra padding नहीं)।

---

## 8. Real-time switching (बिना keyboard restart के)

चार layers — कोई एक अकेला काफ़ी नहीं:

| # | Mechanism | कब fire होता है |
|---|---|---|
| 1 | `ViewCompat.setOnApplyWindowInsetsListener(decorView)` | nav mode बदलने पर insets dispatch होता है (keyboard खुली हो तब सबसे reliable) |
| 2 | `onConfigurationChanged()` में mode दोबारा पढ़ो + `ViewCompat.requestApplyInsets(decor)` | RRO overlay बदलने पर config change आता है |
| 3 | `ContentObserver` on `Settings.Secure "navigation_mode"` | keyboard छिपी हुई हो तब भी तुरंत पता चल जाता है |
| 4 | `onWindowShown()` / `onStartInputView()` में `requestApplyInsets()` | अगली बार keyboard खुलने पर guaranteed sync |

पहली dispatch miss होने का race condition Avoid करने के लिए listener लगाने के बाद **हमेशा** `ViewCompat.requestApplyInsets(decorView)` call करें (post करके, layout के दौरान नहीं)।

---

## 9. Visual continuity (कोई अलग color/border नहीं)

```
   ┌───────────────────────────────┐  ← keyboard top
   │  q w e r t y u i o p          │
   │  a s d f g h j k l            │
   │  z x c v b n m                │
   │  [sym] [space] [enter]        │  ← आख़िरी row
   ├───────────────────────────────┤  ← यह line दिखनी ही नहीं चाहिए
   │      (keyboard bg color)      │  ← padding strip == same background
   │           ▁▁▁▁                │  ← system की gesture pill (transparent bg)
   └───────────────────────────────┘  ← screen bottom
```

इसके लिए 3 काम:
1. **Padding strip का background = keyboard का background.** सबसे आसान तरीका — padding उसी root/container view पर लगाएँ जिसका background पहले से keyboard bg है (नया View मत बनाओ, वरना color sync करना पड़ेगा)।
2. **`window.setNavigationBarColor(TRANSPARENT)`** → pill के पीछे DecorView का background दिखेगा। अगर कोई OEM इस पर भी color paint करे, तो सीधे `setNavigationBarColor(keyboardBgColor)` लगा दो।
3. **Pill contrast:** `WindowInsetsControllerCompat.isAppearanceLightNavigationBars = isLightColor(bg)` → light keyboard पर dark pill, dark keyboard पर light pill (Gboard यही करता है)।
4. Theme बदलने पर (day/night, user theme) → `setKeyboardBackground(color)` दोबारा call करो।

---

## 10. Edge cases checklist

| Case | Behavior |
|---|---|
| API < 29 | gesture nav अस्तित्व में नहीं → padding = 0, feature off |
| 2-button mode (1) | 3-button जैसा treat करो (padding सिर्फ़ keys को बचाने के लिए, visible strip नहीं) |
| Landscape + 3-button | nav bar **side** में → left/right padding चाहिए, bottom नहीं |
| Landscape + gesture | नीचे pill → bottom padding |
| Floating / split / one-handed keyboard | keyboard screen के bottom पर docked नहीं है → padding **0** (वरना अजीब gap) |
| Tablet / taskbar (Android 12L+) | `captionBar()`/taskbar insets भी max() में शामिल करो |
| Desktop windowing / freeform | insets 0 या garbage → 64dp cap बचाता है |
| Cutout नीचे की तरफ़ | `displayCutout().bottom` max() में मिलाओ |
| Keyboard hidden है और user ने mode बदला | ContentObserver + अगली `onWindowShown()` पर sync |
| Theme/day-night बदला | background + pill contrast दोबारा apply करो |

---

## 11. Test matrix (implement करने के बाद यही चलाओ)

1. Pixel (gesture) portrait → strip दिखे, keys pill से ऊपर, strip का color = keyboard bg
2. Pixel → Settings में 3-button on करो, keyboard खुली रहे → strip तुरंत हटे, keys nav bar से न ढकें
3. 3-button → gesture → live switch (keyboard खुली हुई) → दोनों तरफ़ smooth, कोई restart नहीं
4. Landscape दोनों modes में
5. Samsung (gesture hint hide on/off) → padding height बदलनी चाहिए (proof: dynamic)
6. Xiaomi/MIUI "hide full screen indicator" on → padding छोटी/0 हो
7. Android 10 device (API 29) और Android 15 device (API 35) दोनों पर
8. Keyboard छिपी हुई हो तब mode switch → दोबारा खोलने पर सही state
9. Dark theme + light theme दोनों में pill का contrast सही
10. `adb shell wm dump-visible-window-views` / `adb shell dumpsys window` से IME frame verify करो: bottom = display height होना चाहिए

---

## 12. Sources

- AOSP `InputMethodService.java` (android14-release) — window layout params, `setImeExclusionRect`, `onComputeInsets`
- AOSP `InsetsPolicy.java` — `adjustVisibilityForIme`, `getNavControlTarget`, `enforceInsetsPolicyForTarget`, `config_hideNavBarForKeyboard`
- AOSP `WindowLayout.java` — "The IME can always extend under the bottom cutout if the navbar is there"
- AOSP `core/res/res/values/config.xml` — `config_navBarInteractionMode`, `config_hideNavBarForKeyboard`
- Android Developers: *Display content edge-to-edge in views*, *Ensure compatibility with gesture navigation*
- Chris Banes, *Gesture Navigation: handling visual overlaps (II)* (Android Developers Medium)
- AnySoftKeyboard `AnySoftKeyboardColorizeNavBar.java` (production IME implementation)
- HeliBoard/OpenBoard `LatinIME.setNavigationBarColor()` (IME window पर nav bar color set करने का pattern)
- FlorisBoard `ImeWindow.kt` (Compose IME में `navigationBarsPadding()` का use)
