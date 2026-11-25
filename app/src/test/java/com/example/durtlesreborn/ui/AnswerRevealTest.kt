package com.example.durtlesreborn.ui

import com.example.durtlesreborn.answer.Meaning
import com.example.durtlesreborn.answer.Reading
import com.example.durtlesreborn.answer.SubjectAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerRevealTest {
    @Test
    fun aCorrectAnswerShowsTheReading() {
        assertEquals("ゆうすい", confirmationReading(answered = true, correct = true, reading = "ゆうすい"))
    }

    @Test
    fun aWrongAnswerNeverShowsTheReading() {
        assertNull(confirmationReading(answered = true, correct = false, reading = "ゆうすい"))
    }

    @Test
    fun theReadingStaysHiddenUntilTheQuestionIsAnswered() {
        assertNull(confirmationReading(answered = false, correct = true, reading = "ゆうすい"))
        assertNull(confirmationReading(answered = false, correct = null, reading = "ゆうすい"))
    }

    @Test
    fun anAnsweredQuestionWithNoVerdictShowsNothing() {
        assertNull(confirmationReading(answered = true, correct = null, reading = "ゆうすい"))
    }

    @Test
    fun anItemWithoutAReadingShowsNothing() {
        assertNull(confirmationReading(answered = true, correct = true, reading = null))
    }

    private val answers = SubjectAnswers(
        meanings = listOf(Meaning("How Many Times"), Meaning("How Often"), Meaning("Degree", acceptedAnswer = false)),
        readings = listOf(Reading("なんど", acceptedAnswer = true, primary = true)),
    )

    @Test
    fun aMeaningQuestionConfirmsTheMeaning() {
        assertEquals("How Many Times, How Often", confirmationHint(readingQuestion = false, answers = answers))
    }

    @Test
    fun aReadingQuestionConfirmsTheReading() {
        assertEquals("なんど", confirmationHint(readingQuestion = true, answers = answers))
    }

    @Test
    fun aMeaningQuestionWithNoAcceptedMeaningShowsNothing() {
        assertNull(confirmationHint(readingQuestion = false, answers = SubjectAnswers(readings = answers.readings)))
    }

    @Test
    fun aCorrectAnswerOpensTheDetails() {
        assertEquals(BannerTap.DETAILS, bannerTap(correct = true, revealed = false))
    }

    @Test
    fun theFirstWrongTapRevealsTheAnswer() {
        assertEquals(BannerTap.REVEAL, bannerTap(correct = false, revealed = false))
    }

    @Test
    fun aSecondWrongTapOpensTheDetails() {
        assertEquals(BannerTap.DETAILS, bannerTap(correct = false, revealed = true))
    }

    @Test
    fun aWrongAnswerAlreadyShownInTheBannerOpensTheDetails() {
        assertEquals(BannerTap.DETAILS, bannerTap(correct = false, revealed = false, answerShown = true))
    }

    @Test
    fun aWrongAnswerAutoRevealsTheQuickAnswerWhenEnabled() {
        assertTrue(autoRevealQuickAnswer(correct = false, quickAnswer = true, answerShown = false))
    }

    @Test
    fun theQuickAnswerWaitsForATapWhenDisabled() {
        assertFalse(autoRevealQuickAnswer(correct = false, quickAnswer = false, answerShown = false))
    }

    @Test
    fun aCorrectAnswerNeverAutoRevealsTheQuickAnswer() {
        assertFalse(autoRevealQuickAnswer(correct = true, quickAnswer = true, answerShown = false))
    }

    @Test
    fun anAnswerAlreadyShownInTheBannerIsNotRevealedAgain() {
        assertFalse(autoRevealQuickAnswer(correct = false, quickAnswer = true, answerShown = true))
    }

    @Test
    fun aWrongTapStillRevealsTheAnswerWhenTheQuickAnswerIsOff() {
        // The setting only turns off the automatic reveal; the first tap always shows the answer.
        assertEquals(BannerTap.REVEAL, bannerTap(correct = false, revealed = false, answerShown = false))
    }
}
