package io.github.jpsutton.inkstash.scan

/** What the footer line of a Lorcana card says, e.g. `42/204 · EN · 1`. */
data class FooterInfo(
    val collectorNumber: String,
    val total: String?,
    val lang: String,
    val setCode: String,
)

/**
 * Finds the collector line in OCR text. Tolerates the usual OCR slips: O/0, I/l/1 in numbers and
 * set codes, and the dots between the fields read as `.`, `-`, `*`, `,` or `:` instead of `·`/`•`.
 */
object CollectorLineParser {
    private const val SEP = """[·•.\-*,:;]"""
    private const val DIGITISH = """[0-9OoIl|]"""

    private val footer = Regex(
        """(?<![0-9A-Za-z])($DIGITISH{1,3})([a-z]?)(?:\s*/\s*([A-Za-z0-9|]{1,4}))?\s*$SEP\s*([A-Za-z]{2})\s*$SEP\s*([A-Za-z0-9|]{1,5})(?![A-Za-z0-9])""",
    )

    fun parse(line: String): FooterInfo? {
        val m = footer.find(line.trim()) ?: return null
        val (num, suffix, total, lang, set) = m.destructured
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
            else -> it
        }
    }.joinToString("")

    /** `l`/`I`/`O` inside a code made only of such characters is a digit (set `1` read as `l`). */
    private fun normalizeSetCode(raw: String): String {
        val s = raw.replace('|', '1')
        return if (s.all { it in "0123456789OoIl" }) digits(s) else s.uppercase()
    }
}
