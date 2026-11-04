package com.example.durtlesreborn.answer

import com.example.durtlesreborn.kana.Kana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaTest {
    @Test
    fun convertsRomajiToHiragana() {
        assertEquals("かんじ", Kana.simulateInput("kanji"))
        assertEquals("しゃ", Kana.simulateInput("sha"))
        assertEquals("きょう", Kana.simulateInput("kyou"))
        assertEquals("ー", Kana.simulateInput("-"))
    }

    @Test
    fun doubledConsonantMakesSmallTsu() {
        assertEquals("がっこう", Kana.simulateInput("gakkou"))
        assertEquals("きっと", Kana.simulateInput("kitto"))
    }

    @Test
    fun nHandling() {
        assertEquals("ほん", Kana.simulateInput("hon"))
        assertEquals("ほんや", Kana.simulateInput("honnya"))
        assertEquals("んか", Kana.simulateInput("nka"))
    }

    @Test
    fun upperCaseGivesKatakana() {
        assertEquals("カンジ", Kana.simulateInput("KANNJI"))
    }

    @Test
    fun singleUpperCaseNIsNotConvertedLikeTheOriginal() {
        // The original's "n before a consonant" rule only looks at lower-case n; kept for parity.
        assertEquals("カNジ", Kana.simulateInput("KANJI"))
    }

    @Test
    fun katakanaHiraganaRoundTrip() {
        assertEquals("カンジ", Kana.toKatakana("かんじ"))
        assertEquals("かんじ", Kana.toHiragana("カンジ"))
    }

    @Test
    fun directKanaInputIsLeftAlone() {
        // A Japanese IME sends small kana and voiced kana as characters; the romaji converter
        // must not rewrite them.
        assertEquals("きゃく", Kana.typed("", "きゃく"))
        assertEquals("きゃく", Kana.typed("きゃ", "きゃく"))
        assertEquals("び", Kana.typed("ひ", "び"))
        assertEquals("ぱ", Kana.typed("は", "ぱ"))
        assertEquals("しょっき", Kana.typed("", "しょっき"))
    }

    @Test
    fun decomposedDakutenIsNormalized() {
        // ひ + combining dakuten and は + combining handakuten become the precomposed kana.
        assertEquals("び", Kana.normalize("ひ\u3099"))
        assertEquals("ぱ", Kana.normalize("は\u309A"))
        assertEquals("びゃく", Kana.normalize("ひ\u3099ゃく"))
    }
}

class QuestionChecksTest {
    private val action = CloseEnoughAction.ACCEPT_WITH_TOAST

    private val water = SubjectAnswers(
        meanings = listOf(Meaning("Water"), Meaning("Fluid", acceptedAnswer = false)),
        auxiliaryMeanings = listOf(AuxiliaryMeaning("liquid", whitelist = true), AuxiliaryMeaning("ice", whitelist = false)),
        synonyms = listOf("aqua"),
        readings = listOf(Reading("すい", "onyomi", acceptedAnswer = true), Reading("みず", "kunyomi", acceptedAnswer = false)),
    )

    @Test
    fun meaningAcceptsMeaningsWhitelistAndSynonyms() {
        assertTrue(QuestionChecks.meaning(water, "water", action).ok)
        assertTrue(QuestionChecks.meaning(water, "liquid", action).ok)
        assertTrue(QuestionChecks.meaning(water, "aqua", action).ok)
    }

    @Test
    fun meaningRejectsBlacklistAndUnrelated() {
        assertFalse(QuestionChecks.meaning(water, "ice", action).ok)
        assertEquals(AnswerVerdict.NOK_WITHOUT_RETRY, QuestionChecks.meaning(water, "banana", action))
    }

    @Test
    fun readingTypedInMeaningQuestionIsRetry() {
        assertEquals(AnswerVerdict.NOK_WITH_RETRY, QuestionChecks.meaning(water, "mizu", action))
    }

    @Test
    fun kanjiReadingAcceptedWrongTypeAndDigraph() {
        assertTrue(QuestionChecks.kanjiReading(water, "すい", false).ok)
        assertTrue(QuestionChecks.kanjiReading(water, "スイ", false).ok)
        assertTrue(QuestionChecks.kanjiReading(water, "スイ", true).ok)
        assertFalse(QuestionChecks.kanjiReading(water, "すい", true).ok)
        val notAccepted = QuestionChecks.kanjiReading(water, "みず", false)
        assertFalse(notAccepted.ok)
        assertTrue(notAccepted.retry)
        assertEquals(AnswerVerdict.NOK_WITHOUT_RETRY, QuestionChecks.kanjiReading(water, "あああ", false))
    }

    @Test
    fun onYomiInKatakanaRequiredRejectsHiragana() {
        assertFalse(QuestionChecks.kanjiOnYomi(water, "すい", true).ok)
        assertTrue(QuestionChecks.kanjiOnYomi(water, "スイ", true).ok)
    }

    @Test
    fun kunYomiQuestionTreatsOnYomiAsRetry() {
        val v = QuestionChecks.kanjiKunYomi(water, "すい", false)
        assertFalse(v.ok)
        assertTrue(v.retry)
        assertTrue(QuestionChecks.kanjiKunYomi(water, "みず", false).ok)
    }

    @Test
    fun smallKanaDigraphMismatchIsReportedNotAccepted() {
        val subject = SubjectAnswers(readings = listOf(Reading("しゅう", "onyomi", acceptedAnswer = true)))
        val v = QuestionChecks.kanjiReading(subject, "しゆう", false)
        assertFalse(v.ok)
        assertFalse(v.retry)
        assertEquals(DigraphMatch('ゆ', 'ゅ'), v.digraphMatch)
        assertNotNull(Reading("しゅう", "onyomi").matchesForDigraph("しゆう"))
        assertNull(Reading("しゅう", "onyomi").matchesForDigraph("しゅう"))
    }

    @Test
    fun vocabularyReadingRetriesWithKanjiReading() {
        val vocab = SubjectAnswers(readings = listOf(Reading("みず", acceptedAnswer = true)))
        val kanji = SubjectAnswers(readings = listOf(Reading("すい", "onyomi", acceptedAnswer = true)))
        assertTrue(QuestionChecks.vocabularyReading(vocab, kanji, "みず", false).ok)
        assertEquals(AnswerVerdict.NOK_WITH_RETRY, QuestionChecks.vocabularyReading(vocab, kanji, "すい", false))
        assertEquals(AnswerVerdict.NOK_WITHOUT_RETRY, QuestionChecks.vocabularyReading(vocab, kanji, "あ", false))
    }
}

class ConfirmationReadingTest {
    @Test
    fun thePrimaryAcceptedReadingIsPreferred() {
        val answers = SubjectAnswers(
            readings = listOf(
                Reading("にほん", acceptedAnswer = true, primary = false),
                Reading("にっぽん", acceptedAnswer = true, primary = true),
            ),
        )
        assertEquals("にっぽん", answers.confirmationReading)
    }

    @Test
    fun anyAcceptedReadingIsUsedWhenNoneIsPrimary() {
        val answers = SubjectAnswers(readings = listOf(Reading("すい", acceptedAnswer = true)))
        assertEquals("すい", answers.confirmationReading)
    }

    @Test
    fun readingsWaniKaniDoesNotAcceptAreNeverShown() {
        assertNull(SubjectAnswers(readings = listOf(Reading("みず", acceptedAnswer = false))).confirmationReading)
        assertNull(SubjectAnswers().confirmationReading)
    }
}
