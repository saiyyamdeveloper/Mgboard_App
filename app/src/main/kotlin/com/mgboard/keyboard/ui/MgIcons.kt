// android-only: Compose Canvas — monochrome vector icons (Gboard-style)
package com.mgboard.keyboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * **MgBoard ke saare UI icons — plain monochrome vectors**, Gboard ke icons jaise.
 *
 * Gboard ke access-point / menu icons colored emoji NAHI hote — woh 2 dp stroke ke
 * outline vectors hote hain jo theme ke saath tint hote hain:
 *  - **dark / amoled theme → plain white**
 *  - light theme → plain gray `#5F6368` (Gboard light ka icon color; pure white
 *    light surface par invisible ho jaata, isliye Gboard bhi gray rakhta hai)
 *
 * Pehle MgBoard emoji glyphs (🎤  ✍  🫱 …) render karta tha jo colored dikhte the —
 * ab har icon yahan hand-drawn 24×24 vector hai, ek hi tint ke saath.
 *
 * Emoji panel ke emoji, stickers aur GIF **content** hain (icon nahi) — woh colored
 * rehete hain, bilkul Gboard ki tarah.
 */

/** Gboard-jaisa icon tint: dark surface par plain white, light par plain gray. */
@Composable
fun mgIconTint(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color.White
    else Color(0xFF5F6368)

/**
 * Monochrome icon render karo. `id` access-point / tile / chrome id hai
 * (voice, emoji, clipboard, … backspace, close, check, swap …).
 * Unknown id → neutral dot (kabhi crash nahi).
 */
@Composable
fun MgIcon(
    id: String,
    modifier: Modifier = Modifier,
    tint: Color = mgIconTint(),
    size: Dp = 20.dp,
) {
    // do icons vector nahi, monochrome text glyphs hain (Gboard bhi letters use karta hai)
    when (id) {
        "translate" -> {
            Text("अ", color = tint, fontSize = size.value.sp * 0.85f,
                fontWeight = FontWeight.SemiBold, modifier = modifier, maxLines = 1)
            return
        }
        "symbols" -> {
            Text("?123", color = tint, fontSize = size.value.sp * 0.52f,
                fontWeight = FontWeight.SemiBold, modifier = modifier, maxLines = 1)
            return
        }
    }
    Canvas(modifier.size(size)) {
        val k = size.width.toPx() / 24f          // 24×24 viewport scale
        val sw = 1.9f * k                        // ~2 dp stroke (Gboard jaisa)
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        fun r(x1: Float, y1: Float, x2: Float, y2: Float) =
            Rect(x1 * k, y1 * k, x2 * k, y2 * k)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), sw, StrokeCap.Round)
        fun circle(cx: Float, cy: Float, rad: Float, fill: Boolean = false) =
            if (fill) drawCircle(tint, rad * k, p(cx, cy))
            else drawCircle(tint, rad * k, p(cx, cy), style = stroke)
        fun arc(rect: Rect, start: Float, sweep: Float, fill: Boolean = false) {
            drawArc(
                color = tint, startAngle = start, sweepAngle = sweep, useCenter = fill,
                topLeft = Offset(rect.left, rect.top),
                size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else stroke,
            )
        }

        when (id) {
            // ── access points ──────────────────────────────────────────────
            "voice" -> {                       // mic: capsule + holder + stem
                drawRoundRect(tint, topLeft = p(9.3f, 3.5f),
                    size = androidx.compose.ui.geometry.Size(5.4f * k, 10f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.7f * k),
                    style = stroke)
                arc(r(7f, 7f, 17f, 17f), 0f, 180f)
                line(12f, 17f, 12f, 20.5f)
                line(9f, 20.5f, 15f, 20.5f)
            }
            "emoji" -> {                       // smiley outline
                circle(12f, 12f, 8.2f)
                circle(9.2f, 10f, 1.15f, fill = true)
                circle(14.8f, 10f, 1.15f, fill = true)
                arc(r(8f, 10.5f, 16f, 18.5f), 15f, 150f)
            }
            "clipboard" -> {
                drawRoundRect(tint, topLeft = p(6f, 5.5f),
                    size = androidx.compose.ui.geometry.Size(12f * k, 15.5f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * k), style = stroke)
                drawRoundRect(tint, topLeft = p(9.2f, 3.2f),
                    size = androidx.compose.ui.geometry.Size(6.6f * k, 4.2f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f * k), style = stroke)
            }
            "writingTools" -> {                // pen
                line(5.5f, 18.5f, 8.5f, 17.8f)
                line(8.5f, 17.8f, 18.5f, 7.8f)
                line(16.2f, 5.5f, 20.5f, 9.8f)
                line(18.5f, 7.8f, 16.2f, 5.5f)
                line(5.5f, 18.5f, 6.4f, 15.7f)
                line(6.4f, 15.7f, 16.4f, 5.7f)
                line(16.4f, 5.7f, 16.2f, 5.5f)
            }
            "proofread" -> {                    // spellcheck: check + baseline
                line(4.5f, 6.5f, 10f, 6.5f)
                line(8f, 13f, 11f, 16f)
                line(11f, 16f, 19.5f, 7.5f)
            }
            "quickInsert" -> {                  // plus in box
                drawRoundRect(tint, topLeft = p(4.5f, 4.5f),
                    size = androidx.compose.ui.geometry.Size(15f * k, 15f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * k), style = stroke)
                line(12f, 8.5f, 12f, 15.5f)
                line(8.5f, 12f, 15.5f, 12f)
            }
            "settings" -> {                      // gear: hub + 8 spokes
                circle(12f, 12f, 3.4f)
                var a = 0f
                while (a < 360f) {
                    val rad = Math.toRadians(a.toDouble())
                    line(12f + 5.6f * Math.cos(rad).toFloat(), 12f + 5.6f * Math.sin(rad).toFloat(),
                        12f + 8.4f * Math.cos(rad).toFloat(), 12f + 8.4f * Math.sin(rad).toFloat())
                    a += 45f
                }
            }
            "theme" -> {                          // contrast: circle + filled half
                circle(12f, 12f, 7.6f)
                arc(r(4.4f, 4.4f, 19.6f, 19.6f), 90f, 180f, fill = true)
            }
            "oneHanded" -> {                      // phone + side band
                drawRoundRect(tint, topLeft = p(6.5f, 3.5f),
                    size = androidx.compose.ui.geometry.Size(11f * k, 17f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f * k), style = stroke)
                line(13.5f, 3.5f, 13.5f, 20.5f)
                line(17.5f, 8f, 17.5f, 16f)
            }
            "undo" -> {                          // ↶ curved left arrow
                arc(r(6f, 8f, 18f, 20f), 180f, 180f)
                line(6f, 14f, 9.4f, 11.4f)
                line(6f, 14f, 9.4f, 16.6f)
            }
            "redo" -> {                          // ↷ curved right arrow
                arc(r(6f, 8f, 18f, 20f), 180f, 180f)
                line(18f, 14f, 14.6f, 11.4f)
                line(18f, 14f, 14.6f, 16.6f)
            }
            "imeAction" -> {                      // return arrow
                line(18f, 5.5f, 18f, 12f)
                line(18f, 12f, 6.5f, 12f)
                line(10f, 8.5f, 6.5f, 12f)
                line(6.5f, 12f, 10f, 15.5f)
            }
            "imeSwitch", "nextLang" -> {           // globe
                circle(12f, 12f, 8.2f)
                drawOval(tint, topLeft = p(9.1f, 3.8f),
                    size = androidx.compose.ui.geometry.Size(5.8f * k, 16.4f * k), style = stroke)
                line(3.8f, 12f, 20.2f, 12f)
            }
            "more", "featuresMenu", "grid" -> {    // 4-square grid (⊞)
                listOf(r(4.5f, 4.5f, 10.7f, 10.7f), r(13.3f, 4.5f, 19.5f, 10.7f),
                    r(4.5f, 13.3f, 10.7f, 19.5f), r(13.3f, 13.3f, 19.5f, 19.5f)).forEach {
                    drawRoundRect(tint, topLeft = Offset(it.left, it.top),
                        size = androidx.compose.ui.geometry.Size(it.width, it.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.6f * k), style = stroke)
                }
            }
            "moreKeyboardOptions", "moreVert" -> { // ⋮
                circle(12f, 5.5f, 1.7f, fill = true)
                circle(12f, 12f, 1.7f, fill = true)
                circle(12f, 18.5f, 1.7f, fill = true)
            }

            // ── grid-menu tiles jo access points se alag hain ───────────────
            "textEdit" -> {                         // pencil in square
                drawRoundRect(tint, topLeft = p(4.5f, 4.5f),
                    size = androidx.compose.ui.geometry.Size(15f * k, 15f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * k), style = stroke)
                line(9f, 15f, 15f, 9f)
                line(15f, 9f, 16.5f, 10.5f)
                line(9f, 15f, 8.5f, 15.5f)
            }
            "shareApp", "share" -> {
                circle(6.5f, 12f, 2.6f)
                circle(17f, 6f, 2.6f)
                circle(17f, 18f, 2.6f)
                line(8.8f, 10.8f, 14.7f, 7.3f)
                line(8.8f, 13.2f, 14.7f, 16.7f)
            }
            "personalDict", "book" -> {
                drawRoundRect(tint, topLeft = p(5f, 4.5f),
                    size = androidx.compose.ui.geometry.Size(14f * k, 15f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * k), style = stroke)
                line(12f, 4.5f, 12f, 19.5f)
                line(7.5f, 8.5f, 10f, 8.5f)
                line(14f, 8.5f, 16.5f, 8.5f)
            }
            "height" -> {                           // vertical resize
                line(12f, 4f, 12f, 20f)
                line(9f, 7f, 12f, 4f); line(15f, 7f, 12f, 4f)
                line(9f, 17f, 12f, 20f); line(15f, 17f, 12f, 20f)
                line(5f, 12f, 8f, 12f); line(16f, 12f, 19f, 12f)
            }
            "toolbar" -> {                           // strip toggle
                drawRoundRect(tint, topLeft = p(4f, 6f),
                    size = androidx.compose.ui.geometry.Size(16f * k, 12f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * k), style = stroke)
                line(4f, 11f, 20f, 11f)
            }
            "editMenu", "lines" -> {
                line(5f, 8f, 19f, 8f)
                line(5f, 12f, 15f, 12f)
                line(5f, 16f, 19f, 16f)
            }
            "selectAll", "selectMode" -> {           // I-beam cursor
                line(8f, 5f, 16f, 5f)
                line(8f, 19f, 16f, 19f)
                line(12f, 5f, 12f, 19f)
            }
            "copy" -> {
                drawRoundRect(tint, topLeft = p(8f, 8f),
                    size = androidx.compose.ui.geometry.Size(11f * k, 12f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * k), style = stroke)
                drawRoundRect(tint, topLeft = p(5f, 4f),
                    size = androidx.compose.ui.geometry.Size(11f * k, 12f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * k), style = stroke)
            }
            "cut" -> {                                // scissors
                circle(6.5f, 17.5f, 2.4f)
                circle(17.5f, 17.5f, 2.4f)
                line(8.2f, 15.8f, 18f, 4.5f)
                line(15.8f, 15.8f, 6f, 4.5f)
            }
            "paste" -> {                              // clipboard (same family)
                drawRoundRect(tint, topLeft = p(6f, 5.5f),
                    size = androidx.compose.ui.geometry.Size(12f * k, 15.5f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * k), style = stroke)
                drawRoundRect(tint, topLeft = p(9.2f, 3.2f),
                    size = androidx.compose.ui.geometry.Size(6.6f * k, 4.2f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f * k), style = stroke)
                line(9f, 12f, 15f, 12f)
                line(9f, 15.5f, 13f, 15.5f)
            }

            // ── panel chrome ───────────────────────────────────────────────
            "backspace" -> {
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(9.5f * k, 5f * k); lineTo(19.5f * k, 5f * k)
                    lineTo(19.5f * k, 19f * k); lineTo(9.5f * k, 19f * k)
                    lineTo(4.5f * k, 12f * k); close()
                }, tint, style = stroke)
                line(12f, 9.5f, 16.8f, 14.5f)
                line(16.8f, 9.5f, 12f, 14.5f)
            }
            "close" -> { line(6.5f, 6.5f, 17.5f, 17.5f); line(17.5f, 6.5f, 6.5f, 17.5f) }
            "check" -> { line(5f, 12.5f, 10f, 17.5f); line(10f, 17.5f, 19f, 7f) }
            "swap" -> {
                line(4f, 9f, 17f, 9f); line(14f, 6f, 17f, 9f); line(17f, 9f, 14f, 12f)
                line(20f, 15f, 7f, 15f); line(10f, 12f, 7f, 15f); line(7f, 15f, 10f, 18f)
            }
            "search" -> { circle(10.5f, 10.5f, 5.6f); line(14.8f, 14.8f, 20f, 20f) }
            "lock" -> {
                drawRoundRect(tint, topLeft = p(5.5f, 10.5f),
                    size = androidx.compose.ui.geometry.Size(13f * k, 9.5f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * k), style = stroke)
                arc(r(8.5f, 4f, 15.5f, 12f), 180f, 180f)
            }
            "blocked" -> { circle(12f, 12f, 8.2f); line(6.4f, 6.4f, 17.6f, 17.6f) }
            "key" -> {
                circle(7.5f, 12f, 3.4f)
                line(10.9f, 12f, 20f, 12f)
                line(16.5f, 12f, 16.5f, 15.5f)
                line(20f, 12f, 20f, 15.5f)
            }
            "play" -> {
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(9f * k, 6f * k); lineTo(18f * k, 12f * k); lineTo(9f * k, 18f * k); close()
                }, tint)
            }
            "drop" -> { line(8f, 10f, 12f, 14f); line(12f, 14f, 16f, 10f) }
            "download" -> {
                line(12f, 4f, 12f, 14f)
                line(8.5f, 10.5f, 12f, 14f); line(15.5f, 10.5f, 12f, 14f)
                line(5f, 15f, 5f, 19f); line(5f, 19f, 19f, 19f); line(19f, 19f, 19f, 15f)
            }
            "trash" -> {
                line(5f, 7f, 19f, 7f)
                drawRoundRect(tint, topLeft = p(7f, 7f),
                    size = androidx.compose.ui.geometry.Size(10f * k, 13f * k),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.8f * k), style = stroke)
                line(10f, 4.5f, 14f, 4.5f)
                line(10.5f, 10.5f, 10.5f, 16.5f)
                line(13.5f, 10.5f, 13.5f, 16.5f)
            }

            // ── fallback: neutral dot (hide-nothing: khali kabhi nahi) ─────
            // ── settings pages ──────────────────────────────────────────────
            "glide" -> {                        // finger trail squiggle
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(4f * k, 16f * k)
                    cubicTo(7f * k, 8f * k, 10f * k, 20f * k, 13f * k, 12f * k)
                    cubicTo(15f * k, 7f * k, 17.5f * k, 13f * k, 20f * k, 9f * k)
                }, tint, style = stroke)
                circle(4f, 16f, 1.3f, fill = true)
            }
            "person" -> {                       // head + shoulders
                circle(12f, 8f, 3.4f)
                arc(r(5f, 13f, 19f, 27f), 180f, 180f)
            }
            "wrench" -> {                       // tool head + handle
                circle(16.2f, 7.8f, 3.6f)
                line(13.8f, 10.4f, 6f, 18.2f)
                line(4.9f, 17.1f, 7.1f, 19.3f)
            }
            "flask" -> {                        // erlenmeyer flask
                line(9.5f, 4f, 14.5f, 4f)
                line(10.6f, 4f, 10.6f, 10f)
                line(13.4f, 4f, 13.4f, 10f)
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(10.6f * k, 10f * k); lineTo(5.5f * k, 19f * k)
                    lineTo(18.5f * k, 19f * k); lineTo(13.4f * k, 10f * k)
                }, tint, style = stroke)
                line(8.2f, 15.5f, 15.8f, 15.5f)
            }
            "help" -> {                         // circled question mark
                circle(12f, 12f, 9f)
                arc(r(8.8f, 6.8f, 15.2f, 13.2f), 200f, 140f)
                line(14.3f, 12.6f, 12f, 14.6f)
                line(12f, 14.6f, 12f, 16f)
                circle(12f, 18.4f, 1f, fill = true)
            }
            "info" -> {                         // circled "i"
                circle(12f, 12f, 9f)
                line(12f, 11f, 12f, 16.8f)
                circle(12f, 7.9f, 1f, fill = true)
            }
            else -> circle(12f, 12f, 3f, fill = true)
        }
    }
}
