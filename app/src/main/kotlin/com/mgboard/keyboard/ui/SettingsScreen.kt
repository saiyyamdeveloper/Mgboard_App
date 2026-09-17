package com.mgboard.keyboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgboard.keyboard.R
import com.mgboard.keyboard.model.SgItem
import com.mgboard.keyboard.model.SgItems
import com.mgboard.keyboard.model.SgPages
import com.mgboard.keyboard.model.SgType
import com.mgboard.keyboard.prefs.SgPrefs
import com.mgboard.keyboard.prefs.SgStore
import kotlin.math.roundToInt

/**
 * Gboard-jaisa multi-page Settings.
 *
 * 3 views (web `sgRender()` ke identical):
 *  1. HOME   — 14 page tiles (2 columns), har tile par live item count + first summary
 *  2. PAGE   — us page ke rows, controls ke saath (toggle / slider / action / gated / info)
 *  3. SEARCH — poore 90 items par substring search (EN + HI + keywords)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: SgPrefs, onExit: () -> Unit) {
    val context = LocalContext.current
    val app = remember { context.applicationContext }

    var rev by remember { mutableIntStateOf(0) }          // refresh trigger
    var pageId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val t = remember(rev) { SgText(app, prefs) }
    val actions = remember(rev, t) { SettingsActions(app, prefs, t) }

    BackHandler {
        when {
            query.isNotEmpty() -> query = ""
            pageId != null -> pageId = null
            else -> onExit()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = pageId?.let { id -> SgPages.byId(id)?.let(t::page) }
                            ?: app.getString(R.string.app_name),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (pageId != null || query.isNotEmpty()) {
                        IconButton(onClick = {
                            if (query.isNotEmpty()) query = "" else pageId = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (pageId == null) {
                        TextButton(onClick = {
                            prefs.setUiLang(if (t.hi) "en" else "hi"); rev++
                        }) {
                            Text(if (t.hi) "EN" else "हिंदी", fontWeight = FontWeight.Bold)
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            if (pageId == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; rev++ },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    placeholder = { Text(t.searchHint) },
                    singleLine = true,
                )
            }

            val scroll = rememberScrollState()
            when {
                query.isNotEmpty() -> SearchView(query, t = t, actions = actions,
                    prefs = prefs, rev = rev, bump = { rev++ }, scroll = scroll,
                    onOpenPage = { pageId = it }, context = app)

                pageId != null -> PageView(pageId!!, t, actions, prefs, rev, { rev++ }, scroll, app)

                else -> HomeGrid(t, scroll) { pageId = it }
            }
        }
    }
}

// ══════════════════════════════ HOME ══════════════════════════════════════════

@Composable
private fun HomeGrid(t: SgText, scroll: androidx.compose.foundation.ScrollState, onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SgPages.ALL.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p ->
                    val items = SgItems.ofPage(p.id)
                    Card(
                        modifier = Modifier.weight(1f).height(112.dp).clickable { onOpen(p.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            MgIcon(p.icon, size = 22.dp, tint = mgIconTint())
                            Spacer(Modifier.height(6.dp))
                            Text(t.page(p), fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                maxLines = 1)
                            Text(
                                text = t.str(R.string.sg_page_count, items.size),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = t.str(R.string.sg_home_footer, SgItems.ALL.size, SgPages.ALL.size),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

// ══════════════════════════════ PAGE ══════════════════════════════════════════

@Composable
private fun PageView(
    pageId: String,
    t: SgText,
    actions: SettingsActions,
    prefs: SgPrefs,
    rev: Int,
    bump: () -> Unit,
    scroll: androidx.compose.foundation.ScrollState,
    context: android.content.Context,
) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        SgItems.ofPage(pageId).forEach { it ->
            ItemRow(it, t, actions, prefs, rev, bump, context)
            HorizontalDivider(Modifier.padding(start = 56.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ══════════════════════════════ SEARCH ════════════════════════════════════════

@Composable
private fun SearchView(
    query: String,
    t: SgText,
    actions: SettingsActions,
    prefs: SgPrefs,
    rev: Int,
    bump: () -> Unit,
    scroll: androidx.compose.foundation.ScrollState,
    onOpenPage: (String) -> Unit,
    context: android.content.Context,
) {
    val results = remember(query, rev) { SgItems.search(query) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        if (results.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(t.str(R.string.sg_search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                text = t.str(R.string.sg_search_count, results.size),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            results.forEach { it ->
                ItemRow(it, t, actions, prefs, rev, bump, context, showPageChip = true, onOpenPage = onOpenPage)
                HorizontalDivider(Modifier.padding(start = 56.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ══════════════════════════════ ROW ═══════════════════════════════════════════

@Composable
private fun ItemRow(
    it: SgItem,
    t: SgText,
    actions: SettingsActions,
    prefs: SgPrefs,
    rev: Int,
    bump: () -> Unit,
    context: android.content.Context,
    showPageChip: Boolean = false,
    onOpenPage: ((String) -> Unit)? = null,
) {
    val gated = it.type == SgType.GATED
    val pendingIme = it.id in SgItems.PENDING_IME

    Row(
        Modifier.fillMaxWidth().clickable {
            if (actions.onTap(it)) bump()
        }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Text(
                text = when {
                    gated -> "⊘"
                    it.type == SgType.SLIDER -> "↕"
                    it.type == SgType.ACTION -> "▶"
                    it.type == SgType.INFO -> "ℹ"
                    else -> "•"
                },
                fontSize = 18.sp,
                color = if (gated) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
            )
        }

        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = t.label(it),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (gated) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.onSurface,
            )
            t.summary(it)?.let { s ->
                Text(s, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
            if (showPageChip) {
                val p = SgPages.byId(it.page)
                if (p != null && onOpenPage != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp).clickable { onOpenPage(p.id) },
                    ) {
                        MgIcon(p.icon, size = 13.dp, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = t.page(p),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (it.type == SgType.INFO) {
                val dyn = SgText.dynamicValue(context.applicationContext, it.id)
                dyn?.let { d ->
                    Text(d, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (pendingIme) {
                Text(t.str(R.string.sg_needs_ime), fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 3.dp))
            }
        }

        when (it.type) {
            SgType.TOGGLE -> ToggleControl(it, prefs, rev, bump)
            SgType.SLIDER -> {}   // slider neeche full-width
            SgType.ACTION -> TextButton(onClick = { if (actions.onTap(it)) bump() }) {
                Text(it.btn ?: t.str(R.string.sg_open))
            }
            else -> {}
        }
    }

    if (it.type == SgType.SLIDER) {
        SliderRow(it, prefs, bump, LocalContext.current)
    }
}

@Composable
private fun ToggleControl(it: SgItem, prefs: SgPrefs, rev: Int, bump: () -> Unit) {
    val key = it.key ?: it.id
    val checked = remember(rev, key) {
        when (it.id) {
            "sg_vibrate" -> prefs.getBool(SgPrefs.KEY_HAPTIC)
            else -> prefs.getBool(key)
        }
    }
    Switch(
        checked = checked,
        onCheckedChange = { v ->
            when (it.id) {
                // Web parity: 'Vibrate on keypress' purana haptic key use karta hai
                "sg_vibrate" -> prefs.setBool(SgPrefs.KEY_HAPTIC, v)
                else -> prefs.setBool(key, v)
            }
            bump()
        },
    )
}

@Composable
private fun SliderRow(it: SgItem, prefs: SgPrefs, bump: () -> Unit, context: android.content.Context) {
    val isHeight = it.id == "sg_kb_height"
    val cur = remember(it.id) { if (isHeight) prefs.getHeight().toFloat() else prefs.getPinCount().toFloat() }
    var v by remember(it.id) { mutableStateOf(cur) }
    val steps = (((it.max - it.min) / it.step).roundToInt()) - 1

    Column(Modifier.fillMaxWidth().padding(start = 56.dp, end = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = v,
                onValueChange = { nv ->
                    v = snap(nv, it.min.toFloat(), it.max.toFloat(), it.step.toFloat())
                },
                onValueChangeFinished = {
                    if (isHeight) prefs.setHeight(v.toDouble())
                    else {
                        prefs.setPinCount(v.roundToInt())
                        SgStore.trimPinnedTo(context.applicationContext, v.roundToInt())
                    }
                    bump()
                },
                valueRange = it.min.toFloat()..it.max.toFloat(),
                steps = if (steps > 0) steps else 0,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isHeight) String.format("%.2f×", v) else v.roundToInt().toString(),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(52.dp),
            )
        }
    }
}

/** Gboard-exact stepping (web: setKbHeightRatio / setPinnedCapacity clamp+step). */
private fun snap(value: Float, min: Float, max: Float, step: Float): Float {
    if (step <= 0f) return value.coerceIn(min, max)
    val n = ((value - min) / step).roundToInt()
    return (min + n * step).coerceIn(min, max)
}
