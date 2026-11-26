package com.kanjiforge.app.data.reference.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySequenceFamilyTest {
    @Test
    fun jlptLevelsStayInBeginnerToAdvancedOrder() {
        assertEquals(listOf("N5", "N4", "N3", "N2", "N1"), (5 downTo 1).map(StudySequenceFamily.JLPT::levelLabel))
        assertEquals(listOf("N5", "N4", "N3", "N2", "N1"), (5 downTo 1).map(StudySequenceFamily.JLPT_REVISED::levelLabel))
        assertTrue(StudySequenceFamily.JLPT.levelsDescending)
        assertTrue(StudySequenceFamily.JLPT_REVISED.levelsDescending)
    }
}
