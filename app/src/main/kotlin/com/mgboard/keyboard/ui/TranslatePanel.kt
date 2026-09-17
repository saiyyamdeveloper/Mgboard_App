// android-only: Compose UI — translate panel (ML Kit on-device engine ke saath)
package com.mgboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgboard.keyboard.ime.KeyboardModel
import com.mgboard.keyboard.translate.TranslateEngine
import com.mgboard.keyboard.translate.TranslateLang
import com.mgboard.keyboard.translate.TranslatePhase
import com.mgboard.keyboard.translate.isDirectEnglishPair

/**
 * Gboard ka translate panel — keyboard ke **upar** khulta hai, keyboard type karte
 * rehta hai (typed text editor mein nahi, translate buffer mein jaata hai), aur ✓
 * dabane par translated text editor mein insert hota hai.
 *
 * Gboard ke UI rules (research: TRANSLATE-GIF-FEASIBILITY.md §1.2):
 *  - top-left  = source language picker ("Detect language" option ke saath)
 *  - beech mein = ⇄ swap — *"You can also tap the icon in the middle to switch the
 *    two languages' positions"*
 *  - top-right = target language picker
 *  - bottom-right = ✓ translated text insert
 *
 * Engine **ML Kit Translate** (on-device): `hi` aur `en` officially supported hain,
 * ~30 MB model ek baar download, phir offline. Text device se bahar nahi jaata —
 * isliye panel par "On-device" badge dikhta hai.
 */
@Composable
fun TranslatePanel(model: KeyboardModel) {
    val ctrl = model.translate
    val s = ctrl.session
    val hindi = model.settings.uiHindi
    // model.rev har controller bump par badhta hai (onChanged = { bump() })
    val tick = model.rev

    Column(
        Modifier.fillMaxWidth().height(212.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // ── header: [source ▾]  ⇄  [target ▾] ────────────────────────────────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LangPicker(
                label = if (s.autoDetect)
                    (if (hindi) "भाषा पहचानें" else "Detect language")
                else s.src.label(hindi),
                detected = s.detected?.label(hindi),
                autoDetect = s.autoDetect,
                onPick = { lang ->
                    if (lang == null) ctrl.setAutoDetect(true) else ctrl.setSource(lang)
                    model.settings.saveTranslateLanguages(ctrl.session.src.code, ctrl.session.tgt.code)
                },
                modifier = Modifier.weight(1f),
                tick = tick,
                showDetect = true,
            )

            Box(
                Modifier.padding(horizontal = 6.dp).size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        ctrl.swap()
                        model.settings.saveTranslateLanguages(s.src.code, s.tgt.code)
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Gboard: beech ka icon dono languages interchange karta hai
                Text("⇄", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LangPicker(
                label = s.tgt.label(hindi),
                detected = null,
                autoDetect = false,
                onPick = { lang ->
                    if (lang != null) {
                        ctrl.setTarget(lang)
                        model.settings.saveTranslateLanguages(ctrl.session.src.code, lang.code)
                    }
                },
                modifier = Modifier.weight(1f),
                tick = tick,
                alignEnd = true,
                showDetect = false,
            )
        }

        // ── output / status ───────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            when {
                s.phase == TranslatePhase.READY && s.output.isNotEmpty() -> Column {
                    Text(
                        text = s.output,
                        fontFamily = MgondiFont,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    )
                    // ML Kit English-pivot: hi↔en direct, baaki pairs English se hoke jaate hain
                    if (!isDirectEnglishPair(s.src, s.tgt)) {
                        Text(
                            text = if (hindi) "English के ज़रिए अनुवादित" else "Translated via English",
                            fontSize = 9.sp, color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                s.phase == TranslatePhase.DOWNLOADING -> Column(
                    Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (hindi) "अनुवाद मॉडल डाउनलोड हो रहा है…"
                               else "Downloading translation model…",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { s.downloadProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                    Text(
                        text = "~${TranslateEngine.MODEL_SIZE_MB} MB · " +
                            (if (model.settings.translateWifiOnly)
                                if (hindi) "केवल वाई-फ़ाई पर" else "Wi-Fi only"
                             else if (hindi) "कोई भी नेटवर्क" else "any network"),
                        fontSize = 9.sp, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                s.phase == TranslatePhase.TRANSLATING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (hindi) "अनुवाद हो रहा है…" else "Translating…",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                s.phase == TranslatePhase.ERROR && s.error != null -> Column(
                    Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center,
                ) {
                    Text("⊘", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = s.error!!.label(hindi),
                        fontSize = 11.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    // hide-nothing: model download fail ho to dobara koshish ka raasta
                    if (s.error == com.mgboard.keyboard.translate.TranslateError.DOWNLOAD_FAILED ||
                        s.error == com.mgboard.keyboard.translate.TranslateError.ENGINE_UNAVAILABLE
                    ) {
                        Text(
                            text = if (hindi) "फिर कोशिश करें" else "Try again",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                .clickable { ctrl.downloadModel() },
                        )
                    }
                }

                s.input.isEmpty() -> Column(
                    Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (hindi) "अनुवाद करने के लिए नीचे कीबोर्ड से टाइप करें"
                               else "Type below to translate",
                        fontSize = 12.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (!s.modelReady && s.phase != TranslatePhase.DOWNLOADING) {
                        Text(
                            text = if (hindi)
                                "मॉडल डाउनलोड करें (~${TranslateEngine.MODEL_SIZE_MB} MB, एक बार)"
                               else
                                "Download model (~${TranslateEngine.MODEL_SIZE_MB} MB, once)",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                .clickable { ctrl.downloadModel() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                else -> Text(
                    text = s.input,
                    fontFamily = MgondiFont,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                )
            }
        }

        // ── footer: privacy badge + ✓ insert ──────────────────────────────────
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (hindi) "🔒 डिवाइस पर · टेक्स्ट बाहर नहीं जाता"
                       else "🔒 On-device · text never leaves this device",
                fontSize = 9.sp, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(
                        if (s.output.isEmpty()) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary,
                    )
                    .clickable(enabled = s.output.isNotEmpty()) { ctrl.insertOutput() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✓", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (s.output.isEmpty()) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** Source/target language picker — Gboard ke dropdown pickers jaisa. */
@Composable
private fun LangPicker(
    label: String,
    detected: String?,
    autoDetect: Boolean,
    onPick: (TranslateLang?) -> Unit,
    modifier: Modifier = Modifier,
    tick: Int = 0,
    alignEnd: Boolean = false,
    /** "Detect language" option sirf source picker mein hota hai (Gboard jaisa). */
    showDetect: Boolean = false,
) {
    // `tick` = model.rev — controller ke har bump par labels/detected fresh ho jaate hain
    var expanded by remember(tick) { mutableStateOf(false) }

    Box(modifier, contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.clip(RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontFamily = MgondiFont,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(3.dp))
                Text("\u25BE", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
            }
            // auto-detect ka natija (Gboard source detect karke dikhata hai)
            if (autoDetect && detected != null) {
                Text(detected, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (showDetect) {
                DropdownMenuItem(
                    text = { Text("Detect language \u00B7 \u092D\u093E\u0937\u093E \u092A\u0939\u091A\u093E\u0928\u0947\u0902", fontSize = 12.sp) },
                    onClick = { expanded = false; onPick(null) },
                )
            }
            TranslateLang.values().forEach { lang ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${lang.native} \u00B7 ${lang.enName}  (${lang.code})",
                            fontFamily = MgondiFont, fontSize = 12.sp,
                        )
                    },
                    onClick = { expanded = false; onPick(lang) },
                )
            }
        }
    }
}
