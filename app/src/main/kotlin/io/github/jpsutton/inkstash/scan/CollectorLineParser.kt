package io.github.jpsutton.inkstash.scan

/** What the footer line of a Lorcana card says, e.g. `42/204 · EN · 1`. */
data class FooterInfo(
    val collectorNumber: String,
    val total: String?,
    val lang: String,
    val setCode: String,
)

/**
 * Finds the collector line in OCR text. Tolerates the usual OCR slips:
 * - letters read for digits (O/0, I/l/|/1, S/5, B/8, Z/2, G/6),
 * - the dots between the fields read as other punctuation, or dropped so only a space is left
 *   (`137/204 - EN 11`). A space alone only counts when the language is a real Lorcana one,
 *   so ordinary text such as "5 to 10" is not taken for a footer.
 */
object CollectorLineParser {
    private const val PUNCT = """[·•.\-*,:;'`´_+~"●○◦∙°º]"""
    private const val DIGITISH = """[0-9OoIl|SsBZzGg]"""
    private const val SEP = """(?:\s*$PUNCT{1,2}\s*|\s+)"""
    private val KNOWN_LANGS = setOf("EN", "FR", "DE", "IT", "JA", "ZH", "ES", "PT", "KO")

    private val footer = Regex(
        """(?<![0-9A-Za-z])($DIGITISH{1,3})([a-z]?)(?:\s*/\s*([A-Za-z0-9|]{1,4}))?($SEP)([A-Za-z]{2})($SEP)([A-Za-z0-9|]{1,5})(?![A-Za-z0-9])""",
    )

    fun parse(line: String): FooterInfo? {
        val m = footer.find(line.trim()) ?: return null
        val (num, suffix, total, sep1, lang, sep2, set) = m.destructured
        // A bare space between fields is only trusted around a real language code.
        val spaceOnly = sep1.isBlank() || sep2.isBlank()
        if (spaceOnly && lang.uppercase() !in KNOWN_LANGS) return null
        val number = digits(num).trimStart('0').ifEmpty { "0" }
        if (number == "0") return null
        return FooterInfo(
            collectorNumber = number + suffix,
            total = total.takeIf { it.isNotEmpty() }?.let { normalizeSetCode(it) },
            lang = lang.uppercase(),
            setCode = normalizeSetCode(set),
        )
    }

    private fun digits(s: String) = s.map {
        when (it) {
            'O', 'o' -> '0'
            'I', 'l', '|' -> '1'
            'S', 's' -> '5'
            'B' -> '8'
            'Z', 'z' -> '2'
            'G', 'g' -> '6'
            else -> it
        }
    }.joinToString("")

    /** A code made only of digit look-alikes is a number (set `1` read as `l`); otherwise it is letters (`P1`, `D23`). */
    private fun normalizeSetCode(raw: String): String {
        val s = raw.replace('|', '1')
        return if (s.all { it in "0123456789OoIlSsBZzGg" }) digits(s) else s.uppercase()
    }
}
