package com.mgboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgboard.keyboard.data.EmojiData
import com.mgboard.keyboard.ime.KeyboardModel
import com.mgboard.keyboard.toolbar.SymbolPanelCategory
import com.mgboard.keyboard.toolbar.SymbolPanelData
import com.mgboard.keyboard.voice.VoiceSymbols

/**
 * Expression panel (Emoji/GIF/Stickers) — RESEARCH.md §5.1.
 *
 * Gboard ke components:
 *  - tabs: **Emoji · GIF · Stickers · Favorites · Recents**
 *  - search box: *".expression-search-box"* → "Search emoji"
 *  - categories: `.expression-category-item` (yahan side nav)
 *  - footer: *"Close the emoji panel"*
 *
 * GIF/Stickers **gated** hain (hide-nothing): tab dikhta hai, tap par Gboard ka
 * verbatim reason milta hai. Emoji data `KeyboardData.EmojiData` se aata hai —
 * wahi jo web app mein hai (9 categories, 297 emoji).
 */
@Composable
fun EmojiPanel(model: KeyboardModel) {
    val hindi = model.settings.uiHindi
    var tab by remember { mutableStateOf("Emoji") }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EmojiData.CATEGORIES.keys.first()) }

    val gated = SymbolPanelData.tabGateReason(tab, hindi)

    Column(Modifier.fillMaxWidth().height(230.dp)) {
        // ── tabs ────────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolPanelData.EXPRESSION_TABS.forEach { t ->
                val selected = t == tab
                Text(
                    text = t,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { tab = t }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text("⌫", fontSize = 15.sp, modifier = Modifier.padding(horizontal = 8.dp)
                .clickable { model.engine.backspace() })
        }

        if (gated != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⊘", fontSize = 26.sp, color = MaterialTheme.colorScheme.outline)
                    Text(gated, fontSize = 12.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
            }
            return@Column
        }

        // ── search box (.expression-search-box) ───────────────────────────────
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(44.dp),
            placeholder = { Text(if (hindi) "इमोजी खोजें" else "Search emoji", fontSize = 12.sp) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontFamily = MgondiFont),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )

        Row(Modifier.fillMaxWidth().weight(1f)) {
            // ── category side nav (.expression-category-item) ──────────────────
            Column(
                Modifier.width(44.dp).verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            ) {
                EmojiData.CATEGORIES.keys.forEach { c ->
                    val icon = c.substringBefore(" ")
                    Text(
                        text = icon, fontSize = 17.sp, textAlign = TextAlign.Center,
                        color = if (c == category) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            .clickable { category = c },
                    )
                }
            }

            // ── emoji grid ─────────────────────────────────────────────────────
            val emoji = remember(query, category, tab) {
                val all = when (tab) {
                    "Favorites" -> model.favoriteEmoji()
                    "Recents" -> model.recentEmoji()
                    else -> EmojiData.CATEGORIES[category].orEmpty()
                }
                if (query.isBlank()) all else all.filter { it.contains(query) }
            }
            if (emoji.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (tab == "Favorites") (if (hindi) "कोई पसंदीदा इमोजी नहीं" else "No favorite emoji")
                         else if (hindi) "कोई इमोजी नहीं मिला" else "No emoji found",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(38.dp), modifier = Modifier.fillMaxSize()) {
                    items(emoji) { e ->
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    model.insertFromPanel(e)
                                    model.rememberRecentEmoji(e)
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
            }
        }
    }
}

/**
 * Symbols panel — Gboard ki **8 categories** (§5.2, APK se exact):
 * `Numbers · Brackets · Arrows · Mathematics · List · Shapes · Emoticons · Recent`
 *
 * Numbers category mein **Gondi digits** (U+11D50–U+11D59) aate hain — wahi jo
 * keyboard ke numbers panel mein hain (bundled font ke saath).
 */
@Composable
fun SymbolsPanel(model: KeyboardModel) {
    val hindi = model.settings.uiHindi
    var category by remember { mutableStateOf(SymbolPanelCategory.RECENT) }

    Column(Modifier.fillMaxWidth().height(230.dp)) {
        // category tabs
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolPanelCategory.entries.forEach { c ->
                val selected = c == category
                Text(
                    text = c.label(hindi),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp)).clickable { category = c }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text("⌫", fontSize = 15.sp, modifier = Modifier.padding(horizontal = 8.dp)
                .clickable { model.engine.backspace() })
        }

        val grid = remember(category, model.rev) { model.symbolsPanelGrid(category) }
        LazyVerticalGrid(columns = GridCells.Adaptive(40.dp), modifier = Modifier.fillMaxSize()) {
            items(grid) { sym ->
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { model.insertFromPanel(sym); model.rememberRecentSymbol(sym) },
                    contentAlignment = Alignment.Center,
                ) { Text(sym, fontFamily = MgondiFont, fontSize = 17.sp) }
            }
        }
    }
}

/**
 * Clipboard panel (§5.3 "Show clipboard" / "Close clipboard panel").
 *
 * History `SgStore.clipboardHistory()` se aati hai (web ke `cpLoad()` jaisa
 * `[{text, ts}]` shape). Tap = paste, long-press style ✕ = delete.
 * Gboard flag `enable_clipboard_content_suggestion` isi ko drive karta hai.
 */
@Composable
fun ClipboardPanel(model: KeyboardModel) {
    val hindi = model.settings.uiHindi
    val entries = remember(model.rev, model.clipboardRevision) { model.settings.clipboardHistory() }

    Column(Modifier.fillMaxWidth().height(200.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (hindi) "क्लिपबोर्ड इतिहास" else "Clipboard history",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (entries.isNotEmpty()) {
                Text(if (hindi) "सब हटाएं" else "Clear all", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { model.clearClipboardHistory() })
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 24.sp)
                    Text(
                        text = if (hindi) "अभी कोई क्लिप सेव नहीं है" else "No clips saved yet",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                entries.forEach { text ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { model.pasteFromClipboard(text) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text, fontFamily = MgondiFont, fontSize = 13.sp, maxLines = 2,
                            modifier = Modifier.weight(1f))
                        Text("✕", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 8.dp)
                                .clickable { model.removeClipboardEntry(text) })
                    }
                }
            }
        }
    }
}
