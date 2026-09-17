# MgBoard Integration Guide — Grid Menu (⊞ "More features")

**Files is project mein:**

```
grid-menu-project/
├── RESEARCH.md                    ← naam ka faisla + 28 items ki list (structure)
├── FUNCTIONS-AND-LOGIC.md         ← har item ka function + logic (behaviour)  ★
├── INTEGRATION.md                 ← yeh file (wiring + test checklist)
├── kotlin/
│   ├── GridMenuItem.kt            ← 28 items enum + Gate/PanelId/KeyboardMode/GridAction
│   ├── GridMenuController.kt      ← capacity/overflow/gate-eval/click-handling
│   ├── GridMenuCustomizer.kt      ← long-press drag customization + promo state machine
│   └── GridMenuPanelHost.kt       ← panel/mode state machine + Impl skeleton
└── res/
    ├── values/strings.xml         ← EN (Gboard verbatim)
    └── values-hi/strings.xml      ← HI (🟢 verbatim + ✍ own translation marked)
```

---

## 1. Package + build setup

```kotlin
// build.gradle.kts (module)
android {
    defaultConfig {
        // Hindi strings ke liye resource qualifier already `values-hi/` mein hai — koi extra config nahi
    }
    buildFeatures { viewBinding = true }   // optional
}
```

```kotlin
// Package
package com.mgboard.keyboard.gridmenu
// R import
import com.mgboard.keyboard.R
```

> Agar MgBoard ka package alag hai (jaise `com.mgboard.ime`), to `GridMenuItem.kt` aur
> `GridMenuPanelHost.kt` ke `import com.mgboard.keyboard.R` ko apne package se badal dein.

---

## 2. Wiring — 5 steps

### Step 1: Context implement karo (IME service ke andar)

```kotlin
class MgBoardImeService : InputMethodService(),
    GridMenuController.GridMenuContext {

    override val orientation get() = resources.configuration.orientation
    override val isUnfolded get() = deviceStateManager.isUnfolded()      // aapka helper
    override val isTablet get() = resources.configuration.smallestScreenWidthDp >= 600
    override val isIncognito get() = currentEditorInfo.isIncognito()
    override val isWorkProfile get() = currentEditorInfo.isWorkProfile()
    override val hasEditorText get() = !currentInputEditorInfo.extras
        .getCharSequence("android.text").isNullOrBlank()
    override val editorFieldClass get() = when {
        currentInputEditorInfo.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 -> "password"
        currentInputEditorInfo.inputType and InputType.TYPE_TEXT_VARIATION_URI != 0 -> "url"
        else -> "normal"
    }
    override val keyboardSupportsResize get() = currentMode != KeyboardMode.SPLIT
    override val gboardLanguages get() = languageManager.enabledTags()
    override val deviceLanguage get() = Locale.getDefault().toLanguageTag()

    override fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    override fun isModelReady(id: String) = modelManager.isReady(id)
    override fun supportsFeatureInLanguage(f: String) = languageManager.supports(f)
    override fun getString(res: Int) = if (res == 0) "" else super.getString(res)
}
```

### Step 2: PanelHost contract apne keyboard view se jodo

```kotlin
class MgKeyboardView(...) : GridMenuPanelHostImpl.MgKeyboardViewContract {
    override fun setHeaderElement(t: String) { themeEngine.headerElement = t; invalidate() }
    override fun setBodyElement(t: String)   { themeEngine.bodyElement = t;   invalidate() }
    override fun rebuildToolbar()            { toolbarAdapter.submitList(controller.visibleToolbarItems()) }
    override fun setMode(m: String)          { layoutManager.applyMode(KeyboardMode.valueOf(m)) }
    override fun launchSettings()            { startActivity(Intent(this@…, MgSettingsActivity::class.java)) }
    override fun startVoiceTyping()          { voiceManager.start() }
    override fun startScanText()             { scanManager.start() }
    override fun openShareSheet()            { shareHelper.sharePlayStoreLink() }
    override fun openPersonalDictionary()    { startActivity(…PersonalDictionaryActivity…) }
    override fun performUndo()               { editHistory.undo() }
    override fun performRedo()               { editHistory.redo() }
    override fun showExpressionNav(show: Boolean) { expressionNav.visibility = if (show) VISIBLE else GONE }
}
```

### Step 3: Controller + Customizer banao

```kotlin
private lateinit var panelHost: GridMenuPanelHost
private lateinit var gridController: GridMenuController
private lateinit var gridCustomizer: GridMenuCustomizer

override fun onCreate() {
    super.onCreate()
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    panelHost     = GridMenuPanelHostImpl(MgKeyboardView(...))
    gridController = GridMenuController(prefs, this, panelHost)
    gridCustomizer = GridMenuCustomizer(prefs, gridController)
}
```

### Step 4: Toolbar adapter mein ⊞ icon add karo

```kotlin
// Toolbar = visible access points; last item hamesha ⊞ (fixed, non-removable)
val items = gridController.visibleToolbarItems()      // 5/6 + MORE_FEATURES
toolbarAdapter.submitList(items)

// Click
onAccessPointClick = { item ->
    if (item == GridMenuItem.MORE_FEATURES) gridController.toggleMenu()
    else gridController.onItemClick(item)?.let { showError(it) }   // Gboard error string
}

// Long-press → customization
onAccessPointLongClick = { item ->
    if (item.removable) {
        gridCustomizer.enterCustomization()
        showHint(R.string.grid_hold_drag_customize)      // "Hold and drag to customize"
    }
}
```

### Step 5: Grid popup render karo

```kotlin
fun renderGridMenu(popup: GridPopupView) {
    popup.header = getString(R.string.grid_more_features)      // "More features"
    popup.hint   = getString(R.string.grid_drag_reorganize)    // "Drag to reorganize…"
    popup.items  = gridController.buildMenu().map { r ->
        GridRow(
            item        = r.item,
            label       = getString(if (isHindi) r.item.labelHi.let { hiRes(it) } else enRes(r.item)),
            state       = r.state,
            disabledMsg = r.disabledReason,      // Gboard ka exact reason
            endIcon     = endIconFor(r.item),    // 🎨 .widget-popup-menu-entry-end-icon
            shortcutKey = shortcutFor(r.item),   // 🎨 .widget-popup-menu-entry-shortcuts-key
            promo       = gridCustomizer.promoTextFor(r.item),
        )
    }
    // accessibility: "More features opened" / "More features closed"
    popup.contentDescription = getString(
        if (gridController.isMenuOpen()) R.string.grid_more_features_opened
        else R.string.grid_more_features_closed)
}
```

---

## 3. Gboard-exact rules jo code mein already enforce hain

| # | Rule | Kahan |
|---|---|---|
| 1 | Capacity 5 portrait / 6 landscape, range **3–8 inclusive** | `GridMenuController.capacity()` + `MIN_COUNT/MAX_COUNT` |
| 2 | Order **semicolon-separated** persist | `loadOrder()/saveOrder()` + `SEPARATOR` |
| 3 | ⊞ grid icon **fixed** (`removable = false`) | `GridMenuItem.MORE_FEATURES` |
| 4 | Overflow = capacity ke baad wale sab items | `overflowIds()` |
| 5 | Gate check **pehle**, phir action | `onItemClick()` step 1 |
| 6 | Failure par **Gboard ka exact error string** return | `gateFailureReason()` + `Companion.ERR_*` |
| 7 | Panel switch par `savePreviousState()` ("Back to previous state") | `onItemClick()` + `PanelHostImpl` |
| 8 | Theme element names Gboard wale | `PanelId.themeElement`, `applyMode()` |
| 9 | Mutual exclusion (emoji key ↔ language key) | `applyMutualExclusion()` + `mutuallyExclusiveWith` |
| 10 | Promo state machine NOT_SHOWN → SHOWING → CLOSED | `GridMenuCustomizer.promoState()` |
| 11 | Remove action **tooltip CLOSED tak clickable nahi** | `isRemoveActionClickable()` |
| 12 | Capacity full par promote → last item **evict** | `promote()` + `onEvicted` callback |
| 13 | Demote par toolbar **3 se neeche nahi** jaayega | `demote()` MIN_COUNT guard |
| 14 | Orientation change par overflow **recompute** | `onOrientationChanged()` |

---

## 4. Test checklist (Gboard parity)

**Capacity / overflow**
- [ ] Portrait mein toolbar par exactly **5** items + ⊞ dikhein
- [ ] Landscape mein **6** items + ⊞
- [ ] Count setting ko 3 par set karo → toolbar par 3; 8 par → 8
- [ ] Count ko 2 ya 9 par set karne ki koshish → **clamp** ho 3/8
- [ ] Rotate karo → overflow list turant recompute ho (restart nahi)

**Grid icon**
- [ ] ⊞ par tap → menu open, contentDescription = "More features opened"
- [ ] Dobara tap → close, "More features closed"
- [ ] ⊞ ko long-press + drag → **kuch na ho** (removable = false)

**Customization**
- [ ] Kisi item ko long-press → "Hold and drag to customize" hint
- [ ] Grid → toolbar drag → item promote, order persist
- [ ] Toolbar → grid drag → item demote
- [ ] Toolbar par 3 items reh jaayein → 4th demote **block** ho
- [ ] Capacity full ho → promote par last item **evict** ho + callback fire
- [ ] "Finish customizing feature menus" → order `access_points_showing_order` mein **semicolon** se save
- [ ] App kill karke dobara kholo → order wahi rahe

**Gates (har ek par disabled reason dikhe)**
- [ ] Mic permission denied → Voice typing disabled + *"Gboard needs access to the microphone…"*
- [ ] Camera permission denied → Scan text disabled
- [ ] Contacts permission denied → Quick Insert disabled
- [ ] Password field → Settings/Theme disabled + *"…not available in this input field"*
- [ ] Empty editor → Text editing / Writing Tools disabled + *"Disabled because empty editor"*
- [ ] Work profile → Writing Tools disabled
- [ ] Incognito → Voice typing disabled + *"Voice typing is disabled in Incognito Mode"*
- [ ] Folded device → emoji toolbar button hidden (*"Only available in unfolded device state."*)
- [ ] Model not downloaded → Handwriting disabled

**Panel switching**
- [ ] Clipboard → header `.keyboard-header-area.panel.v2`, close → *"Back to previous state"*
- [ ] Symbols → body `.keyboard-body-area.for-non-prime-body`, exit → *"Back to letter keyboard"*
- [ ] Expression panel → `.navbar.for-expression-footer` visible
- [ ] One-handed → left/right switch, exit → *"Exit one-handed mode"*
- [ ] Floating → resize sub-mode, *"Finish changing keyboard size and position"*
- [ ] Split → *"some keys will be duplicated on both sides"* note

**Promo / education**
- [ ] Clipboard ka promo *"Copy & paste multiple items? Try Clipboard"* ek baar dikhe
- [ ] Dismiss ke baad dobara **na** dikhe (CLOSED permanent)
- [ ] Emoji fast-access row ka "Remove" tooltip close hone tak **clickable na ho**

**Errors**
- [ ] Offline translate → *"Can't connect. Retry offline."*
- [ ] Mic busy → *"Can't start. Microphone in use."*
- [ ] Generic failure → *"Something went wrong. Please try again."*

**A11y / i18n**
- [ ] Har item par `Open X` / `Close X` contentDescription
- [ ] Hindi locale par saare labels `values-hi/strings.xml` se aayein
- [ ] TalkBack: menu open/close par state announce ho

---

## 5. Pitfalls (jo galtiyan aasan hain)

1. **⊞ ko removable bana dena** — Gboard mein grid icon fixed hai. `removable = false` mat hatana.
2. **Capacity ko clamp na karna** — user 2 ya 9 set kar de to crash/blank toolbar. `coerceIn(3, 8)` zaroori.
3. **Order ko List<Int> (ordinal) mein save karna** — enum reorder hote hi corrupt ho jaayega.
   **Hamesha `id` string** save karo (Gboard bhi semicolon-separated ids rakhta hai).
4. **Gate check action ke baad karna** — Gboard pehle gate, phir action. Warna permission dialog ke
   baad panel khul jaayega aur inconsistent state banegi.
5. **`savePreviousState()` bhool jaana** — "Back to previous state" kaam nahi karega, user panel mein
   phans jaayega.
6. **Orientation change par overflow recompute na karna** — landscape mein 6 ki jagah 5 dikhega.
7. **Mutual exclusion sirf ek direction mein lagana** — Gboard dono direction handle karta hai
   (emoji key ↔ language key).
8. **Error strings khud likhna** — Gboard ke exact strings use karo (TalkBack users ko familiar lagta
   hai aur Gboard-parity banti hai).
9. **Hindi strings ko hardcode karna** — `labelHi` enum mein sirf fallback ke liye hai; UI hamesha
   `R.string.*` se lo, warna locale switch par update nahi hoga.
10. **Promo ko har baar dikhana** — `CLOSED` permanent hona chahiye (`access_point_education` flags
    Gboard mein bhi ek-baar-wale hain).
11. **`PanelId` aur `KeyboardMode` ko mix karna** — panel = UI container, mode = keyboard state.
    Dono alag track karo (`PanelHostImpl.applyMode()` dekhein).
12. **Customization mode mein normal click allow karna** — customize mode ON ho to tap = drag-handle,
    action execute mat karo.

---

## 6. Baaki projects se link

| Project | Kya share hota hai |
|---|---|
| `toolbar-project` | `visibleToolbarItems()` isi toolbar mein render hoti hai; `access_points_*` keys common |
| `mgboard-setting-project` | "Customize feature menus" settings entry; `feature_menu_show_{settings,theme,handwriting}` flags; keyboard height (mm/ratio) Resize item se |
| `padding-project` | Resize/Floating items ka height model (`total = body + gap`); `keyboard_height_ratio` 0.5–2.0 |
| `voice-pill-project` | Voice typing item → voice toolbar/pill; 100 MB offline model gate |

---

## 7. Implementation order (suggested)

| Phase | Kaam | Files |
|---|---|---|
| **1** | Enum + strings + Gate model | `GridMenuItem.kt`, `res/values*/strings.xml` |
| **2** | Controller: capacity/order/gate-eval | `GridMenuController.kt` |
| **3** | Panel host: mode state machine | `GridMenuPanelHost.kt` |
| **4** | Toolbar adapter + ⊞ icon wiring | aapka `MgKeyboardView` |
| **5** | Grid popup UI (`.widget-popup-menu-*` styling) | naya `GridPopupView` |
| **6** | Customizer: long-press drag + persistence | `GridMenuCustomizer.kt` |
| **7** | Promos + education state machine | `GridMenuCustomizer.kt` |
| **8** | Test checklist (§4) poora karo | — |

---

*Gboard-exact evidence ke liye `FUNCTIONS-AND-LOGIC.md` dekhein — har string wahan 🟢 verbatim quote ke saath hai.*
