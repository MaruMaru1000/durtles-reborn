package com.example.durtlesreborn.reading.core

/**
 * A compact, versionless text encoding for cached tokens: one token per line, fields separated by
 * tabs, with the two characters that would break the format escaped.
 */
object TokenCodec {
    fun encode(tokens: List<Token>): String = tokens.joinToString("\n") { token ->
        listOf(
            escape(token.surface),
            escape(token.baseForm),
            escape(token.reading.orEmpty()),
            token.start.toString(),
            token.end.toString(),
        ).joinToString("\t")
    }

    fun decode(data: String): List<Token> = data.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 5) return@mapNotNull null
            val start = parts[3].toIntOrNull() ?: return@mapNotNull null
            val end = parts[4].toIntOrNull() ?: return@mapNotNull null
            Token(
                surface = unescape(parts[0]),
                baseForm = unescape(parts[1]),
                reading = unescape(parts[2]).ifEmpty { null },
                start = start,
                end = end,
            )
        }
        .toList()

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\n", "\\n")

    private fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    '\\' -> out.append('\\')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
