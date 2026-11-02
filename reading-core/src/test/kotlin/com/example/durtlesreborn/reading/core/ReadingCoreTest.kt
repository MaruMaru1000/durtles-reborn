package com.example.durtlesreborn.reading.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KuromojiTokenizerTest {
    private val tokenizer = KuromojiTokenizer()

    @Test
    fun aConjugatedVerbKeepsItsDictionaryForm() {
        val tokens = tokenizer.tokenize("食べました")
        assertTrue(tokens.any { it.baseForm == "食べる" })
    }

    @Test
    fun anIAdjectiveKeepsItsDictionaryForm() {
        val tokens = tokenizer.tokenize("高かった")
        assertTrue(tokens.any { it.baseForm == "高い" })
    }

    @Test
    fun offsetsPointBackIntoTheText() {
        val text = "私は水を飲む"
        for (token in tokenizer.tokenize(text)) {
            if (token.start >= 0 && token.end <= text.length) {
                assertEquals(token.surface, text.substring(token.start, token.end))
            }
        }
    }
}

class MatcherTest {
    private val tokenizer = KuromojiTokenizer()
    private val subjects = listOf(
        SubjectLite(1, SubjectType.VOCABULARY, "食べる", listOf("たべる"), listOf("Eat"), 1),
        SubjectLite(2, SubjectType.VOCABULARY, "高い", listOf("たかい"), listOf("Expensive"), 1),
        SubjectLite(3, SubjectType.KANA_VOCABULARY, "する", listOf("する"), listOf("Do"), 1),
        SubjectLite(4, SubjectType.KANJI, "水", listOf("みず"), listOf("Water"), 1),
    )
    private val assignments = listOf(
        AssignmentLite(1, 5),
        AssignmentLite(2, 5),
        AssignmentLite(3, 5),
        AssignmentLite(4, 7),
    )
    private val matcher = Matcher(subjects, assignments)

    @Test
    fun aConjugatedVerbMatchesItsVocabularyItem() {
        val article = matcher.analyze(tokenizer, "食べました")
        val match = article.matches.filterNotNull().firstOrNull()
        assertEquals(1, match?.subject?.id)
        assertTrue(match?.known == true)
    }

    @Test
    fun anIAdjectiveMatchesItsVocabularyItem() {
        val article = matcher.analyze(tokenizer, "高かった")
        assertEquals(2, article.matches.filterNotNull().firstOrNull()?.subject?.id)
    }

    @Test
    fun aKanaOnlyWordMatchesKanaVocabulary() {
        val article = matcher.analyze(tokenizer, "した")
        assertEquals(3, article.matches.filterNotNull().firstOrNull()?.subject?.id)
    }

    @Test
    fun aSingleKanjiMatchesTheKanjiSubject() {
        val article = matcher.analyze(tokenizer, "水を飲む")
        val match = article.matches.filterNotNull().firstOrNull()
        assertEquals(4, match?.subject?.id)
    }

    @Test
    fun aLockedItemStillMatchesButIsNotKnown() {
        val locked = Matcher(subjects, emptyList()).analyze(tokenizer, "水")
        val match = locked.matches.filterNotNull().firstOrNull()
        assertEquals(null, match?.srsStage)
        assertTrue(match?.known != true)
    }
}

class TokenCodecTest {
    @Test
    fun aRoundTripKeepsTabsNewlinesAndBackslashes() {
        val tokens = listOf(
            Token("a\tb", "a\nb", "c\\d", 0, 3),
            Token("水", "水", "みず", 3, 4),
        )
        assertEquals(tokens, TokenCodec.decode(TokenCodec.encode(tokens)))
    }

    @Test
    fun aWordWithoutAReadingComesBackWithoutOne() {
        val decoded = TokenCodec.decode(TokenCodec.encode(listOf(Token("x", "x", null, 0, 1))))
        assertEquals(listOf(null), decoded.map { it.reading })
    }

    @Test
    fun malformedLinesAreSkipped() {
        val decoded = TokenCodec.decode("x\ty\t\t0\t1\nnot enough\n\nz\tz\tz\t0\t1")
        assertEquals(listOf("x", "z"), decoded.map { it.surface })
    }
}

class ScorerTest {
    private fun match(stage: Int?, level: Int, type: SubjectType = SubjectType.VOCABULARY) = TokenMatch(
        token = Token("x", "x", null, 0, 1),
        subject = SubjectLite(level, type, "x", emptyList(), emptyList(), level),
        srsStage = stage,
    )

    @Test
    fun knownAndUncoveredPercentagesUseTheMatchedWords() {
        val tokens = List(10) { Token("x", "x", null, it, it + 1) }
        val matches = listOf<TokenMatch?>(
            match(5, 1), match(6, 2), match(7, 3), match(2, 4), null, null, null, null, null, null,
        )
        val score = Scorer.score(TokenizedArticle(tokens, matches))
        assertEquals(10, score.totalTokens)
        assertEquals(4, score.matchedTokens)
        assertEquals(3, score.knownTokens)
        assertEquals(75, score.knownPercent)
        assertEquals(60, score.uncoveredPercent)
    }

    @Test
    fun knownKanjiCountsDistinctSubjects() {
        val matches = listOf<TokenMatch?>(
            match(5, 1, SubjectType.KANJI),
            match(6, 1, SubjectType.KANJI),
            match(1, 2, SubjectType.KANJI),
            match(5, 3),
        )
        val score = Scorer.score(TokenizedArticle(emptyList(), matches))
        assertEquals(2, score.kanjiTokens)
        assertEquals(1, score.knownKanji)
        assertEquals(50, score.knownKanjiPercent)
    }

    @Test
    fun readyLevelIsWhereNinetyFivePercentOfWordsAreCovered() {
        val levels = listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 30)
        val matches = levels.map { match(5, it) }
        assertEquals(30, Scorer.readyLevel(TokenizedArticle(emptyList(), matches)))
        val short = listOf(1, 2, 3).map { match(5, it) }
        assertEquals(3, Scorer.readyLevel(TokenizedArticle(emptyList(), short)))
    }

    @Test
    fun anArticleWithNoMatchesHasNoReadyLevel() {
        assertEquals(null, Scorer.readyLevel(TokenizedArticle(emptyList(), listOf(null, null))))
    }
}
