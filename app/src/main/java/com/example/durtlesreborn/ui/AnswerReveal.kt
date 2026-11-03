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
 * it is revealed, another tap opens the full item details.
 */
internal fun bannerTap(correct: Boolean, revealed: Boolean, answerShown: Boolean = false): BannerTap =
    if (correct || revealed || answerShown) BannerTap.DETAILS else BannerTap.REVEAL
