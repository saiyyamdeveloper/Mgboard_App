// android-only: Coil + Compose — GIF/Clips/Stickers/Memes tabs (expression panel §5.1 + Klipy categories)
package com.mgboard.keyboard.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * Expression panel ke **media tabs** — Klipy ke saare content types:
 * **GIFs · Clips · Stickers · Memes** (user ke order par sab add).
 *
 * Research: TRANSLATE-GIF-FEASIBILITY.md §2.2(a):
 *  - **Tenor API 30 Jun 2026 ko band** (Gboard ka source) — Klipy (ex-Tenor team,
 *    WhatsApp bhi migrate kar chuka) = replacement; free-for-life API
 *  - testing-mode key = **100 requests/hour** → search-as-you-type NAHI;
 *    explicit submit (⌨ search action) + trending ek baar per tab
 *  - attribution zaroori → har tab ke footer mein "<Type> · KLIPY"
 *  - key khali ho (placeholder mode) → **setup hint**, tab chhupta nahi (hide-nothing)
 *  - previews direct Klipy URLs se (unke integration rules); commit ke liye transient
 *    delivery buffer `MediaCommitController` banata hai (Commit Content API)
 *
 * Clips = video (mp4) — editors mein video/* support kam hai, isliye zyada-tar fields
 * par tab Gboard-verbatim reason ke saath gated dikhega (hide-nothing, chhupta nahi).
 */
@Composable
fun KlipyMediaPanel(model: KeyboardModel, kind: KlipyApi.Kind) {
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
            api.trendingUrl(kind, 1, KlipyApi.DEFAULT_PER_PAGE, "IN", "medium")
        else
            api.searchUrl(kind, q, 1, KlipyApi.DEFAULT_PER_PAGE, "IN", "medium")
        model.settings.klipyFetch(url, kind) { result ->
            loading = false
            page = result
            failed = result == null
        }
    }

    // tab khulte hi trending (ek baar)
    LaunchedEffect(key, kind) { if (key.isNotBlank()) fetch(null) }

    Column(Modifier.fillMaxWidth().height(230.dp)) {
        MediaSearchField(
            value = query,
            onChange = { query = it },
            placeholder = kind.searchPlaceholder(hindi),
            onSubmit = { fetch(query) },
        )

        when {
            key.isBlank() -> GifSetupHint(hindi)

            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }

            failed || page == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MgIcon("blocked", size = 22.dp, tint = MaterialTheme.colorScheme.outline)
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
                            text = kind.emptyLabel(hindi),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (kind == KlipyApi.Kind.CLIPS) 108.dp else 86.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(items) { item -> MediaCell(model, item, hindi, kind) }
                    }
                }
            }
        }

        // Klipy attribution (unke terms mein zaroori)
        Text(
            text = kind.attribution(),
            fontSize = 8.sp, color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
    }
}

/**
 * Expression panel ka **Stickers** tab — MgBoard ka bundled pack (offline tier) +
 * key configure ho to **KLIPY stickers** ka alag section (provider content mix nahi
 * hota — Klipy ke integration rules ke mutabiq dedicated section).
 */
@Composable
fun StickersPanel(model: KeyboardModel) {
    val hindi = model.settings.uiHindi
    val context = LocalContext.current
    val key = model.settings.klipyAppKey()
    var query by remember { mutableStateOf("") }
    val stickers = remember(query) { BundledStickers.search(query) }
    var remote by remember { mutableStateOf<MediaPage?>(null) }
    var remoteLoading by remember { mutableStateOf(false) }

    fun fetchRemote(q: String) {
        if (key.isBlank() || q.isBlank()) { remote = null; return }
        remoteLoading = true
        val api = KlipyApi(key) { null }
        model.settings.klipyFetch(
            api.searchUrl(KlipyApi.Kind.STICKERS, q, 1, KlipyApi.DEFAULT_PER_PAGE, "IN", "medium"),
            KlipyApi.Kind.STICKERS,
        ) { result -> remoteLoading = false; remote = result }
    }

    Column(Modifier.fillMaxWidth().height(230.dp)) {
        MediaSearchField(
            value = query,
            onChange = { query = it },
            placeholder = if (hindi) "स्टिकर खोजें (Enter = KLIPY)" else "Search stickers (enter = KLIPY)",
            onSubmit = { fetchRemote(query) },
        )

        // ── section 1: bundled pack (offline, koi key nahi) ─────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (if (hindi) BundledStickers.PACK_TITLE_HI else BundledStickers.PACK_TITLE) +
                    (if (hindi) " · ऑफ़लाइन" else " · offline"),
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("${stickers.size}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        }

        if (stickers.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (hindi) "कोई स्टिकर नहीं मिला" else "No stickers found",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(68.dp),
                modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 6.dp),
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

        // ── section 2: KLIPY stickers (key configure ho to) ─────────────────
        if (key.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hindi) "KLIPY स्टिकर्स · सर्च से" else "KLIPY stickers · via search",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (remoteLoading) CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp)
            }
            val rItems = remote?.items.orEmpty()
            if (rItems.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(68.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(rItems) { item -> MediaCell(model, item, hindi, KlipyApi.Kind.STICKERS) }
                }
            }
        }

        Text(
            text = BundledStickers.ATTRIBUTION + (if (key.isNotBlank()) "  ·  " + KlipyApi.Kind.STICKERS.attribution() else ""),
            fontSize = 8.sp, color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
    }
}

/** Ek media cell — preview direct Klipy URL se (animated GIF/WebP decode). */
@Composable
private fun MediaCell(model: KeyboardModel, item: MediaItem, hindi: Boolean, kind: KlipyApi.Kind) {
    val context = LocalContext.current
    Box(
        Modifier.height(if (kind == KlipyApi.Kind.CLIPS) 96.dp else 86.dp).fillMaxWidth()
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
        // Clips par play indicator (video hai, preview animated thumbnail hai)
        if (kind == KlipyApi.Kind.CLIPS) {
            MgIcon("play", size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
        }
    }
}

/** Key khali ho to setup hint — tab chhupta nahi (hide-nothing), raasta batata hai. */
@Composable
private fun GifSetupHint(hindi: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MgIcon("key", size = 22.dp)
            Text(
                text = MediaAvailability.NoApiKey.reason(hindi)!!,
                fontSize = 11.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Text(
                text = if (hindi)
                    "Key: partner.klipy.com → API Keys (free). Phir app mein: "Klipy key" button se paste karein, ya local.properties mein KLIPY_APP_KEY= likh kar rebuild karein."
                else
                    "Get a free key at partner.klipy.com → API Keys. Then paste in-app via the "Klipy key" button, or set KLIPY_APP_KEY in local.properties and rebuild.",
                fontSize = 9.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/** Sab media tabs ka shared search field — explicit submit (rate limit 100/hr). */
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

// ── per-kind labels (EN + HI) ─────────────────────────────────────────────

private fun KlipyApi.Kind.searchPlaceholder(hindi: Boolean): String = when (this) {
    KlipyApi.Kind.GIFS -> if (hindi) "GIF खोजें (Enter दबाएं)" else "Search GIFs (press enter)"
    KlipyApi.Kind.CLIPS -> if (hindi) "क्लिप खोजें (Enter दबाएं)" else "Search clips (press enter)"
    KlipyApi.Kind.STICKERS -> if (hindi) "स्टिकर खोजें (Enter दबाएं)" else "Search stickers (press enter)"
    KlipyApi.Kind.MEMES -> if (hindi) "मीम खोजें (Enter दबाएं)" else "Search memes (press enter)"
}

private fun KlipyApi.Kind.emptyLabel(hindi: Boolean): String = when (this) {
    KlipyApi.Kind.GIFS -> if (hindi) "कोई GIF नहीं मिला" else "No GIFs found"
    KlipyApi.Kind.CLIPS -> if (hindi) "कोई क्लिप नहीं मिला" else "No clips found"
    KlipyApi.Kind.STICKERS -> if (hindi) "कोई स्टिकर नहीं मिला" else "No stickers found"
    KlipyApi.Kind.MEMES -> if (hindi) "कोई मीम नहीं मिला" else "No memes found"
}
