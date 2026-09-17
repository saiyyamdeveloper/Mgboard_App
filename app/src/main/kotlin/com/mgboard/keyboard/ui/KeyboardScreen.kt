package com.mgboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgboard.keyboard.R
import com.mgboard.keyboard.grid.GridIcons
import com.mgboard.keyboard.grid.GridMenu
import com.mgboard.keyboard.ime.KeyboardModel
import com.mgboard.keyboard.layout.KeyKind
import com.mgboard.keyboard.layout.KeySpec
import com.mgboard.keyboard.layout.KeyboardLayout

/**
 * Masaram Gondi font (APK mein bundle) — Android ke system fonts mein U+11D00+
 * script nahi hoti, isliye yeh zaroori hai warna Gondi glyph tofu (□) dikhte.
 * Fallback chain web ke `font-family: 'Mgondi', 'Noto Sans Masaram Gondi',
 * 'Noto Sans Devanagari', sans-serif` ke barabar hai.
 */
val MgondiFont = FontFamily(
    Font(R.font.noto_sans_masaram_gondi),
    Font(R.font.mgondi),
)

/**
 * Keyboard view ka host. Do implementations hain:
 *  - [com.mgboard.keyboard.ime.MgBoardIme]  : real IME (editor se text sync karta hai)
 *  - Preview (MainActivity)                 : in-app preview, koi IME enable kiye bina
 *
 * Isi se ek hi layout code dono jagah chalta hai — behaviour drift nahi hota.
 */
interface KeyboardHost {
    /** Har key action ke baad text/cursor dobara editor se padho. */
    fun syncFromEditor()
    /** Enter key ka contextual label (Go/Send/Search/Done/⏎). */
    val enterLabel: String
    /**
     * Toolbar ka 🎤 access point — voice toolbar (Gboard "Assistant voice typing")
     * kholta hai. Preview host par no-op.
     */
    fun onMicTap() {}
}

/** In-app preview ka host — sync karne ko editor hai hi nahi. */
object PreviewHost : KeyboardHost {
    override fun syncFromEditor() {}
    override val enterLabel: String = "⏎"
}

/**
 * Keyboard ka poora view — web ke `render()` ka Compose equivalent.
 *
 *  1. toolbar / suggestion strip (pinned access points; capacity 5 portrait / 6 landscape,
 *     valid range 3–8; undo-redo pills)
 *  2. key rows — row1 dynamic vowel↔matra, rows 2–5, control row web ke flex ratios
 *     (1.57 / 1.0 / 1.0 / 4.4 / 1.0 / 1.58) se
 *  3. one-handed dock (left/right) aur height ratio (0.5–2.0)
 *  4. popups: long-press alternate (300ms), period ke 16 symbols, grid menu (21 tiles)
 *
 * NOTE: bottom gap (gesture-navigation padding) yahan nahi lagta — woh View level par
 * `GestureNavPaddingController` WindowInsets se lagata hai (padding-project, Gboard-exact).
 */
/**
 * @param voice optional voice-toolbar controller. IME apna deta hai; launcher-preview
 * bhi deta hai (taaki 5 states bina device ke test ho jayein). `null` = voice layer band.
 */
@Composable
fun KeyboardScreen(
    model: KeyboardModel,
    host: KeyboardHost,
    tick: Int = 0,
    voice: com.mgboard.keyboard.voice.VoiceWidgetController? = null,
) {
    val settings = model.settings
    val rows = remember(model.rev, tick) { model.rows() }

    val ratio = settings.heightRatio.toFloat().coerceIn(0.5f, 2.0f)
    val keyboardHeight = with(LocalDensity.current) { (250.dp * ratio).toDp() }

    // one-handed: keyboard sikud kar ek side, doosri side dock handle (Gboard jaisa)
    val oneHanded = settings.oneHanded
    val dockWeight = if (oneHanded.isEmpty()) 0f else 0.22f

    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth()) {
            // toolbar-project: Gboard-style keyboard toolbar (access points + chips)
            if (settings.toolbarFlags.showToolbar && settings.toolbarVisible) {
                KeyboardToolbar(model = model, host = host)
            }

            // Gboard ka translate panel keyboard ke *upar* khulta hai — keyboard
            // type karte rehta hai (typed text translate buffer mein jaata hai).
            if (model.toolbarPanel == com.mgboard.keyboard.toolbar.ToolbarPanel.TRANSLATE) {
                TranslatePanel(model)
            }

            // baaki panels: keyboard body uski jagah panel dikhata hai (Gboard)
            if (model.toolbarPanel != com.mgboard.keyboard.toolbar.ToolbarPanel.NONE &&
                model.toolbarPanel != com.mgboard.keyboard.toolbar.ToolbarPanel.TRANSLATE
            ) {
                Box(Modifier.fillMaxWidth().height(keyboardHeight)) {
                    ToolbarPanelHost(model = model, host = host)
                }
            } else {
                Row(Modifier.fillMaxWidth().height(keyboardHeight)) {
                    if (oneHanded == "right") DockArea(model, dockWeight)
                    Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 2.dp)) {
                        rows.forEachIndexed { ri, row ->
                            KeyRow(model, host, row, ri, Modifier.weight(1f))
                        }
                    }
                    if (oneHanded == "left") DockArea(model, dockWeight)
                }
            }

            if (oneHanded.isNotEmpty()) OneHandedBar(model)
        }

        // popups sabse upar
        model.popupChar?.let { ch -> AlternatePopup(ch) { model.onPopupPick(it) } }
        if (model.popupMulti.isNotEmpty()) MultiPopup(model.popupMulti) { model.onPopupPick(it) }
        if (model.gridOpen) GridMenuPopup(model)

        // voice toolbar (additive layer) — 5 states: panel, pills, menu, symbols
        if (voice != null) com.mgboard.keyboard.voice.VoiceWidgetLayer(controller = voice, tick = tick)
    }
}

// ══════════════════════════ rows & keys ══════════════════════════

@Composable
private fun KeyRow(
    model: KeyboardModel,
    host: KeyboardHost,
    row: List<KeySpec?>,
    rowIndex: Int,
    modifier: Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        row.forEachIndexed { ci, spec ->
            if (spec == null) {
                Spacer(Modifier.weight(1f))
            } else {
                KeyView(model, host, spec, rowIndex, ci, Modifier.weight(spec.weight))
            }
        }
    }
}

@Composable
private fun KeyView(
    model: KeyboardModel,
    host: KeyboardHost,
    spec: KeySpec,
    rowIndex: Int,
    colIndex: Int,
    modifier: Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val settings = model.settings
    val display = keyDisplay(model, host, spec)
    val keyId = remember(rowIndex, colIndex, spec.kind, spec.glyph) {
        "$rowIndex-$colIndex-${spec.kind}-${spec.glyph.hashCode()}"
    }

    Box(
        modifier
            .fillMaxHeight()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(keyBackground(spec))
            .then(
                KeyGestures.pointerInputFor(
                    key = keyId,
                    repeat = KeyGestures.isRepeatKind(spec.kind),
                    onPress = {
                        if (settings.hapticEnabled) haptics.performHapticFeedback(HapticFeedbackType.KeyPress)
                    },
                    onTap = { model.onKeyTap(spec); host.syncFromEditor() },
                    onLongPress = { model.onKeyLongPress(spec) },
                    onRelease = { model.onKeyRelease(spec) },
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Gboard-jaise: special keys par colored emoji nahi, monochrome vector icons
        val iconId: String? = when {
            spec.kind == KeyKind.GLOBE -> "imeSwitch"
            spec.kind == KeyKind.BACKSPACE -> "backspace"
            spec.kind == KeyKind.EMOJI -> "emoji"
            spec.kind == KeyKind.ENTER && display == SEARCH_ENTER_SENTINEL -> "search"
            else -> null
        }
        if (iconId != null) {
            MgIcon(iconId, size = 18.dp, tint = keyForeground(spec))
        } else {
            Text(
                text = display,
                fontFamily = MgondiFont,
                fontSize = fontSizeFor(spec, display),
                fontWeight = if (spec.kind == KeyKind.SPACE) FontWeight.Medium else FontWeight.Normal,
                color = keyForeground(spec),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** Enter key ka search label sentinel — KeyView isse monochrome search icon banata hai. */
private const val SEARCH_ENTER_SENTINEL = "\uD83D\uDD0D"

private fun keyDisplay(model: KeyboardModel, host: KeyboardHost, spec: KeySpec): String = when (spec.kind) {
    KeyKind.SPACE -> model.engine.spaceLabel
    KeyKind.VOCALIC_R -> model.engine.vocalicRGlyph
    KeyKind.BACKSPACE -> "⌫"
    KeyKind.ENTER -> host.enterLabel
    KeyKind.SHIFT -> "⇧"
    KeyKind.GLOBE -> ""      // KeyView MgIcon("imeSwitch") render karta hai
    else -> spec.glyph.ifEmpty { spec.label }
}

@Composable
private fun keyBackground(spec: KeySpec) = when (spec.semantic) {
    "danger" -> MaterialTheme.colorScheme.errorContainer
    "special" -> MaterialTheme.colorScheme.secondaryContainer
    else -> if (spec.kind == KeyKind.SPACE) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
}

@Composable
private fun keyForeground(spec: KeySpec) = when (spec.semantic) {
    "danger" -> MaterialTheme.colorScheme.onErrorContainer
    "special" -> MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.onSurface
}

private fun fontSizeFor(spec: KeySpec, display: String): TextUnit = when {
    spec.kind == KeyKind.SPACE -> 13.sp
    display.length > 3 -> 11.sp
    display.length > 1 -> 16.sp
    else -> 20.sp
}

// ══════════════════════════ one-handed dock ══════════════════════════

@Composable
private fun DockArea(model: KeyboardModel, weight: Float) {
    Column(
        Modifier.weight(weight).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val hindi = model.settings.uiHindi
        Text(if (model.settings.oneHanded == "left") "◀" else "▶",
            fontSize = 18.sp,
            modifier = Modifier.padding(6.dp).clickable { model.settings.cycleOneHanded() })
        Text("✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(6.dp).clickable { model.settings.setOneHanded("") })
        Text(if (hindi) "एक हाथ" else "One-hand", fontSize = 9.sp,
            color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OneHandedBar(model: KeyboardModel) {
    val hindi = model.settings.uiHindi
    Row(
        Modifier.fillMaxWidth().height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(if (hindi) "एक हाथ वाला मोड" else "One-handed mode", fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◀", fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp).clickable { model.settings.setOneHanded("left") })
            Text("▶", fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp).clickable { model.settings.setOneHanded("right") })
            Text(if (hindi) "✕ बंद करें" else "✕ Exit", fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp).clickable { model.settings.setOneHanded("") })
        }
    }
}

// ══════════════════════════ popups ══════════════════════════

@Composable
private fun AlternatePopup(ch: String, onPick: (String) -> Unit) {
    Box(Modifier.fillMaxSize().clickable { onPick(ch) }, contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) {
            Text(
                ch, fontFamily = MgondiFont, fontSize = 28.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

@Composable
private fun MultiPopup(chars: List<String>, onPick: (String) -> Unit) {
    Box(Modifier.fillMaxSize().clickable { onPick("") }, contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(10.dp)) {
                chars.chunked(8).forEach { line ->
                    Row {
                        line.forEach { c ->
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).clickable { onPick(c) },
                                contentAlignment = Alignment.Center,
                            ) { Text(c, fontFamily = MgondiFont, fontSize = 18.sp) }
                        }
                    }
                }
            }
        }
    }
}

/** Grid menu — Gboard "More features" (21 tiles: 20 grid + fixed mic slot). */
@Composable
private fun GridMenuPopup(model: KeyboardModel) {
    val tiles = remember(model.rev) { model.gridTiles() }
    Box(Modifier.fillMaxSize().clickable { model.closeGrid() }, contentAlignment = Alignment.BottomCenter) {
        Card(
            Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(8.dp)) {
                Text(
                    text = model.moreLabel(),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                )
                Text(
                    text = if (model.settings.uiHindi) "यहाँ सभी keyboard सुविधाएँ पाएँ"
                           else "Access all keyboard features here",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                )
                tiles.chunked(GridMenu.PER_PAGE).forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        line.forEach { t ->
                            Column(
                                Modifier.width(64.dp).clip(RoundedCornerShape(8.dp))
                                    .clickable { model.onGridTile(t) }.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                MgIcon(
                                    id = if (t.enabled) t.id else "blocked",
                                    size = 20.dp,
                                    tint = if (t.enabled) mgIconTint()
                                           else MaterialTheme.colorScheme.outline,
                                )
                                Text(
                                    text = model.label(t), fontSize = 10.sp, textAlign = TextAlign.Center,
                                    maxLines = 2, fontFamily = MgondiFont,
                                    color = if (t.enabled) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
