package com.example.durtlesreborn.reading.core

import kotlinx.coroutines.flow.Flow

/** The kind of WaniKani subject, as the reading feature needs it. */
enum class SubjectType { RADICAL, KANJI, VOCABULARY, KANA_VOCABULARY }

/** A small read-only copy of a WaniKani subject. The app builds these from its own database. */
data class SubjectLite(
    val id: Int,
    val type: SubjectType,
    val characters: String?,
    val readings: List<String>,
    val meanings: List<String>,
    val level: Int,
)

/** A small read-only copy of the user's progress on one subject. */
data class AssignmentLite(val subjectId: Int, val srsStage: Int)

/**
 * The only way the reading feature reads WaniKani data. Implemented by the app over its own
 * database: the reading modules never write to it and never call the WaniKani API themselves.
 */
interface WaniKaniReadOnly {
    fun subjects(): Flow<List<SubjectLite>>
    fun assignments(): Flow<List<AssignmentLite>>
}

/** One word from a tokenizer, with the character offsets in the source text. */
data class Token(
    val surface: String,
    val baseForm: String,
    val reading: String?,
    val start: Int,
    val end: Int,
)

/** One token matched to a WaniKani subject. [srsStage] is null when the item is locked. */
data class TokenMatch(
    val token: Token,
    val subject: SubjectLite,
    val srsStage: Int?,
) {
    /** A word counts as known from Guru 1 upwards. */
    val known: Boolean get() = (srsStage ?: 0) >= GURU_STAGE
}

/** WaniKani SRS stage 5 is Guru 1. */
const val GURU_STAGE = 5

/** The outcome of a tokenizer run: the tokens and, for each one, the WaniKani match if any. */
data class TokenizedArticle(
    val tokens: List<Token>,
    val matches: List<TokenMatch?>,
) {
    val matchedCount: Int get() = matches.count { it != null }
}

/** How much of an article the user already knows. */
data class ArticleScore(
    val totalTokens: Int,
    val matchedTokens: Int,
    val knownTokens: Int,
    val kanjiTokens: Int,
    val knownKanji: Int,
) {
    /** Known WaniKani words out of all WaniKani words in the article. */
    val knownPercent: Int get() = percent(knownTokens, matchedTokens)

    /** Words that are not in WaniKani at all, out of every word. */
    val uncoveredPercent: Int get() = percent(totalTokens - matchedTokens, totalTokens)

    /** Known kanji out of the distinct kanji that are in WaniKani. */
    val knownKanjiPercent: Int get() = percent(knownKanji, kanjiTokens)

    private fun percent(part: Int, whole: Int): Int =
        if (whole <= 0) 0 else (part * 100.0 / whole).toInt()
}

/** Colours for the reader, supplied by the app from its own theme. ARGB. */
data class ReadingPalette(
    val apprentice: Int,
    val guru: Int,
    val master: Int,
    val enlightened: Int,
    val burned: Int,
    val locked: Int,
)
