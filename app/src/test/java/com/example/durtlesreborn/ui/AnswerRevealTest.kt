package com.example.durtlesreborn.ui

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
