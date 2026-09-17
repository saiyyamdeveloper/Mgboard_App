package com.mgboard.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgboard.keyboard.engine.TypingEngine
import com.mgboard.keyboard.ime.KeyboardModel
import com.mgboard.keyboard.model.SgItems
import com.mgboard.keyboard.model.SgPages
import com.mgboard.keyboard.ui.KeyboardScreen
import com.mgboard.keyboard.ui.MgTheme
import com.mgboard.keyboard.ui.MgondiFont
import com.mgboard.keyboard.ui.PreviewHost
import com.mgboard.keyboard.ui.PreviewSettingsSource
import com.mgboard.keyboard.ui.PreviewTextInput
import com.mgboard.keyboard.ui.PreviewVoiceHost
import com.mgboard.keyboard.ui.KeyboardHost
import com.mgboard.keyboard.voice.VoiceWidgetController
import com.mgboard.keyboard.ui.ThemeOptions

/**
 * Launcher activity — teen kaam karti hai:
 *
 *  1. **Setup**: MgBoard IME ko system mein enable karne aur chunne ke steps
 *     (Gboard jaisa hi flow, sirf hamari service ke liye).
 *  2. **Live preview**: asli typing engine + asli layout in-app chalta hai, taaki
 *     bina IME enable kiye nukta composition / युक्त / backspace / undo verify ho sake.
 *  3. **Settings**: poora Gboard-parity settings tree (%d pages · %d items).
 *
 * Preview wahi [KeyboardScreen] use karta hai jo IME karta hai — isliye dono ka
 * behaviour drift nahi kar sakta.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MgTheme { Home() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home() {
    val ctx = LocalContext.current
    var theme by remember { mutableStateOf("system") }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(ctx.getString(R.string.app_name), fontWeight = FontWeight.SemiBold)
            })
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()),
        ) {
            MgTheme(forcedTheme = theme) {
                SetupCard()
                LivePreview(theme = theme, onThemeChange = { theme = it })
                SettingsCard()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ══════════════════════════════ 1. setup ══════════════════════════════

@Composable
private fun SetupCard() {
    val ctx = LocalContext.current
    val enabled = remember { isMgBoardEnabled(ctx) }
    SectionCard(title = "⌨️ " + ctx.getString(R.string.ime_enable_title)) {
        Text(ctx.getString(R.string.ime_enable_step1), fontSize = 13.sp)
        Text(ctx.getString(R.string.ime_enable_step2), fontSize = 13.sp)
        Text(ctx.getString(R.string.ime_enable_step3), fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }) { Text("Open keyboard settings") }
            OutlinedButton(onClick = { showImePicker(ctx) }) { Text("🌐 Choose keyboard") }
        }
        Text(
            text = if (enabled) "✓ MgBoard service system mein enabled hai"
                   else "○ MgBoard abhi enabled nahi hai — pehle upar wala step karein",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ══════════════════════════════ 2. live preview ══════════════════════════════

@Composable
private fun LivePreview(theme: String, onThemeChange: (String) -> Unit) {
    val ctx = LocalContext.current

    val input = remember { PreviewTextInput() }
    val settings = remember { PreviewSettingsSource(onToast = {}) }
    val model = remember {
        KeyboardModel(TypingEngine(input), settings).also { it.onEditorSynced() }
    }
    var out by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready — keys dabakar type karein") }
    // har control change par tick++ → poora preview recompose (settings values
    // plain fields hain, isliye Compose ko explicit signal chahiye)
    var tick by remember { mutableStateOf(0) }
    fun refresh() { tick++; model.bump(); out = input.text; status = describe(model) }
    fun refreshTick() { tick++; out = input.text }

    // voice toolbar preview — poora UI (5 states, drag, menu, symbols) bina device ke
    val voiceHost = remember { PreviewVoiceHost(model, input) }
    val voice = remember {
        VoiceWidgetController(voiceHost).also { c -> voiceHost.onToast = { msg -> status = msg } }
    }
    val previewHost = remember {
        object : KeyboardHost {
            override fun syncFromEditor() {}
            override val enterLabel: String = "⏎"
            override fun onMicTap() { voice.onMicAccessPointTap(); refreshTick() }
        }
    }

    // engine ke har change par output text refresh karo
    model.onChange = { out = input.text; status = describe(model) }

    SectionCard(title = "🔍 Live preview (asli engine)") {
        // output box — Gondi glyph dikhein, isliye bundled font
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(
                text = out.ifEmpty { "…" },
                fontFamily = MgondiFont,
                fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth().padding(12.dp).height(34.dp),
            )
        }
        Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(10.dp))

        // keyboard khud — IME wala hi composable
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            // tick padhne se recompose guaranteed hai
            val t = tick
            KeyboardScreen(model = model, host = previewHost, tick = t, voice = voice)
        }

        Spacer(Modifier.height(12.dp))

        // controls: height / one-handed / theme / toolbar / language
        Text("Keyboard height  ${(settings.heightRatio * 100).toInt()}%   ·   tick $tick", fontSize = 12.sp)
        Slider(
            value = settings.heightRatio.toFloat(),
            onValueChange = { settings.heightRatio = it.toDouble().coerceIn(0.5, 2.0); refresh() },
            valueRange = 0.5f..2.0f,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { settings.cycleOneHanded(); refresh() }) {
                Text("One-handed: " + settings.oneHanded.ifEmpty { "off" })
            }
            OutlinedButton(onClick = { settings.toolbarVisible = !settings.toolbarVisible; refresh() }) {
                Text(if (settings.toolbarVisible) "Toolbar ON" else "Toolbar OFF")
            }
            OutlinedButton(onClick = { input.clear(); model.onEditorSynced(); refresh(); out = "" }) { Text("Clear") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeOptions.ALL.forEach { t ->
                OutlinedButton(onClick = { settings.theme = t; onThemeChange(t); refresh() }) {
                    Text(ThemeOptions.label(t, hindi = false), fontSize = 11.sp)
                }
            }
        }

        // ── voice toolbar (Gboard-style pill) ke controls ────────────────────────
        Text("🎙 Voice toolbar (Gboard-style pill)", fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { voice.onMicAccessPointTap(); refreshTick() }) {
                Text("🎤 Open voice panel")
            }
            OutlinedButton(onClick = { voice.onChevronTap(); refreshTick() }) { Text("⌄⌄ Pill") }
            OutlinedButton(onClick = { voice.onBadgeTap(); refreshTick() }) { Text("☰ Menu") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = {
                voice.preferOffline = !voice.preferOffline; refreshTick()
            }) { Text(if (voice.preferOffline) "Offline: ON" else "Offline: OFF") }
            OutlinedButton(onClick = { voiceHost.dragLocked = !voiceHost.dragLocked; refreshTick() }) {
                Text(if (voiceHost.dragLocked) "Drag: locked" else "Drag: free")
            }
            OutlinedButton(onClick = { voiceHost.resetPosition(); refreshTick() }) { Text("Reset position") }
        }
        Text(
            text = "Status: " + voice.displayStatus() + "  ·  state: " + voice.state.javaClass.simpleName,
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { settings.uiHindi = !settings.uiHindi; refresh() }) {
                Text(if (settings.uiHindi) "UI: हिंदी" else "UI: English")
            }
            OutlinedButton(onClick = {
                // capacity preview: 3–8 (Gboard rule)
                val next = ((settings.storedCapacity ?: 5) % 8) + 1
                settings.storedCapacity = next.coerceIn(3, 8)
                refresh()
            }) { Text("Strip icons: " + (settings.storedCapacity ?: 5)) }
            OutlinedButton(onClick = {
                settings.landscape = !settings.landscape
                refresh()
            }) { Text(if (settings.landscape) "Landscape" else "Portrait") }
        }
    }
}

private fun describe(model: KeyboardModel): String {
    val e = model.engine
    val parts = mutableListOf(
        "mode=${e.kbMode.name.lowercase()}",
        "panel=${e.panel.name.lowercase()}",
        "row1=${e.row1.name.lowercase()}",
    )
    if (e.yukt != null) parts += "युक्त pending"
    if (e.vcCons != null) parts += "vocalic-R combo"
    if (e.kbMode == com.mgboard.keyboard.engine.KbMode.QWERTY) parts += "shift=${model.shift.mode.name.lowercase()}"
    return parts.joinToString(" · ")
}

// ══════════════════════════════ 3. settings ══════════════════════════════

@Composable
private fun SettingsCard() {
    val ctx = LocalContext.current
    SectionCard(title = "⚙️ " + ctx.getString(R.string.settings_title)) {
        Text(
            ctx.getString(R.string.home_subtitle, SgPages.ALL.size, SgItems.ALL.size),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) }) {
            Text(ctx.getString(R.string.home_open_settings))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

// ══════════════════════════════ helpers ══════════════════════════════

/** Kya MgBoard IME system mein enable hai? */
private fun isMgBoardEnabled(ctx: Context): Boolean {
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
}

/** System ka IME picker — globe icon jaisa hi behaviour. */
private fun showImePicker(ctx: Context) {
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    runCatching { imm.showInputMethodPicker() }
}
