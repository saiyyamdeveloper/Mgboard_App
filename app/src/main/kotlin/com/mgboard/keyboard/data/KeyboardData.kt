package com.mgboard.keyboard.data

/* ============================================================================
 *  KeyboardData.kt  —  GENERATED FILE, DO NOT EDIT BY HAND
 *
 *  Source of truth : Mgboard_Web/index.html
 *  Generator       : Mgboard/scripts/gen_keyboard_data.py
 *  Verify          : python3 Mgboard/scripts/check_data_parity.py
 *
 *  Web ke layout literals (CP unicode map, consonant/matra sets, Gondi rows,
 *  Hindi→Gondi rows, QWERTY rows, numbers/symbol pages, emoji categories)
 *  yahan 1:1 transpile hote hain — har glyph, har long-press alternate,
 *  har row order bilkul wahi.
 * ========================================================================== */

/**
 * Ek key ki definition.
 *
 * @param g   glyph — jo character type hota hai (Gondi Unicode / literal)
 * @param s   screen label (Hindi panel mein Devanagari glyph, Gondi panel mein naam)
 * @param lp  long-press alternate glyph (null = koi alternate nahi)
 * @param lps long-press alternate ka label
 * @param cls web CSS class ka equivalent semantic tag ("special", "danger")
 */
data class KeyDef(
    val g: String,
    val s: String = "",
    val lp: String? = null,
    val lps: String? = null,
    val cls: String? = null,
)

object Cp {
    /** Web ka `CP` — Masaram Gondi (U+11D00–U+11DAF) + borrowed symbols. */
    val MAP: Map<String, String> = mapOf(
    "A" to (String(intArrayOf(0x11D00), 0, 1)),
    "AA" to (String(intArrayOf(0x11D01), 0, 1)),
    "I" to (String(intArrayOf(0x11D02), 0, 1)),
    "II" to (String(intArrayOf(0x11D03), 0, 1)),
    "U" to (String(intArrayOf(0x11D04), 0, 1)),
    "UU" to (String(intArrayOf(0x11D05), 0, 1)),
    "E" to (String(intArrayOf(0x11D06), 0, 1)),
    "AI" to (String(intArrayOf(0x11D08), 0, 1)),
    "O" to (String(intArrayOf(0x11D09), 0, 1)),
    "AU" to (String(intArrayOf(0x11D0B), 0, 1)),
    "KA" to (String(intArrayOf(0x11D0C), 0, 1)),
    "KHA" to (String(intArrayOf(0x11D0D), 0, 1)),
    "GA" to (String(intArrayOf(0x11D0E), 0, 1)),
    "GHA" to (String(intArrayOf(0x11D0F), 0, 1)),
    "NGA" to (String(intArrayOf(0x11D10), 0, 1)),
    "CA" to (String(intArrayOf(0x11D11), 0, 1)),
    "CHA" to (String(intArrayOf(0x11D12), 0, 1)),
    "JA" to (String(intArrayOf(0x11D13), 0, 1)),
    "JHA" to (String(intArrayOf(0x11D14), 0, 1)),
    "NYA" to (String(intArrayOf(0x11D15), 0, 1)),
    "TTA" to (String(intArrayOf(0x11D16), 0, 1)),
    "TTHA" to (String(intArrayOf(0x11D17), 0, 1)),
    "DDA" to (String(intArrayOf(0x11D18), 0, 1)),
    "DDHA" to (String(intArrayOf(0x11D19), 0, 1)),
    "NNA" to (String(intArrayOf(0x11D1A), 0, 1)),
    "TA" to (String(intArrayOf(0x11D1B), 0, 1)),
    "THA" to (String(intArrayOf(0x11D1C), 0, 1)),
    "DA" to (String(intArrayOf(0x11D1D), 0, 1)),
    "DHA" to (String(intArrayOf(0x11D1E), 0, 1)),
    "NA" to (String(intArrayOf(0x11D1F), 0, 1)),
    "PA" to (String(intArrayOf(0x11D20), 0, 1)),
    "PHA" to (String(intArrayOf(0x11D21), 0, 1)),
    "BA" to (String(intArrayOf(0x11D22), 0, 1)),
    "BHA" to (String(intArrayOf(0x11D23), 0, 1)),
    "MA" to (String(intArrayOf(0x11D24), 0, 1)),
    "YA" to (String(intArrayOf(0x11D25), 0, 1)),
    "RA" to (String(intArrayOf(0x11D26), 0, 1)),
    "LA" to (String(intArrayOf(0x11D27), 0, 1)),
    "VA" to (String(intArrayOf(0x11D28), 0, 1)),
    "SHA" to (String(intArrayOf(0x11D29), 0, 1)),
    "SSA" to (String(intArrayOf(0x11D2A), 0, 1)),
    "SA" to (String(intArrayOf(0x11D2B), 0, 1)),
    "HA" to (String(intArrayOf(0x11D2C), 0, 1)),
    "LLA" to (String(intArrayOf(0x11D2D), 0, 1)),
    "KSSA" to (String(intArrayOf(0x11D2E), 0, 1)),
    "JNYA" to (String(intArrayOf(0x11D2F), 0, 1)),
    "TRA" to (String(intArrayOf(0x11D30), 0, 1)),
    "VS_AA" to (String(intArrayOf(0x11D31), 0, 1)),
    "VS_I" to (String(intArrayOf(0x11D32), 0, 1)),
    "VS_II" to (String(intArrayOf(0x11D33), 0, 1)),
    "VS_U" to (String(intArrayOf(0x11D34), 0, 1)),
    "VS_UU" to (String(intArrayOf(0x11D35), 0, 1)),
    "VS_R" to (String(intArrayOf(0x11D36), 0, 1)),
    "VS_E" to (String(intArrayOf(0x11D3A), 0, 1)),
    "VS_AI" to (String(intArrayOf(0x11D3C), 0, 1)),
    "VS_O" to (String(intArrayOf(0x11D3D), 0, 1)),
    "VS_AU" to (String(intArrayOf(0x11D3F), 0, 1)),
    "ANUSVARA" to (String(intArrayOf(0x11D40), 0, 1)),
    "VISARGA" to (String(intArrayOf(0x11D41), 0, 1)),
    "NUKTA" to (String(intArrayOf(0x11D42), 0, 1)),
    "CANDRA" to (String(intArrayOf(0x11D43), 0, 1)),
    "HALANTA" to (String(intArrayOf(0x11D44), 0, 1)),
    "VIRAMA" to (String(intArrayOf(0x11D45), 0, 1)),
    "REPHA" to (String(intArrayOf(0x11D46), 0, 1)),
    "RAKARA" to (String(intArrayOf(0x11D47), 0, 1)),
    "D0" to (String(intArrayOf(0x11D50), 0, 1)),
    "D1" to (String(intArrayOf(0x11D51), 0, 1)),
    "D2" to (String(intArrayOf(0x11D52), 0, 1)),
    "D3" to (String(intArrayOf(0x11D53), 0, 1)),
    "D4" to (String(intArrayOf(0x11D54), 0, 1)),
    "D5" to (String(intArrayOf(0x11D55), 0, 1)),
    "D6" to (String(intArrayOf(0x11D56), 0, 1)),
    "D7" to (String(intArrayOf(0x11D57), 0, 1)),
    "D8" to (String(intArrayOf(0x11D58), 0, 1)),
    "D9" to (String(intArrayOf(0x11D59), 0, 1)),
    "RUPEE" to "\u20b9",
    "SWASTIK" to "\u0fd5",
    "DHARMA" to "\u2638",
    "FLORETTE" to "\u2740",
    "DELTA" to "\u0394",
    "PI" to "\u03c0",
    "CHECK" to "\u2713",
    "DEG" to "\u00b0",
    "MOD_APOS" to "\u02bc",
)

    private fun g(k: String): String = MAP.getValue(k)
    val A: String get() = g("A")
    val AA: String get() = g("AA")
    val I: String get() = g("I")
    val II: String get() = g("II")
    val U: String get() = g("U")
    val UU: String get() = g("UU")
    val E: String get() = g("E")
    val AI: String get() = g("AI")
    val O: String get() = g("O")
    val AU: String get() = g("AU")
    val KA: String get() = g("KA")
    val KHA: String get() = g("KHA")
    val GA: String get() = g("GA")
    val GHA: String get() = g("GHA")
    val NGA: String get() = g("NGA")
    val CA: String get() = g("CA")
    val CHA: String get() = g("CHA")
    val JA: String get() = g("JA")
    val JHA: String get() = g("JHA")
    val NYA: String get() = g("NYA")
    val TTA: String get() = g("TTA")
    val TTHA: String get() = g("TTHA")
    val DDA: String get() = g("DDA")
    val DDHA: String get() = g("DDHA")
    val NNA: String get() = g("NNA")
    val TA: String get() = g("TA")
    val THA: String get() = g("THA")
    val DA: String get() = g("DA")
    val DHA: String get() = g("DHA")
    val NA: String get() = g("NA")
    val PA: String get() = g("PA")
    val PHA: String get() = g("PHA")
    val BA: String get() = g("BA")
    val BHA: String get() = g("BHA")
    val MA: String get() = g("MA")
    val YA: String get() = g("YA")
    val RA: String get() = g("RA")
    val LA: String get() = g("LA")
    val VA: String get() = g("VA")
    val SHA: String get() = g("SHA")
    val SSA: String get() = g("SSA")
    val SA: String get() = g("SA")
    val HA: String get() = g("HA")
    val LLA: String get() = g("LLA")
    val KSSA: String get() = g("KSSA")
    val JNYA: String get() = g("JNYA")
    val TRA: String get() = g("TRA")
    val VS_AA: String get() = g("VS_AA")
    val VS_I: String get() = g("VS_I")
    val VS_II: String get() = g("VS_II")
    val VS_U: String get() = g("VS_U")
    val VS_UU: String get() = g("VS_UU")
    val VS_R: String get() = g("VS_R")
    val VS_E: String get() = g("VS_E")
    val VS_AI: String get() = g("VS_AI")
    val VS_O: String get() = g("VS_O")
    val VS_AU: String get() = g("VS_AU")
    val ANUSVARA: String get() = g("ANUSVARA")
    val VISARGA: String get() = g("VISARGA")
    val NUKTA: String get() = g("NUKTA")
    val CANDRA: String get() = g("CANDRA")
    val HALANTA: String get() = g("HALANTA")
    val VIRAMA: String get() = g("VIRAMA")
    val REPHA: String get() = g("REPHA")
    val RAKARA: String get() = g("RAKARA")
    val D0: String get() = g("D0")
    val D1: String get() = g("D1")
    val D2: String get() = g("D2")
    val D3: String get() = g("D3")
    val D4: String get() = g("D4")
    val D5: String get() = g("D5")
    val D6: String get() = g("D6")
    val D7: String get() = g("D7")
    val D8: String get() = g("D8")
    val D9: String get() = g("D9")
    val RUPEE: String get() = g("RUPEE")
    val SWASTIK: String get() = g("SWASTIK")
    val DHARMA: String get() = g("DHARMA")
    val FLORETTE: String get() = g("FLORETTE")
    val DELTA: String get() = g("DELTA")
    val PI: String get() = g("PI")
    val CHECK: String get() = g("CHECK")
    val DEG: String get() = g("DEG")
    val MOD_APOS: String get() = g("MOD_APOS")
}

object Gondi {
    /** 37 consonants (34 + 3 conjunct letters KSSA/JNYA/TRA). */
    val CONSONANTS: Set<String> = setOf(
    Cp.KA,
    Cp.KHA,
    Cp.GA,
    Cp.GHA,
    Cp.NGA,
    Cp.CA,
    Cp.CHA,
    Cp.JA,
    Cp.JHA,
    Cp.NYA,
    Cp.TTA,
    Cp.TTHA,
    Cp.DDA,
    Cp.DDHA,
    Cp.NNA,
    Cp.TA,
    Cp.THA,
    Cp.DA,
    Cp.DHA,
    Cp.NA,
    Cp.PA,
    Cp.PHA,
    Cp.BA,
    Cp.BHA,
    Cp.MA,
    Cp.YA,
    Cp.RA,
    Cp.LA,
    Cp.VA,
    Cp.SHA,
    Cp.SSA,
    Cp.SA,
    Cp.HA,
    Cp.LLA,
    Cp.KSSA,
    Cp.JNYA,
    Cp.TRA
)

    /** 10 dependent vowel signs + halanta (row1 "matra" mode). */
    val MATRAS: Set<String> = setOf(
    Cp.VS_AA,
    Cp.VS_I,
    Cp.VS_II,
    Cp.VS_U,
    Cp.VS_UU,
    Cp.VS_R,
    Cp.VS_E,
    Cp.VS_AI,
    Cp.VS_O,
    Cp.VS_AU,
    Cp.HALANTA
)

    /** Row 1 — 10 independent vowels (dynamic: vowel ↔ matra). */
    val VOWEL_KEYS: List<KeyDef> = listOf(
        KeyDef(g = Cp.A, s = "A"),
        KeyDef(g = Cp.AA, s = "AA"),
        KeyDef(g = Cp.I, s = "I"),
        KeyDef(g = Cp.II, s = "II"),
        KeyDef(g = Cp.U, s = "U"),
        KeyDef(g = Cp.UU, s = "UU"),
        KeyDef(g = Cp.E, s = "E"),
        KeyDef(g = Cp.AI, s = "AI"),
        KeyDef(g = Cp.O, s = "O"),
        KeyDef(g = Cp.AU, s = "AU")
    )

    /** Row 1 — 10 dependent vowel signs. */
    val MATRA_KEYS: List<KeyDef> = listOf(
        KeyDef(g = Cp.VS_AA, s = "AA"),
        KeyDef(g = Cp.VS_I, s = "I"),
        KeyDef(g = Cp.VS_II, s = "II"),
        KeyDef(g = Cp.VS_U, s = "U"),
        KeyDef(g = Cp.VS_UU, s = "UU"),
        KeyDef(g = Cp.VS_E, s = "E"),
        KeyDef(g = Cp.VS_AI, s = "AI"),
        KeyDef(g = Cp.VS_O, s = "O"),
        KeyDef(g = Cp.VS_AU, s = "AU"),
        KeyDef(g = Cp.VS_R, s = "R")
    )

    val ROW2: List<KeyDef?> = listOf(
        KeyDef(g = Cp.KA, s = "KA"),
        KeyDef(g = Cp.KHA, s = "KHA"),
        KeyDef(g = Cp.GA, s = "GA"),
        KeyDef(g = Cp.GHA, s = "GHA", lp = Cp.NGA, lps = "NGA"),
        KeyDef(g = Cp.CA, s = "CA"),
        KeyDef(g = Cp.CHA, s = "CHA"),
        KeyDef(g = Cp.JA, s = "JA"),
        KeyDef(g = Cp.JHA, s = "JHA", lp = Cp.NYA, lps = "NYA"),
        KeyDef(g = Cp.CANDRA, s = "Candra"),
        KeyDef(g = Cp.ANUSVARA, s = "Anusvara", lp = Cp.VISARGA, lps = "Visarga")
    )

    val ROW3: List<KeyDef?> = listOf(
        KeyDef(g = Cp.TTA, s = "TTA"),
        KeyDef(g = Cp.TTHA, s = "TTHA"),
        KeyDef(g = Cp.DDA, s = "DDA"),
        KeyDef(g = Cp.DDHA, s = "DDHA"),
        KeyDef(g = Cp.NNA, s = "NNA"),
        KeyDef(g = Cp.TA, s = "TA"),
        KeyDef(g = Cp.THA, s = "THA"),
        KeyDef(g = Cp.DA, s = "DA"),
        KeyDef(g = Cp.DHA, s = "DHA"),
        KeyDef(g = Cp.NA, s = "NA")
    )

    val ROW4: List<KeyDef?> = listOf(
        KeyDef(g = Cp.PA, s = "PA"),
        KeyDef(g = Cp.PHA, s = "PHA"),
        KeyDef(g = Cp.BA, s = "BA"),
        KeyDef(g = Cp.BHA, s = "BHA"),
        KeyDef(g = Cp.MA, s = "MA"),
        KeyDef(g = Cp.YA, s = "YA"),
        KeyDef(g = Cp.RA, s = "RA"),
        KeyDef(g = Cp.LA, s = "LA", lp = Cp.LLA, lps = "LLA"),
        KeyDef(g = Cp.VA, s = "VA"),
        KeyDef(g = Cp.SHA, s = "SHA")
    )

    val ROW5: List<KeyDef?> = listOf(
        KeyDef(g = Cp.SSA, s = "SSA"),
        KeyDef(g = Cp.SA, s = "SA"),
        KeyDef(g = Cp.HA, s = "HA"),
        KeyDef(g = Cp.KSSA, s = "KSSA"),
        KeyDef(g = Cp.JNYA, s = "JNYA"),
        KeyDef(g = Cp.TRA, s = "TRA"),
        null,
        KeyDef(g = Cp.VISARGA, s = "Visarga"),
        KeyDef(g = Cp.VIRAMA, s = "युक्त", cls = "special"),
        KeyDef(g = "⌫", s = "DEL", cls = "danger")
    )

    /** Rows 2–5 (web: [ROW2, ROW3, ROW4, ROW5]). */
    val LETTER_ROWS: List<List<KeyDef?>> = listOf(ROW2, ROW3, ROW4, ROW5)
}

object Hindi {
    val VOWEL_KEYS: List<KeyDef> = listOf(
        KeyDef(g = Cp.A, s = "अ"),
        KeyDef(g = Cp.AA, s = "आ"),
        KeyDef(g = Cp.I, s = "इ"),
        KeyDef(g = Cp.II, s = "ई"),
        KeyDef(g = Cp.U, s = "उ"),
        KeyDef(g = Cp.UU, s = "ऊ"),
        KeyDef(g = Cp.E, s = "ए"),
        KeyDef(g = Cp.AI, s = "ऐ"),
        KeyDef(g = Cp.O, s = "ओ"),
        KeyDef(g = Cp.AU, s = "औ")
    )

    val MATRA_KEYS: List<KeyDef> = listOf(
        KeyDef(g = Cp.VS_AA, s = "ा"),
        KeyDef(g = Cp.VS_I, s = "ि"),
        KeyDef(g = Cp.VS_II, s = "ी"),
        KeyDef(g = Cp.VS_U, s = "ु"),
        KeyDef(g = Cp.VS_UU, s = "ू"),
        KeyDef(g = Cp.VS_E, s = "े"),
        KeyDef(g = Cp.VS_AI, s = "ै"),
        KeyDef(g = Cp.VS_O, s = "ो"),
        KeyDef(g = Cp.VS_AU, s = "ौ"),
        KeyDef(g = Cp.VS_R, s = "ृ")
    )

    val ROW2: List<KeyDef?> = listOf(
        KeyDef(g = Cp.KA, s = "क", lp = Cp.KA + Cp.NUKTA, lps = "क़"),
        KeyDef(g = Cp.KHA, s = "ख", lp = Cp.KHA + Cp.NUKTA, lps = "ख़"),
        KeyDef(g = Cp.GA, s = "ग", lp = Cp.GA + Cp.NUKTA, lps = "ग़"),
        KeyDef(g = Cp.GHA, s = "घ", lp = Cp.NGA, lps = "ङ"),
        KeyDef(g = Cp.CA, s = "च"),
        KeyDef(g = Cp.CHA, s = "छ"),
        KeyDef(g = Cp.JA, s = "ज", lp = Cp.JA + Cp.NUKTA, lps = "ज़"),
        KeyDef(g = Cp.JHA, s = "झ", lp = Cp.NYA, lps = "ञ"),
        KeyDef(g = Cp.CANDRA, s = "ँ"),
        KeyDef(g = Cp.ANUSVARA, s = "ं")
    )

    val ROW3: List<KeyDef?> = listOf(
        KeyDef(g = Cp.TTA, s = "ट"),
        KeyDef(g = Cp.TTHA, s = "ठ"),
        KeyDef(g = Cp.DDA, s = "ड", lp = Cp.DDA + Cp.NUKTA, lps = "ड़"),
        KeyDef(g = Cp.DDHA, s = "ढ", lp = Cp.DDHA + Cp.NUKTA, lps = "ढ़"),
        KeyDef(g = Cp.NNA, s = "ण"),
        KeyDef(g = Cp.TA, s = "त"),
        KeyDef(g = Cp.THA, s = "थ"),
        KeyDef(g = Cp.DA, s = "द"),
        KeyDef(g = Cp.DHA, s = "ध"),
        KeyDef(g = Cp.NA, s = "न")
    )

    val ROW4: List<KeyDef?> = listOf(
        KeyDef(g = Cp.PA, s = "प"),
        KeyDef(g = Cp.PHA, s = "फ", lp = Cp.PHA + Cp.NUKTA, lps = "फ़"),
        KeyDef(g = Cp.BA, s = "ब"),
        KeyDef(g = Cp.BHA, s = "भ"),
        KeyDef(g = Cp.MA, s = "म"),
        KeyDef(g = Cp.YA, s = "य", lp = Cp.YA + Cp.NUKTA, lps = "य़"),
        KeyDef(g = Cp.RA, s = "र"),
        KeyDef(g = Cp.LA, s = "ल", lp = Cp.LLA, lps = "ळ"),
        KeyDef(g = Cp.VA, s = "व"),
        KeyDef(g = Cp.SHA, s = "श")
    )

    val ROW5: List<KeyDef?> = listOf(
        KeyDef(g = Cp.SSA, s = "ष"),
        KeyDef(g = Cp.SA, s = "स"),
        KeyDef(g = Cp.HA, s = "ह"),
        KeyDef(g = Cp.KSSA, s = "क्ष"),
        KeyDef(g = Cp.JNYA, s = "ज्ञ"),
        KeyDef(g = Cp.TRA, s = "त्र"),
        null,
        KeyDef(g = Cp.VISARGA, s = "ः"),
        KeyDef(g = Cp.VIRAMA, s = "्", cls = "special"),
        KeyDef(g = "⌫", s = "DEL", cls = "danger")
    )

    val LETTER_ROWS: List<List<KeyDef?>> = listOf(ROW2, ROW3, ROW4, ROW5)
}

object Qwerty {
    /** 3 letter rows (row 4 = shift/symbol/backspace control row, code se banti hai). */
    val ROWS: List<List<String>> = listOf(
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("z", "x", "c", "v", "b", "n", "m")
)
}

object Numbers {
    /** 5 rows × 10 keys — shared by Gondi aur Hindi panels (web: #kbN). */
    val ROWS: List<List<KeyDef>> = listOf(
    listOf(
        KeyDef(g = Cp.D1, s = "1"),
        KeyDef(g = Cp.D2, s = "2"),
        KeyDef(g = Cp.D3, s = "3"),
        KeyDef(g = Cp.D4, s = "4"),
        KeyDef(g = Cp.D5, s = "5"),
        KeyDef(g = Cp.D6, s = "6"),
        KeyDef(g = Cp.D7, s = "7"),
        KeyDef(g = Cp.D8, s = "8"),
        KeyDef(g = Cp.D9, s = "9"),
        KeyDef(g = Cp.D0, s = "0")
    ),
    listOf(
        KeyDef(g = Cp.RUPEE, s = "₹"),
        KeyDef(g = Cp.SWASTIK, s = "࿕"),
        KeyDef(g = Cp.DHARMA, s = "☸"),
        KeyDef(g = Cp.FLORETTE, s = "❀"),
        KeyDef(g = "<", s = "<"),
        KeyDef(g = ">", s = ">"),
        KeyDef(g = "&", s = "&"),
        KeyDef(g = "=", s = "="),
        KeyDef(g = ";", s = ";"),
        KeyDef(g = ":", s = ":")
    ),
    listOf(
        KeyDef(g = "@", s = "@"),
        KeyDef(g = "#", s = "#"),
        KeyDef(g = "(", s = "("),
        KeyDef(g = ")", s = ")"),
        KeyDef(g = "{", s = "{"),
        KeyDef(g = "}", s = "}"),
        KeyDef(g = "[", s = "["),
        KeyDef(g = "]", s = "]"),
        KeyDef(g = "~", s = "~"),
        KeyDef(g = "÷", s = "÷")
    ),
    listOf(
        KeyDef(g = Cp.MOD_APOS, s = "ʼ"),
        KeyDef(g = "'", s = "'"),
        KeyDef(g = ",", s = ","),
        KeyDef(g = "-", s = "-"),
        KeyDef(g = "/", s = "/"),
        KeyDef(g = "\\", s = "\\"),
        KeyDef(g = "*", s = "*"),
        KeyDef(g = "^", s = "^"),
        KeyDef(g = "\"", s = "\""),
        KeyDef(g = "|", s = "|")
    ),
    listOf(
        KeyDef(g = "!", s = "!"),
        KeyDef(g = "+", s = "+"),
        KeyDef(g = "_", s = "_"),
        KeyDef(g = "\$", s = "\$"),
        KeyDef(g = Cp.DEG, s = "°"),
        KeyDef(g = Cp.CHECK, s = "✓"),
        KeyDef(g = Cp.DELTA, s = "Δ"),
        KeyDef(g = "¥", s = "¥"),
        KeyDef(g = Cp.PI, s = "π"),
        KeyDef(g = "⌫", s = "DEL", cls = "danger")
    )
    )

    /** Calculator page ke operators (web: CALC_OPS). */
    val CALC_OPS: List<String> = listOf("+", "-", "*", "/", "(", ")", ",")
}

object EmojiData {
    /** 9 categories (order preserved — linked map). */
    val CATEGORIES: Map<String, List<String>> = linkedMapOf(
    "😀 Smileys" to listOf("😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊", "😋", "😎", "😍", "🥰", "😘", "😗", "😜", "🤪", "🤩", "😇", "🙂", "🤗", "🤔", "🫡", "😴", "😢", "😭", "😤", "😠", "😡", "🥺", "😱", "🤯", "😳", "😬", "🤠"),
    "👋 Gestures" to listOf("👍", "👎", "👌", "🤌", "🤝", "🙏", "✌️", "🤞", "🤟", "🤘", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "💪", "🫶", "👋", "🤙", "🖐️", "✋", "🤚", "👆", "👇", "👈", "👉"),
    "❤️ Hearts" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "❣️", "💔", "❤️🔥", "❤️🩹"),
    "🌿 Nature" to listOf("🌸", "🌹", "🌺", "🌻", "🌼", "🌷", "🌱", "🌲", "🌳", "🌴", "🍀", "🌿", "☘️", "🍄", "🌵", "🐝", "🐞", "🦋", "🐢", "🐘", "🦁", "🐯", "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🦆", "🦅", "🦉", "🌙", "⭐", "☀️", "🌈", "⚡", "❄️", "🌊", "🌋"),
    "🍎 Food" to listOf("🍎", "🍌", "🍇", "🍉", "🍊", "🍋", "🍓", "🍒", "🥭", "🍍", "🥥", "🥑", "🍆", "🥔", "🥕", "🍞", "🧀", "🍕", "🍔", "🍟", "🌭", "🍿", "🥨", "🍩", "🍪", "🎂", "🍰", "🧁", "🍫", "🍬", "☕", "🍵", "🥛", "🧃", "🍺", "🍷", "🥂"),
    "⚽ Activity" to listOf("⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏓", "🏸", "🏏", "🥊", "🥋", "⛳", "🎣", "🏊", "🚴", "⛹️", "🤸", "🎯", "🎮", "🎲", "🎳", "🏆", "🏅", "🥇", "🥈", "🥉", "🎪", "🎭", "🎨", "🎤", "🎹", "🎷", "🎺"),
    "🚗 Travel" to listOf("🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚚", "🚛", "🏍️", "🚲", "🛴", "🚂", "✈️", "🚀", "🛸", "🚁", "⛵", "🚢", "🗺️", "🏠", "🏢", "🏰", "🗽", "🌉", "🌆", "🎡", "🎢", "🏖️", "🏔️"),
    "💡 Objects" to listOf("💡", "🔦", "🕯️", "🔥", "📱", "💻", "⌚", "🎧", "📷", "🎥", "📺", "📻", "📚", "📖", "📝", "✏️", "💰", "💎", "🎁", "🎀", "📦", "🔑", "🔒", "🔓", "🧲", "⚙️", "🔧", "🧰", "🧹", "🪣"),
    "💬 Symbols" to listOf("❗", "❓", "⭕", "✅", "❌", "💯", "✨", "💫", "🎉", "🎊", "🙈", "🙉", "🙊", "💬", "💭", "🚫", "🛑", "⚠️", "🎵", "🎶", "♠️", "♥️", "♦️", "♣️", "🕉️", "☮️", "✡️", "☪️", "🕎", "☦️", "🔯"),
)
}
