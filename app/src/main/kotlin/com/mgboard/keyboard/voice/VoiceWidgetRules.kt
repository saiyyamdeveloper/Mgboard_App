package com.mgboard.keyboard.voice

/**
 * Voice widget ke numbers/rules — sab research se confirmed (RESEARCH.md §5, §10.3, §12).
 *
 * Owner-approved defaults:
 *  - edge-flip threshold **28dp** (research: 24–32dp range) + **8dp hysteresis**
 *  - transitions sab animated **150–250ms**
 *  - backspace hold **400ms initial / 70ms repeat** — nayi timing NAHI banayi jaati,
 *    `layout/KeyTiming` ke wahi constants reuse hote hain (owner ka standing rule)
 */
object VoiceWidgetRules {

    // ── drag / flip ────────────────────────────────────────────────────────────
    /** Kitni door drag karne par hi move count ho ( accidental tap se bachao ). */
    const val DRAG_SLOP_DP = 6f

    /** Edge se itni door aane par horizontal → vertical flip (research: 28dp). */
    const val EDGE_FLIP_THRESHOLD_DP = 28f

    /**
     * Wapas horizontal hone ke liye edge se itni door jaana padta hai
     * (flip + hysteresis = 36dp) — warna edge ke paas flicker hota hai.
     */
    const val EDGE_FLIP_HYSTERESIS_DP = 8f
    const val FLIP_BACK_THRESHOLD_DP = EDGE_FLIP_THRESHOLD_DP + EDGE_FLIP_HYSTERESIS_DP

    /** Bottom edge se itni door aane par top par flip. */
    const val TOP_FLIP_THRESHOLD_DP = 40f

    // ── animation (RESEARCH.md §10.3) ──────────────────────────────────────────
    const val ANIM_COLLAPSE_MS = 200      // panel → pill (Material emphasized)
    const val ANIM_MENU_MS = 180          // popup scale+fade
    const val ANIM_FADE_MS = 150          // menu dismiss / pill fade-out
    const val ANIM_MORPH_MS = 250         // horizontal ↔ vertical container transform
    const val ANIM_SNAP_MS = 150          // dock par snap

    // ── pill size (dp-based, owner rule: fixed px nahi) ────────────────────────
    const val PILL_HEIGHT_DP = 48f
    const val PILL_WIDTH_DP = 300f
    /** Chhoti screens par pill screen ki itni fraction se chaudi nahi hogi (default position
     *  mein hi edge-flip zone ke andar aa jaane se bachata hai). */
    const val PILL_MAX_WIDTH_FRACTION = 0.80f
    const val PILL_CORNER_DP = 24f
    const val VERTICAL_PILL_WIDTH_DP = 52f
    const val VERTICAL_PILL_HEIGHT_DP = 210f
    const val MENU_WIDTH_DP = 260f

    // ── persistence keys (Gboard: widget_x_position / widget_y_position) ───────
    const val KEY_X = "widget_x_position"
    const val KEY_Y = "widget_y_position"
    const val KEY_ORIENTATION = "widget_change_widget_orientation"
    const val KEY_MODE_ENABLED = "voice_pill_mode_enabled"
    const val KEY_DRAG_LOCKED = "voice_force_horizontal_toolbar"
    const val KEY_TOOLTIP_SEEN = "voice_drag_tooltip_seen"
    const val KEY_RECENT_SYMBOLS = "voice_recent_symbols"

    /**
     * Drag ka nateeja.
     *
     * @param state     naya state (orientation flip ho sakta hai)
     * @param flipped   orientation badli?
     * @param docked    edge par snap hua?
     * @param showTip   pehli baar drag par "Hold and drag to move toolbar" tooltip
     */
    data class DragOutcome(
        val state: VoiceWidgetState,
        val flipped: Boolean = false,
        val docked: Boolean = false,
        val showTooltip: Boolean = false,
    )
}

/**
 * Drag + edge-flip + dock ka **pure** logic.
 *
 * RESEARCH.md §5.3 ka algorithm:
 *  - horizontal pill ko left/right edge ke `EDGE_FLIP_THRESHOLD_DP` ke andar le jao
 *    → vertical pill ban kar usi edge par dock ho jaata hai
 *  - vertical pill ko edge se `FLIP_BACK_THRESHOLD_DP` se zyada door le jao
 *    → wapas horizontal (bottom dock)
 *  - horizontal pill ko top ke paas le jao → FLOATING (Gboard: docked bottom/left/right)
 *  - drag lock ("Force the toolbar in horizontal mode and disable dragging") ON ho to
 *    sirf horizontal rehta hai aur flip nahi hota
 */
object WidgetDrag {

    fun onDrag(
        state: VoiceWidgetState,
        dx: Float,
        dy: Float,
        screenWidthDp: Float,
        screenHeightDp: Float,
        dragLocked: Boolean = false,
        firstDrag: Boolean = false,
    ): VoiceWidgetRules.DragOutcome {
        val pillW = VoiceWidgetRules.PILL_WIDTH_DP
        val pillH = VoiceWidgetRules.PILL_HEIGHT_DP

        return when (state) {
            is VoiceWidgetState.HorizontalPill -> {
                val pw = pillWidthFor(screenWidthDp)
                val nx = (state.x + dx).coerceIn(0f, (screenWidthDp - pw).coerceAtLeast(0f))
                val ny = (state.y + dy).coerceIn(0f, (screenHeightDp - pillH).coerceAtLeast(0f))

                val nearLeft = nx <= VoiceWidgetRules.EDGE_FLIP_THRESHOLD_DP
                val nearRight = nx + pw >= screenWidthDp - VoiceWidgetRules.EDGE_FLIP_THRESHOLD_DP
                val nearTop = ny <= VoiceWidgetRules.TOP_FLIP_THRESHOLD_DP

                // ★ Flip sirf tab jab user edge **ki taraf** drag kar raha ho.
                // Warna chhoti screen par (jaise 360dp) 300dp chaudi pill pehle se hi
                // threshold zone mein hoti hai aur bina drag kiye flip ho jaati —
                // Gboard mein aisa nahi hota ("Hold and drag to move toolbar").
                val draggingToLeft = dx < 0f
                val draggingToRight = dx > 0f

                when {
                    // vertical flip — drag lock mein allowed nahi
                    !dragLocked && nearLeft && draggingToLeft -> VoiceWidgetRules.DragOutcome(
                        state = VoiceWidgetState.VerticalPill(
                            mic = state.mic, dockedTo = Dock.LEFT,
                            x = 0f, y = ny.coerceIn(0f, (screenHeightDp - VoiceWidgetRules.VERTICAL_PILL_HEIGHT_DP).coerceAtLeast(0f)),
                        ),
                        flipped = true, docked = true, showTooltip = firstDrag,
                    )

                    !dragLocked && nearRight && draggingToRight -> VoiceWidgetRules.DragOutcome(
                        state = VoiceWidgetState.VerticalPill(
                            mic = state.mic, dockedTo = Dock.RIGHT,
                            x = screenWidthDp - VoiceWidgetRules.VERTICAL_PILL_WIDTH_DP,
                            y = ny.coerceIn(0f, (screenHeightDp - VoiceWidgetRules.VERTICAL_PILL_HEIGHT_DP).coerceAtLeast(0f)),
                        ),
                        flipped = true, docked = true, showTooltip = firstDrag,
                    )

                    else -> VoiceWidgetRules.DragOutcome(
                        state = state.copy(
                            x = nx, y = ny,
                            dockedTo = if (nearTop) Dock.FLOATING else Dock.BOTTOM,
                        ),
                        docked = nearTop,
                        showTooltip = firstDrag,
                    )
                }
            }

            is VoiceWidgetState.VerticalPill -> {
                val vw = VoiceWidgetRules.VERTICAL_PILL_WIDTH_DP
                val vh = VoiceWidgetRules.VERTICAL_PILL_HEIGHT_DP
                val nx = (state.x + dx).coerceIn(0f, (screenWidthDp - vw).coerceAtLeast(0f))
                val ny = (state.y + dy).coerceIn(0f, (screenHeightDp - vh).coerceAtLeast(0f))

                // "edge se door" ka matlab: docked edge se distance > threshold + hysteresis.
                // Left dock  → distance = nx
                // Right dock → distance = screenWidthDp - (nx + vw)
                val awayFromDockedEdge = if (state.dockedTo == Dock.LEFT) nx
                                         else screenWidthDp - (nx + vw)

                if (!dragLocked && awayFromDockedEdge > VoiceWidgetRules.FLIP_BACK_THRESHOLD_DP) {
                    // wapas horizontal — bottom dock par, x drag ke saath
                    VoiceWidgetRules.DragOutcome(
                        state = VoiceWidgetState.HorizontalPill(
                            mic = state.mic, status = state.mic.let { StatusText.SPEAK_NOW },
                            dockedTo = Dock.BOTTOM,
                            x = (nx - (VoiceWidgetRules.PILL_WIDTH_DP - vw) / 2f)
                                .coerceIn(0f, (screenWidthDp - VoiceWidgetRules.PILL_WIDTH_DP).coerceAtLeast(0f)),
                            y = screenHeightDp - VoiceWidgetRules.PILL_HEIGHT_DP,
                        ),
                        flipped = true, docked = true, showTooltip = firstDrag,
                    )
                } else {
                    VoiceWidgetRules.DragOutcome(
                        state = state.copy(x = nx, y = ny),
                        docked = true, showTooltip = firstDrag,
                    )
                }
            }

            else -> VoiceWidgetRules.DragOutcome(state = state)
        }
    }

    /** Screen bounds ke andar clamp (rotation/fold ke baad — RESEARCH.md §10.3). */
    fun clamp(state: VoiceWidgetState, screenWidthDp: Float, screenHeightDp: Float): VoiceWidgetState =
        when (state) {
            is VoiceWidgetState.HorizontalPill -> state.copy(
                x = state.x.coerceIn(0f, (screenWidthDp - pillWidthFor(screenWidthDp)).coerceAtLeast(0f)),
                y = state.y.coerceIn(0f, (screenHeightDp - VoiceWidgetRules.PILL_HEIGHT_DP).coerceAtLeast(0f)),
            )
            is VoiceWidgetState.VerticalPill -> state.copy(
                x = state.x.coerceIn(0f, (screenWidthDp - VoiceWidgetRules.VERTICAL_PILL_WIDTH_DP).coerceAtLeast(0f)),
                y = state.y.coerceIn(0f, (screenHeightDp - VoiceWidgetRules.VERTICAL_PILL_HEIGHT_DP).coerceAtLeast(0f)),
            )
            else -> state
        }

    /**
     * Pill ki chaudai — chhoti screens par screen ke 80% se zyada nahi.
     * (360dp screen par 300dp pill default position mein hi edge-flip zone ke andar
     * aa jaati, isliye cap zaroori hai.)
     */
    fun pillWidthFor(screenWidthDp: Float): Float =
        minOf(VoiceWidgetRules.PILL_WIDTH_DP, screenWidthDp * VoiceWidgetRules.PILL_MAX_WIDTH_FRACTION)

    /** Default position: horizontal pill bottom-center (Gboard: docked bottom). */
    fun defaultHorizontal(screenWidthDp: Float, screenHeightDp: Float): VoiceWidgetState.HorizontalPill =
        VoiceWidgetState.HorizontalPill(
            dockedTo = Dock.BOTTOM,
            x = ((screenWidthDp - pillWidthFor(screenWidthDp)) / 2f).coerceAtLeast(0f),
            y = (screenHeightDp - VoiceWidgetRules.PILL_HEIGHT_DP).coerceAtLeast(0f),
        )

    fun defaultVertical(dock: Dock, screenWidthDp: Float, screenHeightDp: Float): VoiceWidgetState.VerticalPill =
        VoiceWidgetState.VerticalPill(
            dockedTo = dock,
            x = if (dock == Dock.LEFT) 0f else (screenWidthDp - VoiceWidgetRules.VERTICAL_PILL_WIDTH_DP).coerceAtLeast(0f),
            y = ((screenHeightDp - VoiceWidgetRules.VERTICAL_PILL_HEIGHT_DP) / 2f).coerceAtLeast(0f),
        )
}

/**
 * Transition table — RESEARCH.md §10.3 ka 1:1 implementation.
 * Har transition par animation duration bhi milta hai (sab 150–250ms).
 */
object VoiceWidgetTransitions {

    data class Result(val state: VoiceWidgetState, val animMs: Int)

    /** mic access-point tap → State 1 (keyboard neeche bana rehta hai). */
    fun micTapped(current: VoiceWidgetState, hindi: Boolean): Result = when (current) {
        VoiceWidgetState.Hidden ->
            Result(VoiceWidgetState.FullVoicePanel(), VoiceWidgetRules.ANIM_COLLAPSE_MS)
        is VoiceWidgetState.FullVoicePanel ->
            Result(current, 0)
        is VoiceWidgetState.HorizontalPill ->
            // pill mein mic tap = pause/resume toggle, pill band NAHI hoti
            Result(current.copy(mic = toggleMic(current.mic)), VoiceWidgetRules.ANIM_FADE_MS)
        is VoiceWidgetState.VerticalPill ->
            Result(current.copy(mic = toggleMic(current.mic)), VoiceWidgetRules.ANIM_FADE_MS)
        is VoiceWidgetState.MenuOpen ->
            Result(current.over, VoiceWidgetRules.ANIM_FADE_MS)
        is VoiceWidgetState.SymbolsOverlay ->
            Result(VoiceWidgetState.FullVoicePanel(), VoiceWidgetRules.ANIM_COLLAPSE_MS)
    }

    /** ˅˅ chevron — pill dikhne ka **ekmatra** trigger (Gboard-exact). */
    fun chevronTapped(current: VoiceWidgetState, screenWidthDp: Float, screenHeightDp: Float): Result =
        when (current) {
            is VoiceWidgetState.FullVoicePanel -> Result(
                WidgetDrag.defaultHorizontal(screenWidthDp, screenHeightDp).copy(mic = current.mic, status = current.status),
                VoiceWidgetRules.ANIM_COLLAPSE_MS,
            )
            else -> Result(current, 0)
        }

    /** ⌨ keyboard icon — pill poori tarah dismiss, normal typing mode. */
    fun keyboardTapped(current: VoiceWidgetState): Result =
        Result(VoiceWidgetState.Hidden, VoiceWidgetRules.ANIM_FADE_MS)

    /** भाषा-badge / hamburger → menu; mic auto-pause (Gboard implicit behaviour). */
    fun badgeTapped(current: VoiceWidgetState): Result = when (current) {
        is VoiceWidgetState.MenuOpen -> Result(current.over, VoiceWidgetRules.ANIM_FADE_MS)
        is VoiceWidgetState.HorizontalPill ->
            Result(VoiceWidgetState.MenuOpen(current.copy(mic = MicState.PAUSED)), VoiceWidgetRules.ANIM_MENU_MS)
        is VoiceWidgetState.VerticalPill ->
            Result(VoiceWidgetState.MenuOpen(current.copy(mic = MicState.PAUSED)), VoiceWidgetRules.ANIM_MENU_MS)
        is VoiceWidgetState.FullVoicePanel ->
            Result(VoiceWidgetState.MenuOpen(current.copy(mic = MicState.PAUSED)), VoiceWidgetRules.ANIM_MENU_MS)
        else -> Result(current, 0)
    }

    /** Menu item → transition (research §10.3 + owner decision: poora 7-item menu). */
    fun menuItemTapped(
        item: VoiceMenuItem,
        current: VoiceWidgetState,
        screenWidthDp: Float,
        screenHeightDp: Float,
    ): Result {
        val base = if (current is VoiceWidgetState.MenuOpen) current.over else current
        return when (item) {
            VoiceMenuItem.SWITCH_VERTICAL -> Result(
                when (base) {
                    is VoiceWidgetState.HorizontalPill -> VoiceWidgetState.VerticalPill(
                        mic = base.mic, dockedTo = Dock.RIGHT,
                        x = screenWidthDp - VoiceWidgetRules.VERTICAL_PILL_WIDTH_DP, y = base.y,
                    )
                    else -> WidgetDrag.defaultVertical(Dock.RIGHT, screenWidthDp, screenHeightDp)
                },
                VoiceWidgetRules.ANIM_MORPH_MS,
            )

            VoiceMenuItem.SWITCH_HORIZONTAL -> Result(
                when (base) {
                    is VoiceWidgetState.VerticalPill -> VoiceWidgetState.HorizontalPill(
                        mic = base.mic, dockedTo = Dock.BOTTOM, x = base.x,
                        y = screenHeightDp - VoiceWidgetRules.PILL_HEIGHT_DP,
                    )
                    else -> WidgetDrag.defaultHorizontal(screenWidthDp, screenHeightDp)
                },
                VoiceWidgetRules.ANIM_MORPH_MS,
            )

            VoiceMenuItem.SYMBOLS -> Result(
                VoiceWidgetState.SymbolsOverlay(), VoiceWidgetRules.ANIM_COLLAPSE_MS,
            )

            // Settings / clipboard / translate / emoji / voice commands / feedback —
            // hide-nothing: host inka gate reason ya action deta hai (state wahi rehta hai)
            else -> Result(base, VoiceWidgetRules.ANIM_FADE_MS)
        }
    }

    /**
     * Back / bahar tap → pichla state (fade 150ms).
     * Symbols overlay band hone par pill apni **last state** ke saath wapas aati hai
     * (owner decision: overlay ke peeche pill visible rehti hai).
     */
    fun dismissed(current: VoiceWidgetState, underlying: VoiceWidgetState): Result = when (current) {
        is VoiceWidgetState.MenuOpen -> Result(current.over, VoiceWidgetRules.ANIM_FADE_MS)
        is VoiceWidgetState.SymbolsOverlay -> Result(underlying, VoiceWidgetRules.ANIM_FADE_MS)
        else -> Result(current, 0)
    }

    private fun toggleMic(m: MicState): MicState = when (m) {
        MicState.LISTENING -> MicState.PAUSED
        MicState.PAUSED -> MicState.LISTENING
        MicState.ERROR, MicState.OFFLINE -> MicState.LISTENING
    }

    /**
     * Menu ke items — Gboard ka exact order. Vertical/horizontal ke hisaab se
     * switch item badalta hai (dono APK strings se verbatim hain).
     */
    fun menuItems(current: VoiceWidgetState): List<VoiceMenuItem> {
        val orientationItem = when (current) {
            is VoiceWidgetState.MenuOpen -> when (current.over) {
                is VoiceWidgetState.VerticalPill -> VoiceMenuItem.SWITCH_HORIZONTAL
                else -> VoiceMenuItem.SWITCH_VERTICAL
            }
            is VoiceWidgetState.VerticalPill -> VoiceMenuItem.SWITCH_HORIZONTAL
            else -> VoiceMenuItem.SWITCH_VERTICAL
        }
        return listOf(
            VoiceMenuItem.SETTINGS,
            VoiceMenuItem.SHOW_VOICE_COMMANDS,
            VoiceMenuItem.SHOW_CLIPBOARD,
            VoiceMenuItem.SHOW_TRANSLATE,
            VoiceMenuItem.SHOW_EMOJI,
            orientationItem,
            VoiceMenuItem.SYMBOLS,
            VoiceMenuItem.FEEDBACK,
        )
    }
}
