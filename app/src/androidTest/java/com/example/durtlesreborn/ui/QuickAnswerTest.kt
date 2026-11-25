package com.example.durtlesreborn.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.durtlesreborn.answer.AnswerVerdict
import com.example.durtlesreborn.answer.Meaning
import com.example.durtlesreborn.answer.Reading
import com.example.durtlesreborn.answer.SubjectAnswers
import com.example.durtlesreborn.session.QuestionKind
import com.example.durtlesreborn.session.QuizCard
import com.example.durtlesreborn.session.QuizFeedback
import com.example.durtlesreborn.session.QuizProgress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAnswerTest {
    @get:Rule val rule = createComposeRule()

    private val answers = SubjectAnswers(
        meanings = listOf(Meaning("Water")),
        readings = listOf(Reading("すい", "onyomi", acceptedAnswer = true, primary = true)),
    )
    private val wrong = QuizFeedback(AnswerVerdict.NOK_WITHOUT_RETRY, retry = false, correct = false)
    private val progress = QuizProgress(remainingQuestions = 2, answeredItems = 0, totalItems = 1)

    private fun card(kind: QuestionKind) = QuizCard(1L, "水", kind, kind.kana, answers)

    @Test
    fun wrongMeaningRevealsTheMeaning() {
        var state by mutableStateOf(UiState.Quiz(card(QuestionKind.KANJI_MEANING), progress = progress))
        rule.setContent { Quiz(state) }
        state = state.copy(input = "fire", feedback = wrong, answered = true)
        rule.onNodeWithContentDescription("Answer: Water").assertIsDisplayed()
    }

    @Test
    fun wrongReadingThenWrongMeaningRevealsTheMeaning() {
        var state by mutableStateOf(UiState.Quiz(card(QuestionKind.KANJI_READING), progress = progress))
        rule.setContent { Quiz(state) }
        state = state.copy(input = "か", feedback = wrong, answered = true)
        rule.onNodeWithContentDescription("Answer: すい").assertIsDisplayed()
        state = UiState.Quiz(card(QuestionKind.KANJI_MEANING), progress = progress)
        rule.waitForIdle()
        state = state.copy(input = "fire", feedback = wrong, answered = true)
        rule.onNodeWithContentDescription("Answer: Water").assertIsDisplayed()
        rule.onNodeWithText("すい").assertDoesNotExist()
    }

    @Test
    fun correctMeaningConfirmsTheMeaningNotTheReading() {
        val correct = QuizFeedback(AnswerVerdict(true), retry = false, correct = true)
        var state by mutableStateOf(UiState.Quiz(card(QuestionKind.KANJI_MEANING), progress = progress))
        rule.setContent { Quiz(state) }
        state = state.copy(input = "water", feedback = correct, answered = true)
        rule.onNodeWithContentDescription("Meaning: Water").assertIsDisplayed()
        rule.onNodeWithText("すい").assertDoesNotExist()
    }

    @Test
    fun correctReadingConfirmsTheReading() {
        val correct = QuizFeedback(AnswerVerdict(true), retry = false, correct = true)
        var state by mutableStateOf(UiState.Quiz(card(QuestionKind.KANJI_READING), progress = progress))
        rule.setContent { Quiz(state) }
        state = state.copy(input = "すい", feedback = correct, answered = true)
        rule.onNodeWithContentDescription("Reading: すい").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun Quiz(state: UiState.Quiz) = QuizScreen(
        state, {}, {}, {}, {}, {}, {}, {}, {}, { null }, {},
    )
}
