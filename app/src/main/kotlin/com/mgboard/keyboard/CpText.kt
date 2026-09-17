package com.mgboard.keyboard

/**
 * Codepoint-safe text helpers.
 *
 * Web engine poore tarah `[...str]` (JS spread = code points) par chalta hai —
 * UTF-16 surrogate pairs (Masaram Gondi U+11D00..U+11DAF BMP ke bahar hain) ko
 * todna nahi. Yeh usi ka Kotlin equivalent hai: `String.codePoints()`.
 *
 * Engine hamesha **code point indices** mein baat karta hai, char indices mein nahi.
 */
object CpText {

    /** `[...s]` — string ko code points (Int) ki list mein todo. */
    fun cps(s: String): IntArray {
        val n = s.codePointCount(0, s.length)
        val out = IntArray(n)
        var i = 0
        var k = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            out[k++] = cp
            i += Character.charCount(cp)
        }
        return out
    }

    fun cpsAsStrings(s: String): List<String> = cps(s).map { String(Character.toChars(it)) }

    fun join(cps: IntArray, from: Int = 0, to: Int = cps.size): String {
        val lo = from.coerceIn(0, cps.size)
        val hi = to.coerceIn(lo, cps.size)
        val sb = StringBuilder()
        for (i in lo until hi) sb.appendCodePoint(cps[i])
        return sb.toString()
    }

    fun len(s: String): Int = s.codePointCount(0, s.length)

    /** web: `codePoints(s)` → "U+11D0C U+11D42" (debug/label ke liye). */
    fun codePointLabels(s: String): String =
        cps(s).joinToString(" ") { "U+%04X".format(it) }
}
