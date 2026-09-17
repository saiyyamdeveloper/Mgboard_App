package com.mgboard.keyboard.engine

import com.mgboard.keyboard.CpText
import com.mgboard.keyboard.data.Cp
import com.mgboard.keyboard.data.Hindi
import java.text.Normalizer

/**
 * Devanagari → Masaram Gondi converter.
 *
 * Web: `convertDevanagariToGondi()` + `GONDI_TO_DEV` / `DEV_TO_GONDI` /
 * `DEV_NUKTA_FALLBACK` / `GONDI_CLUSTER_SPECIAL` (index.html, "[DEEP FIX v16.1]
 * Voice-typing accuracy overhaul" + "[FIX v16.3]"). Yahan maps **Hindi panel ke
 * KeyDefs se hi derive** hote hain (web jaisa), taaki layout badle to converter
 * apne aap sahi rahe — do jagah alag se maintain nahi karna padta.
 *
 * Unicode 17.0 ke hisaab se Masaram Gondi mein RA-clusters ke special forms hain:
 *  - U+11D46 REPHA  — cluster-INITIAL RA   (र + ् + व्यंजन → 𑵆 + व्यंजन)
 *  - U+11D47 RAKARA — cluster-FINAL RA     (व्यंजन + ् + र → व्यंजन + 𑵇)
 *  - U+11D2E..30    — dedicated conjunct letters क्ष / ज्ञ / त्र
 *  - U+11D44 HALANTA (word-final '्') vs U+11D45 VIRAMA (conjunct banane wala '्')
 */
object DevToGondi {

    const val DEV_VS_R = "\u0943"        // ृ
    const val DEV_NUKTA_MARK = "\u093C"  // standalone combining nukta (़)
    const val DEV_VIRAMA_MARK = "\u094D" // devanagari halant (्)

    /** व्यंजन जो क्लस्टर नियमों में गिने जाते hain (single + precomposed nukta forms). */
    private const val DEV_CONS_SINGLE =
        "कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळक़ख़ग़ज़ड़ढ़फ़य़ऱऩऴ"

    /** speech-engine कभी-कभी जोड़े हुए रूप देता है */
    private val DEV_CONS_COMPOSED = listOf("क्ष", "ज्ञ", "त्र")

    val DEV_CONS_SET: Set<String> =
        DEV_CONS_SINGLE.map { it.toString() }.toSet() + DEV_CONS_COMPOSED.toSet()

    // ── maps (web ke jaise hi derive + explicit additions) ────────────────────

    /** Gondi glyph → Devanagari display (Hindi panel ki har key se). */
    val GONDI_TO_DEV: Map<String, String> = buildMap {
        listOf(Hindi.ROW2, Hindi.ROW3, Hindi.ROW4, Hindi.ROW5).flatten()
            .filterNotNull()
            .forEach { if (it.g.isNotEmpty()) put(it.g, it.s) }
        Hindi.VOWEL_KEYS.forEach { put(it.g, it.s) }
        Hindi.MATRA_KEYS.forEach { put(it.g, it.s) }
    }

    /** User-confirmed rule: jin nukta-variants ka Gondi mein alag char nahi hai. */
    val DEV_NUKTA_FALLBACK: Map<String, String> = linkedMapOf(
        "क़" to (Cp.KA + Cp.NUKTA),
        "ख़" to (Cp.KHA + Cp.NUKTA),
        "ग़" to (Cp.GA + Cp.NUKTA),
        "ज़" to (Cp.JA + Cp.NUKTA),
        "ड़" to (Cp.DDA + Cp.NUKTA),
        "ढ़" to (Cp.DDHA + Cp.NUKTA),
        "फ़" to (Cp.PHA + Cp.NUKTA),
        "य़" to (Cp.YA + Cp.NUKTA),
        // [DEEP FIX] composite nukta-variants jo pehle छूट गए थे
        "ऱ" to Cp.RA,
        "ऩ" to Cp.NNA,
        "ऴ" to Cp.LLA,
    )

    /** Devanagari → Gondi code point(s). */
    val DEV_TO_GONDI: Map<String, String> = buildMap {
        // reverse of GONDI_TO_DEV (web: for..in reverse)
        for ((gondiChar, dev) in GONDI_TO_DEV) put(dev, gondiChar)

        // LLA ('ळ') GONDI_TO_DEV mein nahi tha (wo sirf LA ka long-press alternate
        // hai, base row mein nahi) — isliye seedha add.
        put("ळ", Cp.LLA)

        // [DEEP FIX] वे अक्षर जो सिर्फ़ long-press alternate थे
        put("ङ", Cp.NGA)   // वाङ्मय जैसे शब्द
        put("ञ", Cp.NYA)

        // [DEEP FIX] स्वर 'ऋ' — गोंडी में अलग अक्षर नहीं; keyboard ka Vocalic-R
        // bhi VS_R hi daalta hai, isliye आवाज़ भी वही लिखे.
        put("ऋ", Cp.VS_R)

        // [FIX v16.3] Nukta-variants → base + Gondi NUKTA (keyboard long-press jaisa)
        for ((ch, g) in DEV_NUKTA_FALLBACK) put(ch, g)

        // [DEEP FIX] Candra vowels — विदेशी शब्दों के लिए ओ/अ + चन्द्र-चिह्न
        put("ऑ", Cp.O + Cp.CANDRA)
        put("ॲ", Cp.A + Cp.CANDRA)

        // [DEEP FIX] अंक — देवनागरी (०-९) और लैटिन (0-9) दोनों → गोंडी अंक
        for (d in 0..9) {
            put(String(Character.toChars(0x0966 + d)), Cp.MAP.getValue("D$d"))
            put(d.toString(), Cp.MAP.getValue("D$d"))
        }

        // [FIX v16.3] Precomposed nukta letters (NFC roop) — escape-sequence keys,
        // taaki file-literals ka normalization kabhi farak na kare.
        put("\u0958", Cp.KA + Cp.NUKTA)    // क़
        put("\u0959", Cp.KHA + Cp.NUKTA)   // ख़
        put("\u095A", Cp.GA + Cp.NUKTA)    // ग़
        put("\u095B", Cp.JA + Cp.NUKTA)    // ज़
        put("\u095C", Cp.DDA + Cp.NUKTA)   // ड़
        put("\u095D", Cp.DDHA + Cp.NUKTA)  // ढ़
        put("\u095E", Cp.PHA + Cp.NUKTA)   // फ़
        put("\u095F", Cp.YA + Cp.NUKTA)    // य़
    }

    /** Dedicated conjunct letters — REPHA/RAKARA rules se PEHLE check hote hain. */
    val GONDI_CLUSTER_SPECIAL: Map<String, String> = linkedMapOf(
        ("क" + "ष") to Cp.KSSA,
        ("ज" + "ञ") to Cp.JNYA,
        ("त" + "र") to Cp.TRA,
    )

    // ── conversion ───────────────────────────────────────────────────────────

    fun convert(text: String): String {
        // web: `[...text.normalize('NFD')]`
        val chars = CpText.cpsAsStrings(Normalizer.normalize(text, Normalizer.Form.NFD))

        // Step 1: nukta ko pichle व्यंजन token se jodo (decomposed input ke liye)
        val tokens = ArrayList<String>()
        for (ch in chars) {
            val previous = tokens.lastOrNull()
            if (ch == DEV_NUKTA_MARK && previous != null && DEV_CONS_SET.contains(previous[0].toString())) {
                if (!previous.endsWith(DEV_NUKTA_MARK)) tokens[tokens.size - 1] = previous + ch
            } else {
                tokens.add(ch)
            }
        }

        fun isCons(token: String?): Boolean =
            token != null && token.isNotEmpty() &&
                DEV_CONS_SET.contains(token[0].toString()) &&
                (token.length == 1 || (token.length == 2 && token[1].toString() == DEV_NUKTA_MARK))

        fun mapToken(token: String): String {
            DEV_TO_GONDI[token]?.let { return it }
            if (isCons(token) && token.endsWith(DEV_NUKTA_MARK)) {
                val base = DEV_TO_GONDI[token[0].toString()] ?: token[0].toString()
                return base + Cp.NUKTA
            }
            // web: `token === DEV_NUKTA_MARK ? '' : token` — akela nukta gir jaata hai
            return if (token == DEV_NUKTA_MARK) "" else token
        }

        val parts = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (!isCons(token)) {
                parts += if (token == DEV_VIRAMA_MARK) Cp.HALANTA else mapToken(token)
                i++
                continue
            }
            // व्यंजन क्लस्टर इकट्ठा करो: CONS (Virama CONS)*
            val cluster = ArrayList<String>()
            cluster += token
            // NOTE (web parity — cluster boundary quirk): JS mein yeh loop
            //   while(tokens[i + 1] === VIRAMA && isCons(tokens[i + 2]))
            // hai, aur `tokens[i + 2]` array ke bahar `undefined` deta hai →
            // `isCons(undefined)` = false. Kotlin ka `i + 2 < tokens.size`
            // bilkul wahi effective range deta hai, isliye yahan bounds check
            // jaan-boojh kar web jaisa hi rakha gaya hai.
            //
            // Iska matlab: 4-token input "क ् र म" mein cluster sirf [क, र] banta
            // hai (म chhoot jaata hai) → natija KA + RAKARA + MA. Yeh web ka
            // apna behaviour hai (tests isi ko lock karte hain) — "fix" karne ka
            // matlab web se parity todna hota, isliye nahi kiya. Agar aage
            // badalna ho to DONO apps mein saath mein badalna.
            while (i + 2 < tokens.size &&
                tokens[i + 1] == DEV_VIRAMA_MARK && isCons(tokens[i + 2])
            ) {
                cluster += tokens[i + 2]
                i += 2
            }

            var prefix = ""
            if (cluster.size > 1 && cluster[0] == "र") {
                prefix = Cp.REPHA
                cluster.removeAt(0)
            }

            val units = ArrayList<String>()
            var j = 0
            while (j < cluster.size) {
                val pair = cluster[j] + (cluster.getOrNull(j + 1) ?: "")
                val special = GONDI_CLUSTER_SPECIAL[pair]
                when {
                    special != null -> { units += special; j++ }
                    j == cluster.size - 1 && cluster[j] == "र" && units.isNotEmpty() ->
                        units[units.size - 1] = units[units.size - 1] + Cp.RAKARA
                    else -> units += mapToken(cluster[j])
                }
                j++
            }
            parts += prefix + units.joinToString(Cp.VIRAMA)
            i++
        }
        return parts.joinToString("")
    }

    /**
     * Hindi panel ke `KeyDef` se type kya hoga — web: `onConsonant(d.g)` /
     * `onMatra(d.g)`, yaani **glyph** insert hota hai, label nahi.
     */
    fun keyGlyph(def: com.mgboard.keyboard.data.KeyDef): String = def.g

    /** Hindi panel ka Vocalic-R combo label (web: renderVocalicRH). */
    fun vocalicRComboLabel(vcCons: String?): String {
        if (vcCons == null) return DEV_VS_R
        val first = CpText.cpsAsStrings(vcCons).firstOrNull() ?: return DEV_VS_R
        val dev = GONDI_TO_DEV[first] ?: ""
        val nukta = if (vcCons.contains(Cp.NUKTA)) "\u093C" else ""
        return dev + nukta + DEV_VS_R
    }
}
