package com.example.durtlesreborn.kana

import java.text.Normalizer
import java.util.Locale

/** Port of the original PseudoIme: simulates a romaji-to-kana input method. */
object Kana {
    private val toKana: Map<String, String> by lazy {
        val map = HashMap(ROMAJI_TO_HIRAGANA)
        // Upper-case romaji gives katakana.
        for ((key, value) in ROMAJI_TO_HIRAGANA) {
            map[key.uppercase(Locale.ENGLISH)] = toKatakana(value)
        }
        map
    }

    /** Converts hiragana (U+3040..U+3096) to katakana, leaving other characters alone. */
    fun toKatakana(text: String): String = buildString(text.length) {
        for (c in text) append(if (c.code in 0x3040..0x3096) c + 0x60 else c)
    }

    /** Converts katakana, full width and half width, to hiragana. */
    fun toHiragana(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in 'ァ'..'ヾ' -> c - 0x60
                    in 'ｦ'..'ﾝ' -> c - 0xcf25
                    else -> c
                },
            )
        }
    }

    /**
     * NFC, so a base kana followed by a combining dakuten or handakuten (what some IMEs send in a
     * composing region) is the same character as the precomposed kana (ひ + ゛ becomes び).
     */
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

    /**
     * Applies the input method to the range [editStart, editEnd) of [s] in place, like
     * the original does for each text change in the answer box.
     */
    fun fixup(s: StringBuilder, editStart: Int, editEnd: Int) {
        var start = editStart
        var end = editEnd
        while (start in 0 until end) {
            if (start > 0) {
                val c1 = s[start - 1]
                val c2 = s[start]
                if (c1 == 'n' && "aiueoyn ".indexOf(c2) < 0) {
                    s.replace(start - 1, start, "ん")
                    continue
                }
                if (c1 == c2 && "bcdfghjklmpqrstvwxz".indexOf(c1) >= 0) {
                    s.replace(start - 1, start, "っ")
                    continue
                }
            }

            var found = false
            for (i in maxOf(start - 3, 0)..start) {
                val replacement = toKana[s.substring(i, start + 1)] ?: continue
                s.replace(i, start + 1, replacement)
                end = minOf(end - (start + 1 - i) + replacement.length, s.length)
                start = i + replacement.length
                found = true
                break
            }
            if (found) continue

            if (s[start].isWhitespace()) {
                s.deleteCharAt(start)
                end--
                continue
            }
            start++
        }
    }

    /**
     * Text of an answer box after the user changed it from [previous] to [current]: new
     * characters are run through the input method, deletions and edits before the end are kept as typed.
     */
    fun typed(previous: String, current: String): String {
        var common = 0
        while (common < previous.length && common < current.length && previous[common] == current[common]) common++
        if (current.length <= common) return current
        val sb = StringBuilder(current)
        fixup(sb, common, current.length)
        return sb.toString()
    }

    /** Types [input] one go and returns the resulting text, ending a dangling "n" as ん. */
    fun simulateInput(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input)
        fixup(sb, 0, input.length)
        if (sb.isNotEmpty() && sb[sb.length - 1] == 'n') {
            sb.setCharAt(sb.length - 1, 'ん')
        }
        return sb.toString()
    }
}
