package com.example.durtlesreborn.reading.core

/**
 * Matches tokens against WaniKani vocabulary (base or surface form), kana vocabulary and single
 * kanji characters. A locked item still matches: it is shown greyed out rather than ignored.
 */
class Matcher(subjects: List<SubjectLite>, assignments: List<AssignmentLite>) {
    private val vocab: Map<String, SubjectLite>
    private val kanji: Map<String, SubjectLite>
    private val stages: Map<Int, Int> = assignments.associate { it.subjectId to it.srsStage }

    init {
        val vocabMap = HashMap<String, SubjectLite>(subjects.size)
        val kanjiMap = HashMap<String, SubjectLite>(subjects.size)
        for (subject in subjects) {
            val characters = subject.characters?.takeIf { it.isNotBlank() } ?: continue
            when (subject.type) {
                SubjectType.VOCABULARY, SubjectType.KANA_VOCABULARY -> vocabMap[characters] = subject
                SubjectType.KANJI -> if (characters.length == 1) kanjiMap[characters] = subject
                SubjectType.RADICAL -> Unit
            }
        }
        vocab = vocabMap
        kanji = kanjiMap
    }

    fun match(tokens: List<Token>): List<TokenMatch?> = tokens.map { token ->
        val subject = vocab[token.baseForm]
            ?: vocab[token.surface]
            ?: token.surface.takeIf { it.length == 1 }?.let { kanji[it] }
            ?: token.baseForm.takeIf { it.length == 1 }?.let { kanji[it] }
        subject?.let { TokenMatch(token, it, stages[it.id]) }
    }

    /** Tokenizes and matches in one go. */
    fun analyze(tokenizer: JapaneseTokenizer, text: String): TokenizedArticle {
        val tokens = tokenizer.tokenize(text)
        return TokenizedArticle(tokens, match(tokens))
    }
}
