package com.example.durtlesreborn.session

import com.example.durtlesreborn.answer.AnswerVerdict
import com.example.durtlesreborn.kana.Kana

/** Receives the persistence side effects the original scheduled as jobs. */
interface SessionListener {
    /** An item is complete and should be sent to the server. */
    fun onReport(item: SessionItem)

    /** An item changed and should be saved locally. */
    fun onUpdate(item: SessionItem)

    /** An item was dropped by wrapping up the session. */
    fun onAbandon(item: SessionItem) {}
}

/**
 * Port of the rules in the original Session and Question classes: which question is asked next,
 * how answers are recorded, and undo, skip and ignore. It holds no Android types and no
 * database, so it can be unit tested. Lesson presentation and typeface choice are not included.
 *
 * @param delayed items finish as PENDING and are reported later (always true for self-study).
 * @param random returns a number in [0, bound).
 */
class SessionEngine(
    val type: SessionType,
    val items: List<SessionItem>,
    questions: List<Question>,
    private val delayed: Boolean = false,
    private val backToBack: Boolean = false,
    private val readingFirst: Boolean = false,
    private val meaningFirst: Boolean = false,
    private val random: (Int) -> Int,
    private val clock: () -> Long = System::currentTimeMillis,
    private val listener: SessionListener,
    /** Lessons first show each item, in order, before the quiz starts. */
    startInPresentation: Boolean = false,
) {
    private val questions = questions.toMutableList()
    private val history = ArrayDeque<Question>()

    var state = when {
        this.questions.isEmpty() -> SessionState.FINISHING
        startInPresentation -> SessionState.LESSON_PRESENTATION
        else -> SessionState.ACTIVE
    }
        private set

    /** Index into [items] of the item shown during lesson presentation. */
    var presentationIndex = 0
        private set

    val presentedItem: SessionItem? get() = if (state == SessionState.LESSON_PRESENTATION) items.getOrNull(presentationIndex) else null
    val isOnFirstLessonItem get() = presentationIndex == 0
    val isOnLastLessonItem get() = presentationIndex == items.size - 1

    fun moveToNextLessonItem() {
        if (state == SessionState.LESSON_PRESENTATION && presentationIndex < items.size - 1) presentationIndex++
    }

    fun moveToPreviousLessonItem() {
        if (state == SessionState.LESSON_PRESENTATION && presentationIndex > 0) presentationIndex--
    }

    /** Ends the presentation and begins the quiz on the presented items. */
    fun startQuiz() {
        if (state != SessionState.LESSON_PRESENTATION) return
        state = if (questions.isEmpty()) SessionState.FINISHING else SessionState.ACTIVE
        currentQuestion = null
        answered = false
        lastFinishedItemId = -1
    }

    /** From the quiz of a lesson session back to the cards, at the last item as the original does. */
    fun goBackToPresentation() {
        if (state != SessionState.ACTIVE || type != SessionType.LESSON || items.isEmpty() || questions.isEmpty()) return
        state = SessionState.LESSON_PRESENTATION
        presentationIndex = items.size - 1
        currentQuestion = null
        answered = false
    }
    var currentQuestion: Question? = null
        private set
    var answered = false
        private set
    var correct = false
        private set
    var lastFinishedItemId = -1L
        private set

    val currentItem get() = currentQuestion?.item
    val isDelayed get() = delayed || type == SessionType.SELF_STUDY
    val numStartedItems get() = items.count { it.started }
    val numActiveItems get() = items.count { it.active }
    val remainingQuestions get() = questions.size

    val canBeWrappedUp get() = state == SessionState.ACTIVE && numStartedItems < numActiveItems

    val canUndo: Boolean
        get() {
            if (currentQuestion?.canUndo == true && answered) return true
            val last = history.lastOrNull() ?: return false
            return isDelayed || last.canUndo
        }

    /**
     * The SRS move of the item the current answer just completed, or null while it still has
     * questions left or when the stage would not change (for example an already-burned item). It
     * uses the same rule as the assignment patch, so the bubble and the saved progress agree.
     */
    fun finishedItemStageMove(): StageMove? {
        if (!answered) return null
        val item = currentQuestion?.item ?: return null
        if (questions.any { it.item === item && !it.finished }) return null
        val from = item.srsStageIndex
        val to = item.newSrsStageIndex
        return if (from == to) null else StageMove(from, to)
    }

    /** Picks the next question, narrowing the candidates by the session's ordering options. */
    fun chooseQuestion() {
        if (currentQuestion != null || state != SessionState.ACTIVE) return
        answered = false
        if (questions.isEmpty()) {
            state = SessionState.FINISHING
            return
        }

        var candidates: List<Question> = questions
        fun narrow(predicate: (Question) -> Boolean) {
            val narrowed = candidates.filter(predicate)
            if (narrowed.isNotEmpty()) candidates = narrowed
        }

        if (backToBack) narrow { it.item.started }
        if (readingFirst) narrow { !it.item.hasPendingReadingAndMeaning || it.kind.reading }
        if (meaningFirst) narrow { !it.item.hasPendingReadingAndMeaning || it.kind.meaning }
        if (numStartedItems >= 10) narrow { it.item.started }

        val currentBucket = candidates.first().item.bucket
        val bucketItems = items.filter { it.bucket == currentBucket }
        val hasUndelayed = bucketItems.any { it.choiceDelay == 0 }
        val hasDelayed = bucketItems.any { it.choiceDelay != 0 }
        if (hasDelayed && hasUndelayed) {
            narrow { it.item.bucket == currentBucket && it.item.choiceDelay == 0 }
        }

        val bucket = candidates.first().item.bucket
        var span = 1
        while (span < candidates.size && candidates[span].item.bucket == bucket) span++
        currentQuestion = candidates[random(span)]

        for (item in items) if (item.choiceDelay > 0) item.choiceDelay--
    }

    /**
     * Checks the typed answer. Kana questions first guard the input: it must be kana only
     * and contain the subject's own kana in order, otherwise the user may retry.
     */
    fun submit(rawAnswer: String, check: (Question, String) -> AnswerVerdict): AnswerVerdict {
        val question = currentQuestion ?: return AnswerVerdict.NOK_WITHOUT_RETRY
        if (answered) return AnswerVerdict(correct)
        var answer = Kana.normalize(rawAnswer)
        if (answer.isEmpty()) return AnswerVerdict.NOK_WITH_RETRY

        if (question.kind.kana) {
            if (answer.endsWith("n")) answer = answer.dropLast(1) + "ん"
            if (answer.any { it.code !in 0x3040..0x30FF }) return AnswerVerdict.NOK_WITH_RETRY
            if (!containsRequiredKana(question.item.characters, answer)) return AnswerVerdict.NOK_WITH_RETRY
        }

        val verdict = check(question, answer.trim())
        if (verdict.ok) {
            markCorrect(question)
            answered = true
            correct = true
        } else if (!verdict.retry) {
            markIncorrect(question)
            answered = true
            correct = false
        }
        return verdict
    }

    fun submitAnkiCorrect() {
        val question = currentQuestion ?: return
        if (answered) return
        markCorrect(question)
        answered = true
        correct = true
    }

    fun submitAnkiIncorrect() = submitIncorrectWithoutAnswer()

    fun submitDontKnow() = submitIncorrectWithoutAnswer()

    private fun submitIncorrectWithoutAnswer() {
        val question = currentQuestion ?: return
        if (answered) return
        markIncorrect(question)
        answered = true
        correct = false
    }

    /** Moves on after an answer. A wrong answer delays the item by three choices. */
    fun advance() {
        if (!answered) return
        currentItem?.let { if (it.finished) lastFinishedItemId = it.id }
        currentQuestion?.let { question ->
            if (!correct) question.item.choiceDelay = 3
            history.addLast(question)
            if (question.finished) {
                questions.remove(question)
                if (questions.isEmpty()) state = SessionState.FINISHING
            }
        }
        answered = false
        currentQuestion = null
    }

    /** Skips the current question without answering; the item is delayed by three choices. */
    fun skip() {
        if (answered) return
        currentItem?.choiceDelay = 3
        currentQuestion = null
    }

    /** Undoes the last answer and asks the same question again. */
    fun undoAndRetry() {
        if (!canUndo) return
        undoToCurrent()
        answered = false
        correct = false
        if (state == SessionState.FINISHING) state = SessionState.ACTIVE
    }

    /** Undoes the last answer and puts the question back in the queue to be chosen later. */
    fun undoAndPutBack() {
        if (!canUndo) return
        val question = if (currentQuestion?.canUndo == true && answered) {
            currentQuestion!!.also { undo(it) }
        } else {
            history.removeLast().also { undo(it) }
        }
        putBack(question)
        answered = false
        correct = false
        currentQuestion = null
        if (state == SessionState.FINISHING) state = SessionState.ACTIVE
    }

    /** Undoes the last answer, then counts the question as correct (for a wrongly marked answer). */
    fun ignore() {
        if (!canUndo) return
        undoToCurrent()
        if (state == SessionState.FINISHING) state = SessionState.ACTIVE
        markCorrect(currentQuestion!!)
        answered = true
        correct = true
    }

    /** Makes the last answered question current again, undone, and requeues it if it came from history. */
    private fun undoToCurrent() {
        val current = currentQuestion
        if (current != null && answered && current.canUndo) {
            undo(current)
        } else {
            val question = history.removeLast()
            undo(question)
            currentQuestion = question
            putBack(question)
        }
    }

    /** Reports every pending item. Delayed sessions do this when the summary is shown. */
    fun reportPending() {
        for (item in items) {
            if (item.pending) {
                item.state = SessionItemState.REPORTED
                listener.onReport(item)
            }
        }
    }

    /** Drops every item that has not been started and finishes once the started ones are done. */
    fun wrapUp() {
        for (item in items) {
            if (item.active && !item.started) {
                item.state = SessionItemState.ABANDONED
                listener.onAbandon(item)
            }
        }
        checkQuestions()
    }

    private fun checkQuestions() {
        if (currentQuestion?.finished == true) currentQuestion = null
        questions.removeAll { it.finished }
        history.removeAll { it.item.abandoned }
        if (state == SessionState.ACTIVE && questions.isEmpty()) state = SessionState.FINISHING
    }

    private fun markCorrect(question: Question) {
        val item = question.item
        item.setDone(question.kind.slot, true)
        item.numAnswers++
        item.lastAnswer = clock()
        if (item.finished) {
            if (isDelayed) {
                item.state = SessionItemState.PENDING
                listener.onUpdate(item)
            } else {
                item.state = SessionItemState.REPORTED
                listener.onReport(item)
            }
        } else {
            listener.onUpdate(item)
        }
    }

    private fun markIncorrect(question: Question) {
        val item = question.item
        val slot = question.kind.slot
        item.setIncorrectCount(slot, item.incorrectCount(slot) + 1)
        item.numAnswers++
        listener.onUpdate(item)
    }

    private fun undo(question: Question) {
        if (!question.canUndo) return
        val item = question.item
        val slot = question.kind.slot
        if (item.isDone(slot)) {
            item.setDone(slot, false)
        } else {
            item.setIncorrectCount(slot, item.incorrectCount(slot) - 1)
        }
        item.numAnswers--
        if (item.pending) item.state = SessionItemState.ACTIVE
        listener.onUpdate(item)
    }

    /** Re-inserts a question keeping the queue ordered by bucket, and delays its item. */
    private fun putBack(question: Question) {
        if (question in questions) return
        var index = 0
        while (index < questions.size && question.item.bucket < questions[index].item.bucket) index++
        questions.add(index, question)
        question.item.choiceDelay = 3
    }

    /** Captures the whole session so it can be continued later with [restore]. */
    fun snapshot(savedAt: Long): SessionSnapshot {
        val answeredNow = currentQuestion?.takeIf { answered }
        fun ref(q: Question) = QuestionRef(q.item.id, q.kind.name)
        return SessionSnapshot(
            type = type.name,
            savedAt = savedAt,
            state = state.name,
            presentationIndex = presentationIndex,
            delayed = delayed,
            backToBack = backToBack,
            readingFirst = readingFirst,
            meaningFirst = meaningFirst,
            items = items.map { item ->
                ItemSnapshot(
                    id = item.id,
                    bucket = item.bucket,
                    srs = SrsSnapshot.of(item.srs),
                    srsStageIndex = item.srsStageIndex,
                    characters = item.characters,
                    assignmentId = item.assignmentId,
                    state = item.state.name,
                    numAnswers = item.numAnswers,
                    lastAnswer = item.lastAnswer,
                    choiceDelay = item.choiceDelay,
                    done = (0..4).map { item.isDone(it) },
                    incorrect = (0..4).map { item.incorrectCount(it) },
                )
            },
            questions = questions.map(::ref),
            // An answer that was shown but not yet moved past still counts as answered.
            history = (history + listOfNotNull(answeredNow)).map(::ref),
        )
    }

    companion object {
        /** Rebuilds a saved session, or returns null if the snapshot does not hang together. */
        fun restore(
            snapshot: SessionSnapshot,
            random: (Int) -> Int,
            clock: () -> Long = System::currentTimeMillis,
            listener: SessionListener,
        ): SessionEngine? {
            val type = snapshot.sessionType ?: return null
            val state = SessionState.entries.firstOrNull { it.name == snapshot.state } ?: return null
            val items = snapshot.items.map { s ->
                if (s.done.size != 5 || s.incorrect.size != 5) return null
                val itemState = SessionItemState.entries.firstOrNull { it.name == s.state } ?: return null
                SessionItem(s.id, s.bucket, s.srs.toSystem(), s.srsStageIndex, s.characters, s.assignmentId).also { item ->
                    item.state = itemState
                    item.numAnswers = s.numAnswers
                    item.lastAnswer = s.lastAnswer
                    item.choiceDelay = s.choiceDelay
                    for (slot in 0..4) {
                        item.setDone(slot, s.done[slot])
                        item.setIncorrectCount(slot, s.incorrect[slot])
                    }
                }
            }
            val byId = items.associateBy { it.id }
            val shared = HashMap<Pair<Long, String>, Question>()
            fun question(ref: QuestionRef): Question? {
                val item = byId[ref.itemId] ?: return null
                val kind = QuestionKind.entries.firstOrNull { it.name == ref.kind } ?: return null
                return shared.getOrPut(ref.itemId to ref.kind) { Question(item, kind) }
            }
            val queue = snapshot.questions.map { question(it) ?: return null }
            val past = snapshot.history.map { question(it) ?: return null }
            val engine = SessionEngine(
                type, items, queue, snapshot.delayed, snapshot.backToBack, snapshot.readingFirst, snapshot.meaningFirst,
                random, clock, listener,
            )
            engine.history.addAll(past)
            engine.state = state
            engine.presentationIndex = snapshot.presentationIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            engine.checkQuestions()
            return engine
        }

        private val hanOrFullWidthDigits = Regex("[\\p{IsHan}０-９]+")

        /**
         * The answer must contain the subject's kana in order. Katakana is compared as hiragana,
         * the wave dash is ignored and small ヶ counts as か; kanji and digits match any text.
         */
        private fun containsRequiredKana(characters: String?, answer: String): Boolean {
            if (characters == null) return true
            val subject = Kana.toHiragana(characters.replace("〜", "").replace("ヶ", "か"))
            val pattern = hanOrFullWidthDigits.replace(subject, ".+")
            return Kana.toHiragana(answer).matches(Regex(pattern))
        }
    }
}
