package com.mgboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgboard.keyboard.grid.GridIcons
import com.mgboard.keyboard.ime.KeyboardModel
import com.mgboard.keyboard.toolbar.AccessPoint
import com.mgboard.keyboard.toolbar.ToolbarPanel

/**
 * Keyboard toolbar / **suggestion strip** — Gboard ki apni definition (§9):
 *  1. suggestions dikhana
 *  2. features ka access dena (**access points**)
 *  3. contextual chips (undo/redo)
 *
 * Structure (research §2):
 *  - capacity **5 portrait / 6 landscape**, valid range **3–8**
 *  - order semicolon-list se (`access_points_showing_order`)
 *  - jo icons fit nahi hote → **features menu** (⊞, fixed right-side access point)
 *  - *"The Undo and Redo chips appear in the suggestion strip via access point,
 *     when user edits existing text."*
 *
 * Strip ke right side par: IME action (↵/send/search), IME switch (🌐, flag-gated),
 * features menu (⊞). Naam Gboard ke user-facing strings se hi rakhe gaye hain.
 */
@Composable
fun KeyboardToolbar(model: KeyboardModel, host: KeyboardHost, modifier: Modifier = Modifier) {
    val strip = remember(model.rev, model.toolbarPanel) { model.strip() }
    val hindi = model.settings.uiHindi

    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {

        // access-point education footer (Gboard flag: enable_access_point_education_footer)
        if (model.settings.toolbarFlags.accessPointEducationFooter && model.showEducationFooter) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hindi) "कीबोर्ड की सभी सुविधा यहां पाएं"
                           else "Access all keyboard features here",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 6.dp).clickable { model.dismissEducationFooter() })
            }
        }

        Row(
            Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── contextual undo/redo chips (existing text edit ke waqt) ─────────
            if (strip.undoChip) Chip(if (hindi) "↶ पहले जैसा करें" else "↶ Undo") {
                model.onAccessPoint(com.mgboard.keyboard.toolbar.AccessPoints.UNDO)
            }
            if (strip.redoChip) Chip(if (hindi) "↷ दोबारा करें" else "↷ Redo") {
                model.onAccessPoint(com.mgboard.keyboard.toolbar.AccessPoints.REDO)
            }

            // ── left: pinned access points (capacity ke andar) ──────────────────
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                strip.left.forEach { ap -> AccessPointItem(model, host, ap, hindi) }
            }

            // ── right: fixed access points ─────────────────────────────────────
            strip.right.forEach { ap -> AccessPointItem(model, host, ap, hindi, fixed = true) }
        }
    }
}

@Composable
private fun AccessPointItem(
    model: KeyboardModel,
    host: KeyboardHost,
    ap: AccessPoint,
    hindi: Boolean,
    fixed: Boolean = false,
) {
    val active = ap.panel != null && model.toolbarPanel == ap.panel
    Column(
        Modifier
            .padding(horizontal = 2.dp)
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    active -> MaterialTheme.colorScheme.primaryContainer
                    ap.gated -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .clickable {
                // panel already khula ho → wahi band karo (Gboard: "Open X" / "Close X")
                if (ap.panel != null && model.toolbarPanel == ap.panel) model.closeToolbarPanel()
                else model.onAccessPoint(ap)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            // gated access point par ⊘ — hide-nothing: icon dikhta hai, reason tap par milta hai
            text = if (ap.gated) "⊘" else ap.glyph,
            fontFamily = MgondiFont,
            fontSize = if (ap.glyph.length > 1) 12.sp else 17.sp,
            color = when {
                ap.gated -> MaterialTheme.colorScheme.outline
                active -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
        // chhota label — access points par naam dikhta hai (Gboard `.label.access-point-item`)
        Text(
            text = ap.label(hindi).take(9),
            fontSize = 7.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 3.dp).height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = MgondiFont, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/**
 * Toolbar se khulne wale panels ka host (§5) — keyboard body ki jagah dikhta hai.
 * Gboard pattern: har panel ka header + "Close X panel" footer.
 */
@Composable
fun ToolbarPanelHost(model: KeyboardModel, host: KeyboardHost) {
    val panel = model.toolbarPanel
    if (panel == ToolbarPanel.NONE) return
    val hindi = model.settings.uiHindi

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        // header (.keyboard-header-area.panel.v2)
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(panel.label(hindi), fontFamily = MgondiFont, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            // TalkBack label = Gboard ka close pattern
            Text("✕", fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 8.dp).clickable { model.closeToolbarPanel() })
        }

        when (panel) {
            ToolbarPanel.EMOJI -> EmojiPanel(model)
            ToolbarPanel.SYMBOLS -> SymbolsPanel(model)
            ToolbarPanel.CLIPBOARD -> ClipboardPanel(model)
            ToolbarPanel.EDIT_MENU -> EditMenuPanel(model)
            ToolbarPanel.MORE_KEYBOARD_OPTIONS -> MoreKeyboardOptionsPanel(model)
            ToolbarPanel.SELECT_MODE -> GatedPanel(model, panel)
            // TRANSLATE KeyboardScreen mein keyboard ke upar render hota hai
            // (Gboard pattern: panel + keyboard dono visible, typing translate
            //  buffer mein jaati hai) — isliye yahan kuch nahi.
            ToolbarPanel.TRANSLATE -> Unit
            ToolbarPanel.WRITING_TOOLS, ToolbarPanel.PROOFREAD -> GatedPanel(model, panel)
            ToolbarPanel.FEATURES_MENU -> FeaturesMenuPanel(model)
            ToolbarPanel.NONE -> Unit
        }

        // footer (Gboard: "Close the emoji panel" / "सिंबल वाला पैनल बंद करें")
        Text(
            text = panel.closeLabel(hindi),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                .clickable { model.closeToolbarPanel() },
        )
    }
}

/** Jo panel backend ke bina hai — hide-nothing: khulta hai aur verbatim reason dikhata hai. */
@Composable
private fun GatedPanel(model: KeyboardModel, panel: ToolbarPanel) {
    val hindi = model.settings.uiHindi
    val reason = when (panel) {
        ToolbarPanel.TRANSLATE, ToolbarPanel.WRITING_TOOLS ->
            if (hindi) "इस समय इस टूल का इस्तेमाल नहीं किया जा सकता. कृपया बाद में फिर कोशिश करें."
            else "Can't use this tool at the moment. Please try again later."
        ToolbarPanel.PROOFREAD ->
            if (hindi) "इस फ़ील्ड में टेक्स्ट का प्रूफ़रीड नहीं किया जा सकता"
            else "Can't proofread text in this field"
        ToolbarPanel.SELECT_MODE ->
            if (hindi) "चयन मोड के लिए editor mein selection support chahiye"
            else "Select mode needs an editor that supports selection"
        else -> if (hindi) "इस ऐप में यह कमांड उपलब्ध नहीं है" else "Command not available in this app"
    }
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⊘", fontSize = 26.sp, color = MaterialTheme.colorScheme.outline)
            Text(reason, fontSize = 12.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
    }
}

/** Features menu (⊞) — overflow access points + grid-menu tiles (§2 overflow rule). */
@Composable
private fun FeaturesMenuPanel(model: KeyboardModel) {
    val items = remember(model.rev) { model.featuresMenu() }
    val hindi = model.settings.uiHindi
    Column(
        Modifier.fillMaxWidth().height(200.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        items.chunked(6).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                line.forEach { ap ->
                    Column(
                        Modifier.width(58.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { model.onAccessPoint(ap) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (ap.gated) "⊘" else ap.glyph, fontFamily = MgondiFont, fontSize = 18.sp)
                        Text(ap.label(hindi), fontSize = 9.sp, maxLines = 2, textAlign = TextAlign.Center,
                            color = if (ap.gated) MaterialTheme.colorScheme.outline
                                    else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/** "More keyboard options" popup (§4 ki 4 popup families mein se ek). */
@Composable
private fun MoreKeyboardOptionsPanel(model: KeyboardModel) {
    val hindi = model.settings.uiHindi
    val options = remember(model.rev) { model.moreKeyboardOptions() }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        options.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable { item.onClick() }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.glyph, fontSize = 15.sp, modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.label(hindi), fontFamily = MgondiFont, fontSize = 13.sp)
                    if (item.summary(hindi).isNotEmpty()) {
                        Text(item.summary(hindi), fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Edit menu (§5.3 "Open edit menu") — select all / copy / cut / paste. */
@Composable
private fun EditMenuPanel(model: KeyboardModel) {
    val hindi = model.settings.uiHindi
    val items = remember(model.rev) { model.editMenuItems() }
    Row(
        Modifier.fillMaxWidth().height(120.dp).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Column(
                Modifier.width(70.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { item.onClick() }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(item.glyph, fontSize = 18.sp)
                Text(item.label(hindi), fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}
