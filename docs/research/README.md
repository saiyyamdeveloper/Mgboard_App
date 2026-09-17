# Research Projects — index

MgBoard ke features **pehle research, phir implement** ke rule se bane hain. Yeh paanch
projects woh research hain — Gboard ke APK (`18.3.1-beta`), AOSP source, aur official
docs se verify ki gayi detail. Har project ke saath uska **implementation status**
likha hai, taaki pata rahe ki kya app mein live hai aur kya abhi sirf research hai.

| # | Project | Kya hai | Status in `Mgboard` app | Files |
|---|---|---|---|---|
| 1 | **padding-project** | Gesture-navigation par keyboard ke neeche safe-area gap — Gboard-exact | ✅ **Implemented & wired** — `ime/insets/GestureNavPaddingController.kt`, `MgBoardIme` ke lifecycle mein attach/detach/reapply | `RESEARCH.md`, `GBOARD-VERIFICATION.md`, `INTEGRATION.md`, `kotlin/`, `java/` |
| 2 | **grid-menu-project** | ⊞ "More features" menu — 21 tiles, capacity 3–8, gating, customize/drag | ✅ **Implemented** — `grid/GridMenu.kt` + `grid/GridIcons.kt` (generated), `gridmenu/*Controller/Customizer/PanelHost`, Compose popup + toolbar pinned tiles | `RESEARCH.md`, `FUNCTIONS-AND-LOGIC.md`, `INTEGRATION.md`, `kotlin/`, `res/` |
| 3 | **mgboard-setting-project** | Gboard ka poora Settings tree (labels, summaries, search) | ✅ **Implemented** — `model/SettingsModel.kt` (generated), `ui/SettingsScreen.kt`, `SettingsActivity`, parity 1055/1055 | `RESEARCH.md`, `FUNCTIONS-AND-LOGIC.md` |
| 4 | **toolbar-project** | Suggestion strip / access-point toolbar ka behaviour | 🟡 **Partial** — toolbar Compose mein live hai (pinned tiles, undo/redo pills, mic, fixed grid icon); Gboard ke baaki strip behaviours (suggestions, promotions) research par hain | `RESEARCH.md` |
| 5 | **voice-pill-project** | Gboard "Assistant voice typing toolbar" — 5 states, drag/dock/flip, persistence | ✅ **Implemented** — `voice/` package (state machine + controller + drag + dictation bridge + Compose UI), IME-window widget (option A, zero permission), 219 JVM assertions | `RESEARCH.md`, `APK-EVIDENCE.md`, `SPEC-vs-GBOARD.md`, `IMPLEMENTATION.md` |

## Padhne ka order

Naye contributor ke liye:

1. `padding-project/GBOARD-VERIFICATION.md` — Gboard APK se internal keys kaise nikali
   gayin (methodology sab projects mein same hai)
2. `grid-menu-project/FUNCTIONS-AND-LOGIC.md` — Gboard-exact rules ka format
3. `mgboard-setting-project/FUNCTIONS-AND-LOGIC.md` — sabse bada inventory
4. `voice-pill-project/SPEC-vs-GBOARD.md` — kya cheez abhi match nahi karti

## Rule: research pehle, implementation baad

Koi bhi feature tab tak implement nahi hota jab tak uska research doc na ho. Aur
research ke baad implementation tab tak nahi hota jab tak explicit command na ho —
isse behaviour drift nahi hota.

## Rule: hide-nothing gating

Jo feature backend ke bina hai woh UI se **hataaya nahi jaata**. Tile/key dikhta hai
aur Gboard ka verbatim disabled-reason deta hai. Isliye user ko pata rehta hai ki
feature exist karta hai, bas abhi available nahi.
