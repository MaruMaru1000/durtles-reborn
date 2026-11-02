package com.example.durtlesreborn.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchAccentTest {
    @Test
    fun moraeMergeSmallKanaOntoThePreviousMora() {
        assertEquals(listOf("しゃ", "し", "ん"), morae("しゃしん"))
        assertEquals(listOf("み", "ず"), morae("みず"))
    }

    @Test
    fun heibanStartsLowThenRisesAndStaysHigh() {
        assertEquals("LHH", pitchPattern(3, 0))
        assertEquals("LH", pitchPattern(2, 0))
    }

    @Test
    fun atamadakaStartsHighThenFallsAndStaysLow() {
        assertEquals("HLL", pitchPattern(3, 1))
        assertEquals("H", pitchPattern(1, 1))
    }

    @Test
    fun nakadakaDropsAfterTheAccentedMora() {
        assertEquals("LHL", pitchPattern(3, 2))
        assertEquals("LHHL", pitchPattern(4, 3))
    }
}
