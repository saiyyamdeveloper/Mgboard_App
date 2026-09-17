// android-only: Commit Content API + FileProvider — JVM runner is file ko skip karta hai
package com.mgboard.keyboard.media

import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * **Commit Content API** pipeline — GIF/sticker ko editor tak bhejne ka raasta.
 *
 * Research: `docs/research/toolbar-project/TRANSLATE-GIF-FEASIBILITY.md` §2.3
 * (Android Developers: *Image keyboard support*)
 *
 *  1. Editor batata hai ki woh kaun se MIME types accept karta hai —
 *     `EditorInfoCompat.getContentMimeTypes(editorInfo)`
 *  2. IME media file ko apne **FileProvider** se `content://` URI banata hai
 *     (network URI commit nahi hoti — file local disk par chahiye)
 *  3. `InputConnectionCompat.commitContent(ic, editorInfo, InputContentInfoCompat(...),
 *     INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)`
 *
 * Zaroori rules:
 *  - API 25+ par `INPUT_CONTENT_GRANT_READ_URI_PERMISSION` flag lagao
 *  - composing text ke waqt commitContent mat karo (editor focus kho sakta hai)
 *  - editor ne opt-in nahi kiya → commit **fail** hota hai; UI Gboard ka verbatim
 *    message dikhata hai: *"The text field does not support GIF insertion from the keyboard"*
 *
 * Klipy ke integration rules kehte hain media **unke URLs se directly load** hoti hai
 * (mirror/cache nahi). Commit ke liye Android ko local file chahiye, isliye yahan ek
 * **transient delivery buffer** banaya jaata hai (`cacheDir/media/`) jo commit ke turant
 * baad rotate/clean hota hai — yeh cache nahi, delivery handoff hai. Production release
 * se pehle Klipy ko `developers@klipy.com` par yeh architecture confirm karwana chahiye
 * (unke docs khud custom caching ke liye approval ka raasta dete hain).
 */
class MediaCommitController(private val context: Context) {

    /** Editor ke accepted MIME types (Commit Content API ka opt-in). */
    fun editorMimeTypes(info: EditorInfo?): List<String> =
        info?.let { EditorInfoCompat.getContentMimeTypes(it)?.toList() } ?: emptyList()

    /** Editor is MIME ko accept karta hai kya (wildcard `image/*` support ke saath). */
    fun editorSupports(info: EditorInfo?, mime: String): Boolean {
        val types = editorMimeTypes(info)
        if (types.isEmpty()) return false
        return types.any { t ->
            t.equals(mime, ignoreCase = true) ||
                ClipDescription.compareMimeTypes(t, mime) ||
                t.endsWith("/*") && mime.startsWith(t.removeSuffix("/*"), ignoreCase = true)
        }
    }

    /**
     * Bundled sticker commit karo — raw resource se cache file, phir FileProvider URI.
     * @return true = editor ne content accept kiya (commit call succeed hua).
     */
    fun commitBundledSticker(
        sticker: BundledSticker,
        info: EditorInfo?,
        ic: InputConnection?,
    ): Boolean {
        if (info == null || ic == null) return false
        if (!editorSupports(info, sticker.mime)) return false
        val resName = sticker.res.substringAfter('/')
        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
        if (resId == 0) return false
        val file = mediaFile("${sticker.id}.png")
        context.resources.openRawResource(resId).use { input ->
            file.outputStream().use { out -> input.copyTo(out) }
        }
        return commitFile(file, sticker.mime, sticker.title, info, ic)
    }

    /**
     * Network media (Klipy GIF/sticker) commit karo — download → transient file → commit.
     * Network call **caller background thread par** karta hai; yeh method synchronous hai.
     */
    fun commitRemoteMedia(item: MediaItem, info: EditorInfo?, ic: InputConnection?): Boolean {
        if (info == null || ic == null) return false
        if (!editorSupports(info, item.mime)) return false
        val ext = if (item.mime == "image/gif") "gif" else "webp"
        val file = mediaFile("${sanitize(item.slug)}.$ext")
        if (!downloadTo(item.fullUrl, file)) return false
        return commitFile(file, item.mime, item.title, info, ic)
    }

    /**
     * Media bytes download karo (grid preview ke liye nahi — preview Coil direct URL se
     * load karta hai; yeh sirf commit ke liye delivery buffer hai).
     */
    fun downloadTo(url: String, file: File): Boolean = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 12000
        conn.setRequestProperty("User-Agent", "MgBoard-Keyboard/1.0")
        conn.inputStream.use { input -> file.outputStream().use { out -> input.copyTo(out) } }
        conn.responseCode in 200..299
    } catch (e: Exception) {
        false
    }

    private fun commitFile(
        file: File, mime: String, label: String,
        info: EditorInfo, ic: InputConnection,
    ): Boolean {
        val uri: Uri = try {
            FileProvider.getUriForFile(context, AUTHORITY, file)
        } catch (e: Exception) {
            return false
        }
        val description = ClipDescription(label, arrayOf(mime))
        val content = InputContentInfoCompat(uri, description)
        var flags = 0
        if (Build.VERSION.SDK_INT >= 25) {
            flags = flags or InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        }
        val ok = InputConnectionCompat.commitContent(ic, info, content, flags, null)
        pruneOldFiles()
        return ok
    }

    // ── delivery buffer management ─────────────────────────────────────────

    private fun mediaDir(): File = File(context.cacheDir, "media").apply { mkdirs() }

    private fun mediaFile(name: String): File = File(mediaDir(), name)

    /** Purani delivery files hatao — sirf haal ki kuch files rakho (cache nahi, buffer hai). */
    private fun pruneOldFiles(keep: Int = 12) {
        val files = mediaDir().listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(keep).forEach { it.delete() }
    }

    private fun sanitize(s: String): String =
        s.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60).ifEmpty { "media" }

    companion object {
        /** Manifest mein declared FileProvider authority (`${applicationId}.mediaprovider`). */
        const val AUTHORITY_SUFFIX = ".mediaprovider"
        fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX
    }
}
