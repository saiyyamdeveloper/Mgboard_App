// android-only: Coil + Compose — GIF/Stickers tabs (expression panel §5.1)
package com.mgboard.keyboard.ui

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.mgboard.keyboard.ime.KeyboardModel
import com.mgboard.keyboard.media.BundledStickers
import com.mgboard.keyboard.media.KlipyApi
import com.mgboard.keyboard.media.MediaAvailability
import com.mgboard.keyboard.media.MediaItem
import com.mgboard.keyboard.media.MediaPage

/**
 * Expression panel ke **Stickers** tab — MgBoard ka apna bundled pack.
 *
 * Research: TRANSLATE-GIF-FEASIBILITY.md §2.2(d):
 *  - Gboard ke Stickers tab mein third-party packs aate hain; MgBoard **apna pack**
 *    bundle karta hai — offline, koi API key/attribution/rate-limit nahi
 *  - insertion **Commit Content API** se (`MediaCommitController`): editor ko
 *    `image/png` accept karna hota hai; na ho to tab khud Gboard ka verbatim
 *    reason dikha deta hai (yahan tak pahunchte hi nahi)
 *  - tap par `commitBundledSticker` — fail ho to field-reason toast
 */
@Composable
fun StickersPanel(model: KeyboardModel) {
    val hindi = model.settings.uiHindi
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val stickers = remember(query) { BundledStickers.search(query) }

    Column(Modifier.fillMaxWidth().height(230.dp)) {
        // search (Hindi + English tags dono match hote hain)
        MediaSearchField(
            value = query,
            onChange = { query = it },
            placeholder = if (hindi) "स्टिकर खोजें" else "Search stickers",
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (hindi) BundledStickers.PACK_TITLE_HI else BundledStickers.PACK_TITLE,
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${stickers.size}",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.outline,
            )
        }

        if (stickers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (hindi) "कोई स्टिकर नहीं मिला" else "No stickers found",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(68.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(stickers) { st ->
                    Box(
                        Modifier.size(68.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .clickable {
                                val ok = model.settings.commitBundledSticker(st)
                                if (!ok) {
                                    model.settings.toast(
                                        MediaAvailability.EditorUnsupported(st.mime).reason(hindi)
                                            ?: "The text field does not support GIF insertion from the keyboard",
                                    )
                                }
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(rawResId(context, st.res))
                                .crossfade(false)
                                .build(),
                            contentDescription = st.title,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // pack attribution (font license) — chhota footer
        Text(
            text = BundledStickers.ATTRIBUTION,
            fontSize = 8.sp, color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
    }
}

/**
 * Expression panel ka **GIF** tab — Klipy search (free-for-life API).
 *
 * Research: TRANSLATE-GIF-FEASIBILITY.md §2.2(a):
 *  - **Tenor API 30 Jun 2026 ko band** (Gboard ka source) — naye keyboards ke liye nahi;
 *    Klipy (ex-Tenor team, WhatsApp bhi migrate kar chuka) = replacement
 *  - testing-mode key = **100 requests/hour** → isliye search-as-you-type NAHI;
 *    explicit submit (⌨ search action) + trending ek baar
 *  - attribution zaroori → footer mein "GIFs · KLIPY"
 *  - key khali ho (placeholder mode) → **setup hint**, tab chhupta nahi (hide-nothing)
 *  - previews direct Klipy URLs se load hote hain (unke integration rules); commit ke
 *    liye transient delivery buffer `MediaCommitController` banata hai
 */
@Composable
fun GifPanel(model: KeyboardModel) {
    val hindi = model.settings.uiHindi
    val key = model.settings.klipyAppKey()
    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf<MediaPage?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun fetch(q: String?) {
        if (key.isBlank()) return
        loading = true
        failed = false
        val api = KlipyApi(key) { null }   // transport settings.klipyFetch deta hai
        val url = if (q.isNullOrBlank())
            api.trendingUrl(KlipyApi.Kind.GIFS, 1, KlipyApi.DEFAULT_PER_PAGE, "IN", "medium")
        else
            api.searchUrl(KlipyApi.Kind.GIFS, q, 1, KlipyApi.DEFAULT_PER_PAGE, "IN", "medium")
        model.settings.klipyFetch(url) { result ->
            loading = false
            page = result
            failed = result == null
        }
    }

    // panel khulte hi trending (ek baar)
    LaunchedEffect(key) { if (key.isNotBlank()) fetch(null) }

    Column(Modifier.fillMaxWidth().height(230.dp)) {
        // search — explicit submit (rate limit 100/hr)
        MediaSearchField(
            value = query,
            onChange = { query = it },
            placeholder = if (hindi) "GIF खोजें (Enter दबाएं)" else "Search GIFs (press enter)",
            onSubmit = { fetch(query) },
        )

        when {
            key.isBlank() -> GifSetupHint(hindi)

            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }

            failed || page == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⊘", fontSize = 22.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = MediaAvailability.Offline.reason(hindi)!!,
                        fontSize = 11.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                    Text(
                        text = if (hindi) "फिर कोशिश करें" else "Try again",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { fetch(query) },
                    )
                }
            }

            else -> {
                val items = page!!.items
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (hindi) "कोई GIF नहीं मिला" else "No GIFs found",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(86.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(items) { item ->
                            GifCell(model, item, hindi)
                        }
                    }
                }
            }
        }

        // Klipy attribution (unke terms mein zaroori)
        Text(
            text = "GIFs · KLIPY",
            fontSize = 8.sp, color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
    }
}

/** Ek GIF cell — preview direct Klipy URL se (unke integration rules). */
@Composable
private fun GifCell(model: KeyboardModel, item: MediaItem, hindi: Boolean) {
    val context = LocalContext.current
    Box(
        Modifier.height(86.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable {
                // commit background thread par (network download) — UI block na ho
                Thread {
                    val ok = model.settings.commitRemoteMedia(item)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (!ok) {
                            model.settings.toast(
                                MediaAvailability.EditorUnsupported(item.mime).reason(hindi)
                                    ?: "The text field does not support GIF insertion from the keyboard",
                            )
                        }
                    }
                }.start()
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.previewUrl)
                .decoderFactory(GifDecoder.Factory())   // animated GIF decode
                .crossfade(false)
                .build(),
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Key khali ho to setup hint — tab chhupta nahi (hide-nothing), raasta batata hai. */
@Composable
private fun GifSetupHint(hindi: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔑", fontSize = 22.sp)
            Text(
                text = MediaAvailability.NoApiKey.reason(hindi)!!,
                fontSize = 11.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Text(
                text = if (hindi)
                    "Key: partner.klipy.com → API Keys (free). Phir Settings → Klipy API key mein paste karein."
                else
                    "Get a free key at partner.klipy.com → API Keys, then paste in Settings → Klipy API key.",
                fontSize = 9.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/** Dono tabs ka shared search field — GifPanel mein explicit submit bhi hai. */
@Composable
private fun MediaSearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    onSubmit: (() -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).height(42.dp),
        placeholder = { Text(placeholder, fontSize = 12.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontFamily = MgondiFont),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSubmit?.invoke() },
        ),
    )
}

/** `raw/sticker_hi` jaisa res path → resource id. */
private fun rawResId(context: Context, res: String): Int {
    val name = res.substringAfter('/')
    return context.resources.getIdentifier(name, "raw", context.packageName)
}
