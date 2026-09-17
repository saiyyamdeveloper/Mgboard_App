package com.mgboard.keyboard.voice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgboard.keyboard.layout.KeyKind
import com.mgboard.keyboard.layout.KeyTiming
import com.mgboard.keyboard.ui.KeyGestures
import com.mgboard.keyboard.ui.MgondiFont
import kotlin.math.roundToInt

/**
 * Voice widget ka Compose UI — paanch states (RESEARCH.md §4):
 *
 *  1. **FullVoicePanel**  — status + भाषा badge + नीला mic circle + ˅˅ chevron; keyboard neeche bana rehta hai
 *  2. **HorizontalPill**  — badge | ⌨ | status | ⌫ | 🎤 (mic ke kone par chhota language tag)
 *  3. **MenuOpen**        — Gboard ka poora menu (7 items) + divider ke baad language list
 *  4. **SymbolsOverlay**  — "चिह्न" header + X + grid + category tabs; peeche pill visible
 *  5. **VerticalPill**    — 🎤 | ⌫ | ⌨ | badge (ऊपर→नीचे)
 *
 * Standing rules jo yahan follow hue:
 *  - **additive**: keyboard ka koi composable/key pipeline chheda nahi gaya
 *  - **backspace timing reuse**: pill ke ⌫ par wahi `KeyGestures` repeat handler hai
 *    (400ms initial / 70ms repeat) — naya Handler/Runnable nahi banaya
 *  - **dp-based sizing**, koi fixed px nahi
 *  - **gesture-inset safe**: dock karte waqt system insets ka khayal (padding-project)
 *  - transitions sab animated 150–250ms
 */
@Composable
fun VoiceWidgetLayer(controller: VoiceWidgetController, tick: Int) {
    val state = remember(controller.state, tick) { controller.state }
    if (state == VoiceWidgetState.Hidden) return

    when (state) {
        is VoiceWidgetState.FullVoicePanel -> FullVoicePanel(controller)

        is VoiceWidgetState.HorizontalPill -> {
            FloatingPill(controller, state)
            if (controller.tooltipVisible) DragTooltip(state.x, state.y)
        }

        is VoiceWidgetState.VerticalPill -> {
            FloatingPill(controller, state)
            if (controller.tooltipVisible) DragTooltip(state.x, state.y)
        }

        is VoiceWidgetState.MenuOpen -> {
            // pill peeche visible rehti hai, menu upar khulta hai
            when (val over = state.over) {
                is VoiceWidgetState.HorizontalPill -> FloatingPill(controller, over.copy(mic = MicState.PAUSED))
                is VoiceWidgetState.VerticalPill -> FloatingPill(controller, over.copy(mic = MicState.PAUSED))
                is VoiceWidgetState.FullVoicePanel -> FullVoicePanel(controller)
                else -> Unit
            }
            VoiceMenu(controller, state)
        }

        is VoiceWidgetState.SymbolsOverlay -> SymbolsOverlay(controller, state)
        VoiceWidgetState.Hidden -> Unit
    }
}

// ══════════════════════════ State 1 — Full Voice Panel ══════════════════════════

/**
 * Mic dabane par khulta hai. Gboard-exact: full keyboard **neeche bana rehta hai**,
 * uske upar yeh voice strip aati hai. Status text Gboard se verbatim: **"अब बोलें"**.
 */
@Composable
private fun FullVoicePanel(controller: VoiceWidgetController) {
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LanguageBadge(controller, big = true)
            Spacer(Modifier.width(8.dp))
            Text(
                text = controller.displayStatus(),
                fontFamily = MgondiFont,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // ˅˅ chevron — "Minimize toolbar and hide suggestions" (pill ka ekmatra trigger)
            WidgetIcon("⌄⌄", contentDescription = "Collapse keyboard") { controller.onChevronTap() }
            Spacer(Modifier.width(4.dp))
            MicCircle(controller, size = 40.dp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            Text(
                text = StatusText.JUST_SPEAK.label(controller.isHindi()),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ══════════════════════════ States 2 & 5 — floating pills ══════════════════════════

@Composable
private fun FloatingPill(controller: VoiceWidgetController, state: VoiceWidgetState) {
    val density = LocalDensity.current

    val xDp by animateDpAsState(
        targetValue = pillX(state).dp,
        animationSpec = tween(VoiceWidgetRules.ANIM_MORPH_MS),
        label = "pillX",
    )
    val yDp by animateDpAsState(
        targetValue = pillY(state).dp,
        animationSpec = tween(VoiceWidgetRules.ANIM_SNAP_MS),
        label = "pillY",
    )

    val horizontal = state is VoiceWidgetState.HorizontalPill
    val w by animateDpAsState(
        targetValue = if (horizontal) VoiceWidgetRules.PILL_WIDTH_DP.dp else VoiceWidgetRules.VERTICAL_PILL_WIDTH_DP.dp,
        animationSpec = tween(VoiceWidgetRules.ANIM_MORPH_MS), label = "w",
    )
    val h by animateDpAsState(
        targetValue = if (horizontal) VoiceWidgetRules.PILL_HEIGHT_DP.dp else VoiceWidgetRules.VERTICAL_PILL_HEIGHT_DP.dp,
        animationSpec = tween(VoiceWidgetRules.ANIM_MORPH_MS), label = "h",
    )
    val corner by animateDpAsState(
        targetValue = VoiceWidgetRules.PILL_CORNER_DP.dp,
        animationSpec = tween(VoiceWidgetRules.ANIM_MORPH_MS), label = "corner",
    )

    Box(
        Modifier
            .offset { IntOffset(with(density) { xDp.roundToPx() }, with(density) { yDp.roundToPx() }) }
            .size(w, h)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corner))
            // drag — "Hold and drag to move toolbar"
            .pointerInput(state::class) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = { controller.onDragEnd() },
                    onDragCancel = { controller.onDragEnd() },
                ) { change, drag ->
                    change.consume()
                    controller.onDrag(
                        with(density) { drag.x.toDp().value },
                        with(density) { drag.y.toDp().value },
                    )
                }
            },
    ) {
        if (horizontal) HorizontalPillContent(controller, state) else VerticalPillContent(controller, state)
    }
}

private fun pillX(s: VoiceWidgetState): Float = when (s) {
    is VoiceWidgetState.HorizontalPill -> s.x
    is VoiceWidgetState.VerticalPill -> s.x
    else -> 0f
}

private fun pillY(s: VoiceWidgetState): Float = when (s) {
    is VoiceWidgetState.HorizontalPill -> s.y
    is VoiceWidgetState.VerticalPill -> s.y
    else -> 0f
}

/** State 2 — order Gboard ke teardown se: badge | ⌨ | status | ⌫ | 🎤 */
@Composable
private fun HorizontalPillContent(controller: VoiceWidgetController, state: VoiceWidgetState.HorizontalPill) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageBadge(controller, big = true)
        Spacer(Modifier.width(4.dp))
        WidgetIcon("⌨", contentDescription = "Show keyboard") { controller.onKeyboardTap() }
        Spacer(Modifier.width(2.dp))
        Text(
            text = controller.displayStatus(),
            fontFamily = MgondiFont,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        BackspaceKey(controller)
        Spacer(Modifier.width(2.dp))
        MicCircle(controller, size = 34.dp, localeTag = true)
    }
}

/** State 5 — order (ऊपर→नीचे): 🎤 | ⌫ | ⌨ | badge */
@Composable
private fun VerticalPillContent(controller: VoiceWidgetController, state: VoiceWidgetState.VerticalPill) {
    Column(
        Modifier.fillMaxSize().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MicCircle(controller, size = 34.dp, localeTag = true)
        BackspaceKey(controller)
        WidgetIcon("⌨", contentDescription = "Show keyboard") { controller.onKeyboardTap() }
        Spacer(Modifier.weight(1f))
        LanguageBadge(controller, big = false)
    }
}

// ══════════════════════════ State 3 — menu ══════════════════════════

@Composable
private fun VoiceMenu(controller: VoiceWidgetController, state: VoiceWidgetState.MenuOpen) {
    val items = remember(state) { VoiceWidgetTransitions.menuItems(state) }
    val over = state.over
    val xDp = when (over) {
        is VoiceWidgetState.HorizontalPill -> over.x
        is VoiceWidgetState.VerticalPill -> over.x
        else -> 8f
    }
    val yDp = when (over) {
        is VoiceWidgetState.HorizontalPill -> over.y - 8f
        is VoiceWidgetState.VerticalPill -> over.y - 8f
        else -> 200f
    }
    val density = LocalDensity.current

    // upward gravity: menu pill ke **upar** khulta hai
    Box(
        Modifier.fillMaxSize()
            .clickable { controller.onBackPressed() },          // bahar tap → dismiss
        contentAlignment = Alignment.TopStart,
    ) {
        Card(
            Modifier
                .offset {
                    IntOffset(
                        with(density) { xDp.dp.roundToPx() },
                        with(density) { (yDp - 320f).coerceAtLeast(0f).dp.roundToPx() },
                    )
                }
                .width(VoiceWidgetRules.MENU_WIDTH_DP.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                Text(
                    text = "Open more voice options",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
                items.forEach { item ->
                    MenuRow(
                        label = item.label(controller.isHindi()),
                        checked = false,
                    ) { controller.onMenuItem(item) }
                }
                // divider ke baad language list (Gboard pattern: <भाषा> (<देश>))
                Box(Modifier.fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .padding(vertical = 4.dp))
                controller.languages().forEachIndexed { i, lang ->
                    MenuRow(label = lang, checked = i == 0) { controller.onLanguageTap(lang) }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = MgondiFont, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (checked) Text("✓", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
}

// ══════════════════════════ State 4 — symbols overlay ══════════════════════════

@Composable
private fun SymbolsOverlay(controller: VoiceWidgetController, state: VoiceWidgetState.SymbolsOverlay) {
    val hindi = controller.isHindi()
    // peeche pill apni last status ke saath visible rehti hai (owner decision)
    controller.underlyingState()?.let { u ->
        when (u) {
            is VoiceWidgetState.HorizontalPill -> FloatingPill(controller, u)
            is VoiceWidgetState.VerticalPill -> FloatingPill(controller, u)
            else -> Unit
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))) {
        Card(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = SymbolCategoryHeader(hindi),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    WidgetIcon("✕", contentDescription = "Close menu") { controller.onSymbolsClose() }
                }
                val grid = remember(state.category, tickOf(controller)) { controller.symbolsFor(state.category) }
                grid.chunked(10).forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        line.forEach { sym ->
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { controller.onSymbolTap(sym) },
                                contentAlignment = Alignment.Center,
                            ) { Text(sym, fontFamily = MgondiFont, fontSize = 16.sp) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SymbolCategory.entries.forEach { c ->
                        val selected = c == state.category
                        Text(
                            text = c.label(hindi),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .clickable { controller.onSymbolCategoryTap(c) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun SymbolCategoryHeader(hindi: Boolean): String = if (hindi) "चिह्न" else "Symbols"

// ══════════════════════════ shared bits ══════════════════════════

@Composable
private fun LanguageBadge(controller: VoiceWidgetController, big: Boolean) {
    val tag = controller.localeTag
    Text(
        text = tag,
        fontFamily = MgondiFont,
        fontSize = if (big) 12.sp else 10.sp,
        maxLines = 1,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { controller.onBadgeTap() }     // hamburger/badge → menu
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = if (big) 6.dp else 4.dp),
    )
}

@Composable
private fun WidgetIcon(glyph: String, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontFamily = MgondiFont, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

/**
 * Pill ka ⌫ — **wahi maujooda repeat handler** (400ms initial, 70ms repeat) jo
 * keyboard ke backspace par hai. Naya timer nahi banaya (owner ka rule).
 */
@Composable
private fun BackspaceKey(controller: VoiceWidgetController) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.size(38.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            .then(
                KeyGestures.pointerInputFor(
                    key = "voice-backspace",
                    repeat = KeyGestures.isRepeatKind(KeyKind.BACKSPACE),
                    onPress = { haptics.performHapticFeedback(HapticFeedbackType.KeyPress) },
                    onTap = { controller.onBackspace() },
                    onLongPress = { },
                    onRelease = { },
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("⌫", fontFamily = MgondiFont, fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

/**
 * नीला mic circle — Google-colour jaisa gradient nahi (branding), par shape/state
 * behaviour wahi: LISTENING par pulse-ish colour, PAUSED par muted.
 * mic ke kone par chhota language tag (Gboard: `.label.secondary.text-size-very-tiny.for-widget`).
 */
@Composable
private fun MicCircle(controller: VoiceWidgetController, size: androidx.compose.ui.unit.Dp, localeTag: Boolean = false) {
    val mic = controller.micState()
    val haptics = LocalHapticFeedback.current
    val bg by animateColorAsState(
        targetValue = when (mic) {
            MicState.LISTENING -> MaterialTheme.colorScheme.primary
            MicState.PAUSED -> MaterialTheme.colorScheme.surfaceVariant
            MicState.ERROR -> MaterialTheme.colorScheme.error
            MicState.OFFLINE -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(VoiceWidgetRules.ANIM_FADE_MS),
        label = "micBg",
    )
    val fg = when (mic) {
        MicState.LISTENING -> MaterialTheme.colorScheme.onPrimary
        MicState.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onError
    }
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier.size(size).clip(CircleShape).background(bg)
                .then(
                    KeyGestures.pointerInputFor(
                        key = "voice-mic",
                        repeat = false,
                        onPress = { haptics.performHapticFeedback(HapticFeedbackType.KeyPress) },
                        onTap = { controller.onPillMicTap() },
                        onLongPress = { controller.onMicAccessPointTap() },
                        onRelease = { },
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (mic == MicState.LISTENING) "🎙" else "🎤", fontSize = (size.value * 0.45f).sp)
        }
        if (localeTag) {
            Text(
                text = controller.localeTag,
                fontSize = 7.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = (-2).dp, y = (-2).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(horizontal = 2.dp),
            )
        }
    }
}

/** "Hold and drag to move toolbar" — Gboard APK se verbatim tooltip (pehli drag par). */
@Composable
private fun DragTooltip(x: Float, y: Float) {
    val density = LocalDensity.current
    Box(
        Modifier.offset {
            IntOffset(
                with(density) { x.dp.roundToPx() },
                with(density) { (y - 56f).coerceAtLeast(0f).dp.roundToPx() },
            )
        }
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Hold and drag to move toolbar",
            fontFamily = MgondiFont,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

private fun tickOf(controller: VoiceWidgetController): Int = controller.revision
