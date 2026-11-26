package com.kanjiforge.app.domain

data class FuriganaSegment(
    val base: String,
    val reading: String? = null,
)

private val furiganaRegex = Regex("""\{(\d*)([^}]+)\}""")

fun parseFurigana(raw: String): List<FuriganaSegment> {
    if (raw.isEmpty()) return emptyList()
    val segments = mutableListOf<FuriganaSegment>()
    val plain = StringBuilder()

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            val cleaned = plain.toString().replace("\u3000", "")
            if (cleaned.isNotEmpty()) {
                val last = segments.lastOrNull()
                if (last != null && last.reading == null) {
                    segments[segments.lastIndex] = FuriganaSegment(last.base + cleaned, null)
                } else {
                    segments += FuriganaSegment(cleaned, null)
                }
            }
            plain.setLength(0)
        }
    }

    var cursor = 0
    while (cursor < raw.length) {
        val match = furiganaRegex.find(raw, cursor)
        if (match == null) {
            plain.append(raw, cursor, raw.length)
            break
        }
        if (match.range.first > cursor) {
            plain.append(raw, cursor, match.range.first)
        }
        val countText = match.groupValues[1]
        val reading = match.groupValues[2].trim()
        val afterBrace = match.range.last + 1
        val desiredCount = countText.toIntOrNull()?.coerceAtLeast(1) ?: 1

        var baseEnd = afterBrace
        var consumed = 0
        while (consumed < desiredCount && baseEnd < raw.length) {
            val ch = raw[baseEnd]
            if (ch == '{' || ch == '\u3000' || ch.isWhitespace()) break
            val codePoint = raw.codePointAt(baseEnd)
            baseEnd += Character.charCount(codePoint)
            consumed += 1
        }

        if (baseEnd > afterBrace && reading.isNotEmpty()) {
            flushPlain()
            segments += FuriganaSegment(
                base = raw.substring(afterBrace, baseEnd),
                reading = reading,
            )
            cursor = baseEnd
        } else {
            plain.append(raw, match.range.first, afterBrace)
            cursor = afterBrace
        }
    }
    flushPlain()
    return segments
}

fun formatFuriganaPlain(raw: String): String {
    if (raw.isEmpty()) return ""
    val segments = parseFurigana(raw)
    if (segments.isEmpty()) return raw.replace("\u3000", "")
    return segments.joinToString(separator = "") { it.base }
}
