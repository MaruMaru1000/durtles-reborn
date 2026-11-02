package com.example.durtlesreborn.reading.core

/** Scores a tokenized article, and works out the level at which most of it is readable. */
object Scorer {
    /** 95% of the matched words may be known at the "ready" level; the rest may still be new. */
    private const val READY_FRACTION = 0.95

    fun score(tokenized: TokenizedArticle): ArticleScore {
        val matched = tokenized.matches.filterNotNull()
        val kanji = matched
            .filter { it.subject.type == SubjectType.KANJI }
            .distinctBy { it.subject.id }
        return ArticleScore(
            totalTokens = tokenized.tokens.size,
            matchedTokens = matched.size,
            knownTokens = matched.count { it.known },
            kanjiTokens = kanji.size,
            knownKanji = kanji.count { it.known },
        )
    }

    /**
     * The lowest WaniKani level at which at least 95% of the matched words are at or below that
     * level, or null when the article has no matched words.
     */
    fun readyLevel(tokenized: TokenizedArticle): Int? {
        val levels = tokenized.matches.filterNotNull().map { it.subject.level }.sorted()
        if (levels.isEmpty()) return null
        val needed = kotlin.math.ceil(levels.size * READY_FRACTION).toInt().coerceAtLeast(1)
        return levels.getOrNull(needed - 1)
    }
}
