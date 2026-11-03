package com.example.durtlesreborn.ui

/**
 * The reading shown above the item once a question is answered. Only a correct answer reveals it:
 * a wrong answer must not give the reading away above the character, whatever the answer setting
 * says, because the accepted answer is what the wrong answer is hiding.
 */
internal fun confirmationReading(answered: Boolean, correct: Boolean?, reading: String?): String? =
    reading?.takeIf { answered && correct == true }

/** What tapping the feedback banner does. */
internal enum class BannerTap { REVEAL, DETAILS }

/**
 * On a correct answer the banner opens the full details at once. On a wrong one the first tap only
 * reveals the accepted answer above the item, so the answer is not given away by a stray tap; once
 * it is revealed, another tap opens the full item details. The quick answer setting never changes
 * this: turning the automatic reveal off leaves the answer one tap away.
 */
internal fun bannerTap(correct: Boolean, revealed: Boolean, answerShown: Boolean = false): BannerTap =
    if (correct || revealed || answerShown) BannerTap.DETAILS else BannerTap.REVEAL

/**
 * Whether a wrong answer shows the accepted answer above the item by itself (`quick_answer_on_wrong`).
 * Turning it off only removes the automatic reveal; tapping the banner still shows the answer.
 */
internal fun autoRevealQuickAnswer(correct: Boolean?, quickAnswer: Boolean, answerShown: Boolean): Boolean =
    correct == false && quickAnswer && !answerShown
