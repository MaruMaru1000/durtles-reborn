package com.example.durtlesreborn.reading.core

import com.atilika.kuromoji.ipadic.Tokenizer

/** Splits Japanese text into words with character offsets. Swappable for tests. */
interface JapaneseTokenizer {
    fun tokenize(text: String): List<Token>
}

/**
 * Kuromoji with the IPADIC dictionary. Words are matched on their base form (食べました → 食べる), so
 * the dictionary lives in the bundled jar and nothing leaves the device.
 */
class KuromojiTokenizer : JapaneseTokenizer {
    // Kuromoji 0.9 has no builder; the no-arg tokenizer loads IPADIC from the bundled jar.
    private val tokenizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Tokenizer() }

    override fun tokenize(text: String): List<Token> = synchronized(tokenizer) {
        val out = ArrayList<Token>(text.length / 2)
        for (token in tokenizer.tokenize(text)) {
            val surface = token.surface ?: continue
            val start = token.position.coerceAtLeast(0)
            val base = token.baseForm?.takeUnless { it == "*" } ?: surface
            out += Token(
                surface = surface,
                baseForm = base,
                reading = token.reading?.takeUnless { it == "*" },
                start = start,
                end = start + surface.length,
            )
        }
        out
    }
}
